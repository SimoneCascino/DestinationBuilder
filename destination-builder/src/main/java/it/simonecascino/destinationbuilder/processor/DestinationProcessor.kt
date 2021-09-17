package it.simonecascino.destinationbuilder.processor

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import it.simonecascino.destinationbuilder.annotation.Destination
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

            val sealedClassBuilder = prepareSealedClass(elements)

            elements.forEach {

                val name = it.simpleName.toString()
                val annotation = it.getAnnotation(Destination::class.java)

                generateSealedClass(annotation, sealedClassBuilder, name)

            }

            FileSpec.builder(GenerationConstants.Global.PACKAGE_NAME, GenerationConstants.Global.CLASS_NAME).also { fileSpec ->
                fileSpec.addType(sealedClassBuilder.build())
            }.build().writeTo(File(kaptKotlinGeneratedDir))

        }

        return true
    }

    private fun prepareSealedClass(elements: Set<Element>): TypeSpec.Builder {

        val array = ClassName("kotlin", "Array")
        val producerArrayOfStrings = array.parameterizedBy(WildcardTypeName.producerOf(String::class))

        return TypeSpec.classBuilder(GenerationConstants.Global.CLASS_NAME)
            .addModifiers(KModifier.SEALED)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(GenerationConstants.Parameters.TITLE, String::class)
                    .addParameter(GenerationConstants.Parameters.PATHS, producerArrayOfStrings)
                    .addParameter(GenerationConstants.Parameters.QUERY_PARAMS, producerArrayOfStrings)
                    .addParameter(GenerationConstants.Parameters.DYNAMIC_TITLE, Boolean::class)
                    .build()
            )
            .addProperty(
                PropertySpec.builder(GenerationConstants.Parameters.TITLE, String::class)
                    .initializer(GenerationConstants.Parameters.TITLE).build()
            )
            .addProperty(
                PropertySpec.builder(GenerationConstants.Parameters.PATHS, producerArrayOfStrings)
                    .initializer(GenerationConstants.Parameters.PATHS).build()
            )
            .addProperty(
                PropertySpec.builder(GenerationConstants.Parameters.QUERY_PARAMS, producerArrayOfStrings)
                    .initializer(GenerationConstants.Parameters.QUERY_PARAMS).build()
            )
            .addProperty(
                PropertySpec.builder(GenerationConstants.Parameters.DYNAMIC_TITLE, Boolean::class)
                    .initializer(GenerationConstants.Parameters.DYNAMIC_TITLE).build()
            )
            .addProperty(
                PropertySpec.builder(GenerationConstants.Parameters.NAME, String::class, KModifier.PRIVATE)
                    .initializer(CodeBlock.builder().add("this::class.simpleName ?: throw IllegalStateException()").build()).build()
            )
            .addFunction(
                generateRouteFunction()
            )
            .addFunction(
                generatePathFunction()
            )
            .addType(
                generateFromFunction(elements)
            )

    }

    private fun generatePathFunction(): FunSpec{

        val map = ClassName("kotlin.collections", "Map")
        val producerMapOfStrings = map.parameterizedBy(WildcardTypeName.producerOf(String::class), WildcardTypeName.producerOf(String::class))
        val producerMapOfStringsNullable = map.parameterizedBy(WildcardTypeName.producerOf(String::class), WildcardTypeName.producerOf(String::class.asTypeName().copy(nullable = true)))

        val complexPathTemplate = "/" + "$" + "{pathMap[it]}"
        val keyException = "$" + "it is not in the map"

        return FunSpec.builder(GenerationConstants.Functions.BUILD_PATH)
            .addModifiers(KModifier.PROTECTED)
            .addParameter(GenerationConstants.Parameters.PATH_MAP, producerMapOfStrings)
            .addParameter(GenerationConstants.Parameters.QUERY_MAP, producerMapOfStringsNullable)
            .returns(String::class)
            .addStatement("var pathToBuild = StringBuilder()")
            .addStatement("pathToBuild.append(${GenerationConstants.Parameters.NAME})")
            .beginControlFlow("if(${GenerationConstants.Parameters.PATH_MAP}.containsKey(%S))", GenerationConstants.Parameters.ANDROID_TITLE_VALUE)
            .addStatement("pathToBuild.append(%S)", "/")
            .addStatement("pathToBuild.append(${GenerationConstants.Parameters.PATH_MAP}[%S])", GenerationConstants.Parameters.ANDROID_TITLE_VALUE)
            .endControlFlow()
            .beginControlFlow("${GenerationConstants.Parameters.PATHS}.forEach{")
            .beginControlFlow("if(!${GenerationConstants.Parameters.PATH_MAP}.containsKey(it))")
            .addStatement("throw IllegalArgumentException(%P)", keyException)
            .endControlFlow()
            .addStatement("pathToBuild.append(%P)", complexPathTemplate)
            .endControlFlow()
            .beginControlFlow("if(queryMap.isNotEmpty())")
            .addStatement("pathToBuild.append(%S)", "?")
            .beginControlFlow("queryMap.forEach{(key, value) ->")
            .addStatement("pathToBuild.append(%P)", "$" + "key=$" + "value&")
            .endControlFlow()
            .addStatement("pathToBuild.deleteCharAt(pathToBuild.length -1)")
            .endControlFlow()
            .addStatement("return pathToBuild.toString()")
            .build()
    }

    private fun generateRouteFunction(): FunSpec{

        return FunSpec.builder(GenerationConstants.Functions.ROUTE)
            .returns(String::class)
            .addStatement("var route = StringBuilder(${GenerationConstants.Parameters.NAME})")
            .beginControlFlow("if(${GenerationConstants.Parameters.DYNAMIC_TITLE})")
            .addStatement("route.append(%S)", "/{${GenerationConstants.Parameters.ANDROID_TITLE_VALUE}}")
            .endControlFlow()
            .beginControlFlow("if(${GenerationConstants.Parameters.PATHS}.isNotEmpty())")
            .addStatement("val endPath = ${GenerationConstants.Parameters.PATHS}.joinToString(%S)", "}/{")
            .addStatement("route.append(%S)", "/{")
            .addStatement("route.append(endPath)")
            .addStatement("route.append(%S)", "}")
            .endControlFlow()
            .beginControlFlow("if(${GenerationConstants.Parameters.QUERY_PARAMS}.isNotEmpty())")
            .addStatement("route.append(%S)", "?")
            .beginControlFlow("${GenerationConstants.Parameters.QUERY_PARAMS}.forEach{ query ->")
            .addStatement("route.append(%P)", "$" + "query={$" + "query}&")
            .endControlFlow()
            .addStatement("route.deleteCharAt(route.length -1)")
            .endControlFlow()
            .addStatement("return route.toString()")
            .build()

    }

    private fun generateFromFunction(elements: Set<Element>): TypeSpec{
        val toReturns = ClassName(GenerationConstants.Global.PACKAGE_NAME, GenerationConstants.Global.CLASS_NAME)

        val function = FunSpec.builder(GenerationConstants.Functions.FROM_PATH)
            .addParameter(GenerationConstants.Parameters.PATH, String::class)
            .returns(toReturns)
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

        function.addStatement(" else -> throw RuntimeException()")

        function.addStatement("}")

        return TypeSpec.companionObjectBuilder()
            .addFunction(
                function.build()
            )
            .addProperty(
                PropertySpec.builder(GenerationConstants.Parameters.ANDROID_TITLE_KEY, String::class, KModifier.CONST)
                    .initializer("%S", GenerationConstants.Parameters.ANDROID_TITLE_VALUE).build()
            )
            .build()
    }

    private fun generateSealedClass(annotation: Destination, superClass: TypeSpec.Builder, name: String){

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
            .superclass(ClassName(GenerationConstants.Global.PACKAGE_NAME, GenerationConstants.Global.CLASS_NAME))
            .addSuperclassConstructorParameter("%S", annotation.title)
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
        const val CLASS_NAME = "Destinations"
    }

    object Functions{
        const val BUILD_PATH = "buildPath"
        const val ROUTE = "route"
        const val FROM_PATH = "fromPath"
    }

    object Parameters{

        const val PATHS = "paths"
        const val QUERY_PARAMS = "queryParams"
        const val PATH = "path"
        const val DYNAMIC_TITLE = "dynamicTitle"
        const val TITLE = "title"
        const val PATH_MAP = "pathMap"
        const val QUERY_MAP = "queryMap"
        const val NAME = "name"
        const val ANDROID_TITLE_KEY = "ANDROID_TITLE"
        const val ANDROID_TITLE_VALUE = "androidAppTitle"

    }

}