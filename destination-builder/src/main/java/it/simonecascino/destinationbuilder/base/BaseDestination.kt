package it.simonecascino.destinationbuilder.base

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

import java.net.URLEncoder

abstract class BaseDestination(
    private val paths: Array<out String>,
    private val queryParams: Array<out String>,
    val dynamicTitle: Boolean
) {

    protected open val name: String = this::class.simpleName ?: throw IllegalStateException()

    fun route(): String {
        val route = StringBuilder(name)
        if(dynamicTitle) {
            route.append("/{$ANDROID_TITLE}")
        }
        if(paths.isNotEmpty()) {
            val endPath = paths.joinToString("}/{")
            route.append("/{")
            route.append(endPath)
            route.append("}")
        }
        if(queryParams.isNotEmpty()) {
            route.append("?")
            queryParams.forEach{ query ->
                route.append("$query={$query}&")
            }
            route.deleteCharAt(route.length -1)
        }
        return route.toString()
    }

    protected fun buildPath(pathMap: Map<String, String>, queryMap: Map<String, String?>): String {
        val pathToBuild = StringBuilder()
        pathToBuild.append(name)
        if(pathMap.containsKey(ANDROID_TITLE)) {
            pathToBuild.append("/")
            val encodedTitle = URLEncoder.encode(pathMap[ANDROID_TITLE], "UTF-8")
            pathToBuild.append(encodedTitle)
        }
        paths.forEach{
            if(!pathMap.containsKey(it)) {
                throw IllegalArgumentException("$it is not in the map")
            }
            val encodedPath = URLEncoder.encode(pathMap[it], "UTF-8")
            pathToBuild.append("/$encodedPath")
        }
        if(queryMap.isNotEmpty()) {
            pathToBuild.append("?")
            queryMap.forEach{(key, value) ->
                val encodedValue = URLEncoder.encode(value, "UTF-8")
                pathToBuild.append("$key=$encodedValue&")
            }
            pathToBuild.deleteCharAt(pathToBuild.length -1)
        }

        return pathToBuild.toString()
    }

    companion object{
        const val ANDROID_TITLE: String = "androidAppTitle"
    }

}