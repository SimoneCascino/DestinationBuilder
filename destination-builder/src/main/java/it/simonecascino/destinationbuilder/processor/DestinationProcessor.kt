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

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ksp.writeTo
import it.simonecascino.destinationbuilder.annotation.Destination
import it.simonecascino.destinationbuilder.generation.CodeGeneration

class DestinationProcessor(private val environment: SymbolProcessorEnvironment): SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {

        //elements are filtered by KSFunctionDeclaration since only
        //functions can be annotated as Destination
        val processableSymbols = resolver.getSymbolsWithAnnotation(
            Destination::class.qualifiedName.toString()
        ).filterIsInstance<KSFunctionDeclaration>()

        if (!processableSymbols.iterator().hasNext()) return emptyList()

        val sourceFiles = processableSymbols.mapNotNull { it.containingFile }
            .toSet()

        CodeGeneration(
            sourceFiles,
            processableSymbols
        ).generateFileSpecs().forEach {
            it.writeTo(
                environment.codeGenerator,
                Dependencies(false, *sourceFiles.toTypedArray())
            )
        }

        return emptyList()
    }

}