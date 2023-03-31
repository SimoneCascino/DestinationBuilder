package it.simonecascino.destinationbuilder.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.writeTo
import it.simonecascino.destinationbuilder.annotation.Destination
import it.simonecascino.destinationbuilder.base.BaseDestination

class DestinationProcessor(private val environment: SymbolProcessorEnvironment): SymbolProcessor {

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {

        //elements are filtered by KSFunctionDeclaration since only
        //functions can be annotated as Destination
        val processableSymbols = resolver.getSymbolsWithAnnotation(
            Destination::class.qualifiedName.toString()
        ).filterIsInstance<KSFunctionDeclaration>()

        val sourceFiles = processableSymbols.mapNotNull { it.containingFile }
            .toSet()

        val destinations = processableSymbols.map {
            AnnotationAndName(
                destination = it.getAnnotationsByType(Destination::class).first(),
                name = it.simpleName.getShortName()
            )

        }

        if (!destinations.iterator().hasNext()) return emptyList()

        val graphs = destinations.groupBy {
            it.destination.graphName
        }

        graphs.keys.forEach { graphName ->

            val annotations = graphs[graphName]

            val objectBuilder = TypeSpec.objectBuilder(graphName)
                .addFunction(
                    generateFromFunction(
                        annotations?.map {
                            it.name
                        } ?: emptyList(),
                        sourceFiles
                    )
                )

            sourceFiles.forEach {
                objectBuilder.addOriginatingKSFile(it)
            }

            annotations?.forEach {
                generateDestination(it.destination, objectBuilder, it.name, sourceFiles)
            }

            FileSpec.builder(
                GenerationConstants.Global.PACKAGE_NAME,
                graphName
            ).also { fileSpec ->
                fileSpec.addType(objectBuilder.build())
            }.build().writeTo(
                environment.codeGenerator,
                Dependencies(false, *sourceFiles.toTypedArray())
            )

        }

        return emptyList()
    }

    data class AnnotationAndName(
        val destination: Destination,
        val name: String
    )

    private fun generateFromFunction(
        names: List<String>,
        sources: Set<KSFile>
    ): FunSpec {

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

        sources.forEach {
            function.addOriginatingKSFile(it)
        }

        names.forEach {
            function.addStatement(" %S -> $it", it)
        }

        //todo: don't throw the exception here, since we may have multiple graph
        function.addStatement(" else -> throw RuntimeException()")

        function.addStatement("}")

        return function.build()

    }

    private fun generateDestination(
        annotation: Destination,
        superClass: TypeSpec.Builder,
        name: String,
        sources: Set<KSFile>
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

        objectSpecBuilder.addFunction(generateInnerPathFunction(annotation, objectSpecBuilder, sources))

        sources.forEach {
            objectSpecBuilder.addOriginatingKSFile(it)
        }

        superClass.addType(
            objectSpecBuilder.build()
        )

    }

    private fun generateInnerPathFunction(
        annotation: Destination,
        objectSpecBuilder: TypeSpec.Builder,
        sources: Set<KSFile>
    ): FunSpec {

        val pathFunSpecBuilder = FunSpec.builder(GenerationConstants.Functions.BUILD_PATH)
            .returns(String::class)

        sources.forEach {
            pathFunSpecBuilder.addOriginatingKSFile(it)
        }

        val pathMap = mutableMapOf<String, String>()
        pathMap[""] = ""

        pathFunSpecBuilder.addStatement("val pathMap = mutableMapOf<String, String>()")
        pathFunSpecBuilder.addStatement("val queryMap = mutableMapOf<String, String?>()")

        if(annotation.dynamicTitle){
            pathFunSpecBuilder
                .addStatement("pathMap[%S] = ${GenerationConstants.Parameters.ANDROID_TITLE_VALUE}",
                    GenerationConstants.Parameters.ANDROID_TITLE_VALUE
                )
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

}