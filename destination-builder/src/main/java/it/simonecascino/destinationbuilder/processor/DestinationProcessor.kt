package it.simonecascino.destinationbuilder.processor

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

import com.squareup.kotlinpoet.*
import it.simonecascino.destinationbuilder.annotation.Destination
import it.simonecascino.destinationbuilder.base.BaseDestination
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

@SupportedSourceVersion(SourceVersion.RELEASE_8)
class DestinationProcessor: AbstractProcessor() {

    override fun getSupportedAnnotationTypes() =
        mutableSetOf(Destination::class.java.canonicalName)

    override fun process(annotations: MutableSet<out TypeElement>?,
                         roundEnv: RoundEnvironment
    ): Boolean {

        val kaptKotlinGeneratedDir =
            processingEnv.options[GenerationConstants.Global.GENERATION_FOLDER]
                ?: return false

        val elements = roundEnv.getElementsAnnotatedWith(Destination::class.java)

        if(elements.isNotEmpty()){

            val graphs = elements.groupBy {
                it.getAnnotation(Destination::class.java).graphName
            }

            graphs.keys.forEach {

                val itemsForGraph = graphs[it] ?: emptyList()

                val objectBuilder = TypeSpec.objectBuilder(it)
                    .addFunction(
                        generateFromFunction(itemsForGraph)
                    )

                itemsForGraph.forEach {

                    val name = it.simpleName.toString()
                    val annotation = it.getAnnotation(Destination::class.java)

                    generateDestination(annotation, objectBuilder, name)

                }

                FileSpec.builder(
                    GenerationConstants.Global.PACKAGE_NAME,
                    it
                ).also { fileSpec ->
                    fileSpec.addType(objectBuilder.build())
                }.build().writeTo(File(kaptKotlinGeneratedDir))

            }

        }

        return true
    }

    private fun generateFromFunction(elements: List<Element>): FunSpec{

        val function = FunSpec.builder(GenerationConstants.Functions.FROM_PATH)
            .addParameter(GenerationConstants.Parameters.PATH, String::class)
            .returns(BaseDestination::class)
            .beginControlFlow("val name = if(${GenerationConstants.Parameters.PATH}.contains(%S))", "/")
            .addStatement("${GenerationConstants.Parameters.PATH}.split(%S).first()", "/")
            .endControlFlow()
            .beginControlFlow("else if (${GenerationConstants.Parameters.PATH}.contains(%S))", "?")
            .addStatement("${GenerationConstants.Parameters.PATH}.split(%S).first()", "?")
            .endControlFlow()
            .addStatement("else ${GenerationConstants.Parameters.PATH}")
            .addStatement("return when(name){")

        elements.forEach {
            val name = it.simpleName.toString()
            function.addStatement(" %S -> $name", name)
        }

        //todo: don't throw the exception here, since we may have multiple graph
        function.addStatement(" else -> throw RuntimeException()")

        function.addStatement("}")

        return function.build()

    }

    private fun generateDestination(
        annotation: Destination,
        superClass: TypeSpec.Builder,
        name: String
    ){

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
            .superclass(BaseDestination::class)
            .addSuperclassConstructorParameter(CodeBlock.builder().add("arrayOf($pathsTemplate)", *annotation.paths).build())
            .addSuperclassConstructorParameter(CodeBlock.builder().add("arrayOf($queryParamsTemplate)", *annotation.queryParams).build())
            .addSuperclassConstructorParameter(CodeBlock.builder().add("${annotation.dynamicTitle}").build())

        objectSpecBuilder.addFunction(generateInnerPathFunction(annotation, objectSpecBuilder))

        superClass.addType(
            objectSpecBuilder.build()
        )

    }

    private fun generateInnerPathFunction(annotation: Destination, objectSpecBuilder: TypeSpec.Builder): FunSpec{
        val pathFunSpecBuilder = FunSpec.builder(GenerationConstants.Functions.BUILD_PATH)
            .returns(String::class)

        val pathMap = mutableMapOf<String, String>()
        pathMap[""] = ""

        pathFunSpecBuilder.addStatement("val pathMap = mutableMapOf<String, String>()")
        pathFunSpecBuilder.addStatement("val queryMap = mutableMapOf<String, String?>()")

        if(annotation.dynamicTitle){
            pathFunSpecBuilder
                .addStatement("pathMap[%S] = ${GenerationConstants.Parameters.ANDROID_TITLE_VALUE}", GenerationConstants.Parameters.ANDROID_TITLE_VALUE)
                .addParameter(GenerationConstants.Parameters.ANDROID_TITLE_VALUE, String::class)
        }

        annotation.paths.forEachIndexed { index, s ->

            pathFunSpecBuilder
                .addStatement("pathMap[%S] = $s", annotation.paths[index])
                .addParameter(s, String::class)

            objectSpecBuilder.addProperty(
                PropertySpec.builder("KEY_$s", String::class, KModifier.CONST)
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
                PropertySpec.builder("KEY_$s", String::class, KModifier.CONST)
                    .initializer("%S", s).build()
            )

        }

        return pathFunSpecBuilder
            .addStatement("return super.buildPath(pathMap, queryMap)")
            .build()
    }
}

private object GenerationConstants{

    object Global{
        const val PACKAGE_NAME = "it.simonecascino.destination"
        const val GENERATION_FOLDER = "kapt.kotlin.generated"
    }

    object Functions{
        const val BUILD_PATH = "buildPath"
        const val FROM_PATH = "fromPath"
    }

    object Parameters{

        const val PATH = "path"
        const val ANDROID_TITLE_VALUE = "androidAppTitle"

    }

}