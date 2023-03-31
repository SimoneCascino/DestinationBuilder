package it.simonecascino.destinationbuilder.base

abstract class BaseDestination(
    private val paths: Array<out String>,
    private val queryParams: Array<out String>,
    private val dynamicTitle: Boolean
) {

    private val name: String = this::class.simpleName ?: throw IllegalStateException()

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
            pathToBuild.append(pathMap[ANDROID_TITLE])
        }
        paths.forEach{
            if(!pathMap.containsKey(it)) {
                throw IllegalArgumentException("$it is not in the map")
            }
            pathToBuild.append("/${pathMap[it]}")
        }
        if(queryMap.isNotEmpty()) {
            pathToBuild.append("?")
            queryMap.forEach{(key, value) ->
                pathToBuild.append("$key=$value&")
            }
            pathToBuild.deleteCharAt(pathToBuild.length -1)
        }
        return pathToBuild.toString()
    }

    companion object{
        const val ANDROID_TITLE: String = "androidAppTitle"
    }

}