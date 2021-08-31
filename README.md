# DestinationBuilder
Google recently released Jetpack compose, the new UI toolkit for Android. It also released navigation for Jetpack compose, a component which help you to navigate between composable functions ([here the official documentation](https://developer.android.com/jetpack/compose/navigation), you must know about it to understand the purpose of this library).

Navigation provide some nice features, but requires the definition of all the destinations. It also requires to handle some logic to generate the routes which are used to navigate. The most recommended approach is to use enum or sealed classes to do this. Destination Builder autogenerate a sealed class for you, via annotation processor, handling several things, avoiding boilerplate and mainteinance problems.

# How it works?
To trigger the generation of the sealed class, you must annotate a composable function with the **Destination** annotation. So, suppose that the first screen you want to show in your app is a composable functiona called **FirstDestination**, you have the following: 

``` kotlin
@Composable
@Destination
fun FirstDestination(){

}
```

This will generate a sealed class called **Destinations** and its first child, an object wrapped into it, called **FirstDestination**. It also generate several helper functions, we will se them later:

``` kotlin
public sealed class Destinations(
  public val title: String,
  public val paths: Array<out String>,
  public val queryParams: Array<out String>,
  public val dynamicTitle: Boolean
) {
  private val name: String = this::class.simpleName ?: throw IllegalStateException()

  public fun route(): String {
    var route = StringBuilder(name)
    if(dynamicTitle) {
      route.append("/{androidAppTitle}")
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
        route.append("""$query={$query}&""")
      }
      route.deleteCharAt(route.length -1)
    }
    return route.toString()
  }

  protected fun buildPath(pathMap: Map<out String, out String>, queryMap: Map<out String, out
      String?>): String {
    var pathToBuild = StringBuilder()
    pathToBuild.append(name)
    if(pathMap.containsKey("androidAppTitle")) {
      pathToBuild.append("/")
      pathToBuild.append(pathMap["androidAppTitle"])
    }
    paths.forEach{
      if(!pathMap.containsKey(it)) {
        throw IllegalArgumentException("""$it is not in the map""")
      }
      pathToBuild.append("""/${pathMap[it]}""")
    }
    if(queryMap.isNotEmpty()) {
      pathToBuild.append("?")
      queryMap.forEach{(key, value) ->
        pathToBuild.append("""$key=$value&""")
      }
      pathToBuild.deleteCharAt(pathToBuild.length -1)
    }
    return pathToBuild.toString()
  }

  public companion object {
    public const val ANDROID_TITLE: String = "androidAppTitle"

    public fun fromPath(path: String): Destinations {
      val name = if(path.contains("/")) {
        path.split("/").first()
      }
      else if (path.contains("?")) {
        path.split("?").first()
      }
      else path
      return when(name){
       "FirstDestination" -> FirstDestination
       else -> throw RuntimeException()
      }
    }
  }

  public object FirstDestination : Destinations("", arrayOf(), arrayOf(), false) {
    public fun buildPath(): String {
      val pathMap = mutableMapOf<String, String>()
      val queryMap = mutableMapOf<String, String?>()
      return super.buildPath(pathMap, queryMap)
    }
  }
}
```

At first glance it could appear complicated, but everything will be cleared later. So keep the focus on the sealed class (Destinations) and its child (FirstDestination). 

Lets start to build the navigation graph. As described in the navigation documentation, you first have to use the **NavHost** composable function. It requires the navController, a string which represent the route of the start destination and a lambda which represent the NavGraphBuilder.

``` kotlin
NavHost(navController = navController, startDestination = Destinations.FirstDestination.route()){

}
```


