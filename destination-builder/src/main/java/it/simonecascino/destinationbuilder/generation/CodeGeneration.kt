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
import com.google.devtools.ksp.symbol.KSType
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
import com.squareup.kotlinpoet.ksp.toClassName
import it.simonecascino.destinationbuilder.annotations.Destination
import it.simonecascino.destinationbuilder.annotations.Graph
import it.simonecascino.destinationbuilder.base.BaseDestination

private const val PACKAGE_NAME = "it.simonecascino.destination"
private const val BUILD_PATH = "buildPath"
private const val FROM_PATH = "fromPath"
private const val PATH = "path"
private const val PATHS = "paths"
private const val QUERY_PARAMS = "queryParams"
private const val DYNAMIC_TITLE = "dynamicTitle"
private const val ANDROID_TITLE_VALUE = "androidAppTitle"

private val underscoreChar = "_".toCharArray().first()

class CodeGeneration(
    private val sourceFiles: Set<KSFile>,
    private val processableSymbols: Sequence<KSFunctionDeclaration>
) {

    @OptIn(KspExperimental::class)
    fun generateFileSpecs(): List<FileSpec>{

        val destinations = ArrayList<AnnotationSpecsAndName<Destination>>()
        val resolvers = ArrayList<AnnotationSpecsAndName<List<String>>>()

        processableSymbols.forEach { function ->

            function.getAnnotationsByType(Destination::class).firstOrNull()?.let {
                destinations.add(
                    AnnotationSpecsAndName(
                        annotation = it,
                        name = function.simpleName.getShortName()
                    )
                )
            }

            function.annotations.filter {
                it.shortName.getShortName() == Graph::class.simpleName &&
                it.annotationType.resolve().declaration.qualifiedName?.asString() == Graph::class.qualifiedName
            } .let{ graphAnnotations ->

                graphAnnotations.firstOrNull()?.arguments?.let { args ->
                    val consumerType = args.first().value as ArrayList<KSType>

                    resolvers.add(
                        AnnotationSpecsAndName(
                            annotation = consumerType.map {
                                it.toClassName().simpleName
                            },
                            name = "${function.simpleName.getShortName()}Resolver"
                        )
                    )
                }

            }

        }

        val graphs = destinations.groupBy {
            it.annotation.graphName
        }

        val specs = ArrayList<FileSpec>()

        graphs.keys.forEach { graphName ->
            val annotations = graphs[graphName]

            val code = generateDestinationsFileContent(
                graphName,
                annotations
            )

            specs.add(
                FileSpec.builder(
                    PACKAGE_NAME,
                    graphName
                ).also { fileSpec ->
                    fileSpec.addType(code)
                }.build()
            )

        }

        resolvers.forEach { appGraph ->

            if(appGraph.annotation.isEmpty())
                return@forEach

            specs.add(
                FileSpec.builder(
                    PACKAGE_NAME,
                    appGraph.name
                ).also {
                    it.addType(generateResolver(appGraph))
                }.build()
            )

        }

        return specs

    }

    private fun generateResolver(
        annotation: AnnotationSpecsAndName<List<String>>
    ): TypeSpec{

        val code = annotation.annotation.joinToString(
            separator = " ?:\n",
            prefix = "return \n",
        ){
            "\t$it.fromPath(route)"
        }

        return TypeSpec.objectBuilder(annotation.name)
            .addFunction(
                funSpec = FunSpec.builder(
                    "resolve"
                ).addParameter(
                    "route",
                    String::class
                ).addStatement(
                    code
                ).returns(
                    BaseDestination::class.asTypeName().copy(nullable = true)
                ).build()
            )
            .build()

    }

    private fun generateDestinationsFileContent(
        graphName: String,
        annotations: List<AnnotationSpecsAndName<Destination>>?
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
                    .addProperty(
                        PropertySpec.builder("graphRoute", String::class)
                            .initializer("%S", graphName.lowercase())
                            .build()
                    )
                    .build()
            ).also { builder ->
                sourceFiles.forEach {
                    builder.addOriginatingKSFile(it)
                }
                annotations?.forEach {
                    builder.addType(
                        generateDestination(it.annotation, graphName, it.name)
                    )
                }
            }.build()

    }

    private fun generateFromFunction(
        destinations: List<AnnotationSpecsAndName<Destination>>
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
            function.addStatement(" %S -> ${it.name}", it.annotation.destinationName.ifBlank { it.name })
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

            val nameInSnakeCase = s.fold(StringBuilder("KEY_")){ acc, c ->
                acc.also {

                    it.append(

                        if(c.isLowerCase())
                            c.uppercase()

                        else if (
                            c.isLetter() || (c.isDigit() && acc.last() != underscoreChar)
                        )
                            "_$c"

                        else c
                    )
                }
            }.toString()

            objectSpecBuilder.addProperty(
                PropertySpec.builder(nameInSnakeCase, String::class, KModifier.CONST)
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

    private data class AnnotationSpecsAndName<T>(
        val annotation: T,
        val name: String
    )

}