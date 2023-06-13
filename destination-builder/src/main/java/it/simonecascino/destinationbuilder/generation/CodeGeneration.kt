package it.simonecascino.destinationbuilder.generation

/**
Copyright (C) 2021 Simone Cascino

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import it.simonecascino.destinationbuilder.annotation.Destination
import it.simonecascino.destinationbuilder.base.BaseDestination

private const val PACKAGE_NAME = "it.simonecascino.destination"
private const val BUILD_PATH = "buildPath"
private const val FROM_PATH = "fromPath"
private const val PATH = "path"
private const val PATHS = "paths"
private const val QUERY_PARAMS = "queryParams"
private const val DYNAMIC_TITLE = "dynamicTitle"
private const val ANDROID_TITLE_VALUE = "androidAppTitle"

class CodeGeneration(
    private val sourceFiles: Set<KSFile>,
    private val processableSymbols: Sequence<KSFunctionDeclaration>
) {

    @OptIn(KspExperimental::class)
    fun generateFileSpecs(): List<FileSpec>{

        val destinations = processableSymbols.map {
            AnnotationAndName(
                destination = it.getAnnotationsByType(Destination::class).first(),
                name = it.simpleName.getShortName()
            )

        }

        val graphs = destinations.groupBy {
            it.destination.graphName
        }

        return graphs.keys.map { graphName ->

            val annotations = graphs[graphName]

            val code = generateFileContent(
                graphName,
                annotations
            )

            FileSpec.builder(
                PACKAGE_NAME,
                graphName
            ).also { fileSpec ->
                fileSpec.addType(code)
            }.build()

        }

    }

    private fun generateFileContent(
        graphName: String,
        annotations: List<AnnotationAndName>?
    ): TypeSpec {

        val array = ClassName("kotlin", "Array")
        val producerArrayOfStrings = array.parameterizedBy(WildcardTypeName.producerOf(String::class))

        return TypeSpec.classBuilder(graphName)
            .addModifiers(KModifier.SEALED)
            .superclass(BaseDestination::class)
            .addSuperclassConstructorParameter(
                "$PATHS, $QUERY_PARAMS, $DYNAMIC_TITLE"
            )
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(
                        PATHS, producerArrayOfStrings
                    )
                    .addParameter(
                        QUERY_PARAMS, producerArrayOfStrings
                    )
                    .addParameter(
                        DYNAMIC_TITLE, Boolean::class
                    )
                    .build()
            )
            .addType(
                TypeSpec.companionObjectBuilder()
                    .addFunction(
                        generateFromFunction(
                            annotations?: emptyList()
                        )
                    )
                    .build()
            ).also { builder ->
                sourceFiles.forEach {
                    builder.addOriginatingKSFile(it)
                }
                annotations?.forEach {
                    builder.addType(
                        generateDestination(it.destination, graphName, it.name)
                    )
                }
            }.build()

    }

    private fun generateFromFunction(
        destinations: List<AnnotationAndName>
    ): FunSpec {

        val function = FunSpec.builder(FROM_PATH)
            .addParameter(PATH, String::class)
            .returns(BaseDestination::class.asTypeName().copy(nullable = true))
            .beginControlFlow("val name = if($PATH.contains(%S))", "/")
            .addStatement("$PATH.split(%S).first()", "/")
            .endControlFlow()
            .beginControlFlow("else if ($PATH.contains(%S))", "?")
            .addStatement("$PATH.split(%S).first()", "?")
            .endControlFlow()
            .addStatement("else $PATH")
            .addStatement("return when(name){")

        sourceFiles.forEach {
            function.addOriginatingKSFile(it)
        }

        destinations.forEach {
            function.addStatement(" %S -> ${it.name}", it.destination.destinationName.ifBlank { it.name })
        }

        function.addStatement(" else -> null")

        function.addStatement("}")

        return function.build()

    }

    private fun generateDestination(
        annotation: Destination,
        graphName: String,
        name: String
    ): TypeSpec {

        val pathsTemplate = StringBuilder()
        val queryParamsTemplate = StringBuilder()

        repeat(annotation.paths.size){
            if(pathsTemplate.isEmpty())
                pathsTemplate.append("%S")
            else pathsTemplate.append(",%S")
        }

        repeat(annotation.queryParams.size){
            if(queryParamsTemplate.isEmpty())
                queryParamsTemplate.append("%S")
            else queryParamsTemplate.append(",%S")
        }

        val objectSpecBuilder = TypeSpec.objectBuilder(name)
            .superclass(ClassName(PACKAGE_NAME, graphName))
            .addSuperclassConstructorParameter(CodeBlock.builder().add("arrayOf($pathsTemplate)", *annotation.paths).build())
            .addSuperclassConstructorParameter(CodeBlock.builder().add("arrayOf($queryParamsTemplate)", *annotation.queryParams).build())
            .addSuperclassConstructorParameter(CodeBlock.builder().add("${annotation.dynamicTitle}").build())

        objectSpecBuilder.addFunction(generateInnerPathFunction(annotation, objectSpecBuilder))

        if(annotation.destinationName.isNotBlank()){
            objectSpecBuilder.addProperty(
                PropertySpec.builder("name", String::class, KModifier.OVERRIDE)
                    .initializer("%S", annotation.destinationName)
                    .build()
            )
        }

        sourceFiles.forEach {
            objectSpecBuilder.addOriginatingKSFile(it)
        }

        return objectSpecBuilder.build()
    }

    private fun generateInnerPathFunction(
        annotation: Destination,
        objectSpecBuilder: TypeSpec.Builder
    ): FunSpec {

        val pathFunSpecBuilder = FunSpec.builder(BUILD_PATH)
            .returns(String::class)

        sourceFiles.forEach {
            pathFunSpecBuilder.addOriginatingKSFile(it)
        }

        val pathMap = mutableMapOf<String, String>()
        pathMap[""] = ""

        pathFunSpecBuilder.addStatement("val pathMap = mutableMapOf<String, String>()")
        pathFunSpecBuilder.addStatement("val queryMap = mutableMapOf<String, String?>()")

        if(annotation.dynamicTitle){
            pathFunSpecBuilder
                .addStatement("pathMap[%S] = $ANDROID_TITLE_VALUE",
                    ANDROID_TITLE_VALUE
                )
                .addParameter(ANDROID_TITLE_VALUE, String::class)
        }

        annotation.paths.forEachIndexed { index, s ->

            pathFunSpecBuilder
                .addStatement("pathMap[%S] = $s", annotation.paths[index])
                .addParameter(s, String::class)

            objectSpecBuilder.addProperty(
                PropertySpec.builder("KEY_${s.uppercase()}", String::class, KModifier.CONST)
                    .initializer("%S", s).build()
            )

        }

        annotation.queryParams.forEachIndexed { index, s ->
            pathFunSpecBuilder
                .beginControlFlow("if($s != null)")
                .addStatement("queryMap[%S] = $s", annotation.queryParams[index])
                .endControlFlow()
                .addParameter(
                    ParameterSpec
                        .builder(s, String::class.asTypeName().copy(nullable = true))
                        .defaultValue("null")
                        .build()
                )

            objectSpecBuilder.addProperty(
                PropertySpec.builder("KEY_${s.uppercase()}", String::class, KModifier.CONST)
                    .initializer("%S", s).build()
            )

        }

        return pathFunSpecBuilder
            .addStatement("return super.buildPath(pathMap, queryMap)")
            .build()
    }

    private data class AnnotationAndName(
        val destination: Destination,
        val name: String
    )

}