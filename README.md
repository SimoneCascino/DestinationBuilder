# Summary

- [What is DestinationBuilder?](https://github.com/SimoneCascino/DestinationBuilder#destinationbuilder)
- [Integration](https://github.com/SimoneCascino/DestinationBuilder#integration)
- [How it works](https://github.com/SimoneCascino/DestinationBuilder#how-it-works)
- [How to use it](https://github.com/SimoneCascino/DestinationBuilder#how-to-use-it)
- [Passing arguments between destinations](https://github.com/SimoneCascino/DestinationBuilder#passing-arguments-between-destinations)
- [Optional arguments](https://github.com/SimoneCascino/DestinationBuilder#optional-arguments)
- [How to get the arguments](https://github.com/SimoneCascino/DestinationBuilder#how-to-get-the-arguments)
- [Obtain the destination from the navController](https://github.com/SimoneCascino/DestinationBuilder#obtain-the-destination-from-the-navcontroller)
- [Dynamic title](https://github.com/SimoneCascino/DestinationBuilder#dynamic-title)
- [Custom destination name](https://github.com/SimoneCascino/DestinationBuilder#custom-destination-name)
- [Multimodular/Multigraph project](https://github.com/SimoneCascino/DestinationBuilder#multimodularmultigraph-project)

# DestinationBuilder
Google released Jetpack Compose, the new UI toolkit for Android. It also released navigation for Jetpack Compose, a component which allows building a navigation graph and enabling the navigation between composable functions ([here the official documentation](https://developer.android.com/jetpack/compose/navigation). Knowledge of Jetpack Compose Navigation is required to understand the purpose of this library.

Navigation provide some nice features, but requires the definition of all the destinations. It also requires to handle some logic to generate the routes which are used to navigate. The most recommended approach is to use enums or sealed classes. Destination Builder autogenerates such classes for you, via annotation processor, handling several things, avoiding boilerplate and mainteinance problems.

# Integration

This library uses the ksp annotation processor, [Here some info about it](https://developer.android.com/build/migrate-to-ksp). 
The library is hosted in jitpack, so ensure to have it in the project repositories, for example, in the settings.gradle file:

```kotlin
repositories {
        google()
        maven { url = uri("https://jitpack.io") }   <---
        mavenCentral()
    }
```

Then just add (kotlin-dsl):

```kotlin
implementation("com.github.SimoneCascino:DestinationBuilder:1.1.3")
ksp("com.github.SimoneCascino:DestinationBuilder:1.1.3")
```

or in groovy:

```
implementation 'com.github.SimoneCascino:DestinationBuilder:1.1.3'
ksp 'com.github.SimoneCascino:DestinationBuilder:1.1.3'
```

# How it works?
To trigger the generation, annotate a composable function with the **Destination** annotation. So, suppose that the first screen you want to show in your app is a composable function called **FirstDestination**: 

``` kotlin
@Destination
@Composable
fun FirstScreen(){

}
```

This will generate a sealed class called **Destinations** which extends **BaseDestination** and an object wrapped into it, called **FirstScreen**, which extends **Destinations**. It also generates other things, we will se them later:

``` kotlin
public object Destinations {
  public fun fromPath(path: String): BaseDestination? {
    val name = if(path.contains("/")) {
      path.split("/").first()
    }
    else if (path.contains("?")) {
      path.split("?").first()
    }
    else path
    return when(name){
     "FirstScreen" -> FirstScreen
     else -> null
    }
  }

  public object FirstScreen : BaseDestination(arrayOf(), arrayOf(), false) {
    public fun buildPath(): String {
      val pathMap = mutableMapOf<String, String>()
      val queryMap = mutableMapOf<String, String?>()
      return super.buildPath(pathMap, queryMap)
    }
  }
}
```

# How to use it 

Let's start to build the navigation graph:

``` kotlin
NavHost(navController = navController, startDestination = Destinations.FirstScreen.route()){

    composable(Destinations.FirstScreen.route()){

    }

}
```

**Destinations.FirstScreen.route()** returns the string **FirstScreen**.

# Passing arguments between destinations

Now imagine that FirstScreen contains a list of items. Clicking on an item you can see its detail screen. To load the proper detail screen, we need to pass something, usually the id of the item, to the next destination. 

According to the documentation, you should create a route like this:

```kotlin
"detailscreen/{id}"
```

This route should be used in the NavHost composable dsl, so:

``` kotlin
NavHost(navController = navController, startDestination = Destinations.FirstScreen.route()){

    composable(Destinations.FirstScreen.route()){

    }
    
    composable("detailscreen/{id}"){

    }

}
```

To build such destination write the following:

```kotlin
@Destination(
    paths = ["id"]
)
@Composable
fun DetailScreen(){

}
```

If you look now the generated object you will find this other object inside:

```kotlin
public object DetailScreen : BaseDestination(arrayOf("id"), arrayOf(), false) {
    public const val KEY_ID: String = "id"

    public fun buildPath(id: String): String {
      val pathMap = mutableMapOf<String, String>()
      val queryMap = mutableMapOf<String, String?>()
      pathMap["id"] = id
      return super.buildPath(pathMap, queryMap)
    }
  }
  ```
  
Destinations.DetailScreen.route() returns the string **DetailScreen/{id}**, which is pretty much what we wanted to obtain, but in pascal case. It is possible to customize it, I will explain how later.
So we can replace the hardcoded string in the composable dsl of the navigation graph:

```kotlin
composable(Destinations.DetailScreen.route()){

}
```

So now we have another autogenerated route, which avoid the developer to manually maintains hardcoded strings. But such route can be used to define a destination, we also need to have something which can be used with the NavHostController to perform the navigation. To navigate we need to do something like this:

```kotlin
val navController = rememberNavController
navController.navigate("...")
```

In the navigate function we need to pass the route of the destination with the argument properly replaced, so if the id of the item is **1**, to navigate to the DetailScreen destination, we need to write the following:

```kotlin
navController.navigate("DetailScreen/1")
```

This can be easily done with the generated object. Checking the code posted above, in the generated DetailScreen object we have this function:

```kotlin
public fun buildPath(id: String): String {
      val pathMap = mutableMapOf<String, String>()
      val queryMap = mutableMapOf<String, String?>()
      pathMap["id"] = id
      return super.buildPath(pathMap, queryMap)
}
```

**Destinations.DetailScreen.buildPath("1")** returns the string **DetailScreen/1** which is exactly what we need. The attributes to pass to the buildPath functions are always strings (so if the id is an Int, you will have to convert it into a string) and they always reflect what you wrote in the annotation arguments. You can pass all the arguments you want:

```kotlin
@Destination(
    paths = ["id", "anotherArgument", "andAnother"]
)
@Composable
fun ThirdScreen(){

}

//will generate the following:

public object ThirdScreen : BaseDestination(arrayOf("id","anotherArgument","andAnother"),
      arrayOf(), false) {
    public const val KEY_ID: String = "id"

    public const val KEY_ANOTHERARGUMENT: String = "anotherArgument"

    public const val KEY_ANDANOTHER: String = "andAnother"

    public fun buildPath(
      id: String,
      anotherArgument: String,
      andAnother: String,
    ): String {
      val pathMap = mutableMapOf<String, String>()
      val queryMap = mutableMapOf<String, String?>()
      pathMap["id"] = id
      pathMap["anotherArgument"] = anotherArgument
      pathMap["andAnother"] = andAnother
      return super.buildPath(pathMap, queryMap)
    }
  }
```

# Optional arguments

According to the navigation documentation, you can also pass optional arguments between destinations. To pass optional arguments, we need to use them as query params, for example a possible destination route with optional argument is **FourthDestination?optionalArg={optionalArg}**. So we can do:

```kotlin
@Destination(
    queryParams = ["optionalArg"]
)
@Composable
fun FourthDestination(){

}
```

To generate:

```kotlin
public object FourthDestination : BaseDestination(arrayOf(), arrayOf("optionalArg"), false) {
    public const val KEY_optionalArg: String = "optionalArg"

    public fun buildPath(optionalArg: String? = null): String {
      val pathMap = mutableMapOf<String, String>()
      val queryMap = mutableMapOf<String, String?>()
      if(optionalArg != null) {
        queryMap["optionalArg"] = optionalArg
      }
      return super.buildPath(pathMap, queryMap)
    }
  }
```

Notice that in the **buildPath** function the **optionalArg** attribute is nullable, null by default. So **Destinations.FourthDestination.buildPath()** returns the string **FourthDestination** and **Destinations.FourthDestination.buildPath("hello")** returns **FourthDestination?optionalArg=hello**.

Paths and query params can be combined, for example:

```kotlin
@Destination(
    paths = ["id", "anotherArgument", "andAnother"],
    queryParams = ["optionalArg", "optionalArg2"]
)
@Composable
fun FifthDestination(){

}

//generates the following

public object FifthDestination : BaseDestination(arrayOf("id","anotherArgument","andAnother"),
      arrayOf("optionalArg","optionalArg2"), false) {
    public const val KEY_ID: String = "id"

    public const val KEY_ANOTHERARGUMENT: String = "anotherArgument"

    public const val KEY_ANDANOTHER: String = "andAnother"

    public const val KEY_OPTIONALARG: String = "optionalArg"

    public const val KEY_OPTIONALARG2: String = "optionalArg2"

    public fun buildPath(
      id: String,
      anotherArgument: String,
      andAnother: String,
      optionalArg: String? = null,
      optionalArg2: String? = null,
    ): String {
      val pathMap = mutableMapOf<String, String>()
      val queryMap = mutableMapOf<String, String?>()
      pathMap["id"] = id
      pathMap["anotherArgument"] = anotherArgument
      pathMap["andAnother"] = andAnother
      if(optionalArg != null) {
        queryMap["optionalArg"] = optionalArg
      }
      if(optionalArg2 != null) {
        queryMap["optionalArg2"] = optionalArg2
      }
      return super.buildPath(pathMap, queryMap)
    }
  }
```

**Destinations.FifthDestination.route()** returns **FifthDestination/{id}/{anotherArgument}/{andAnother}?optionalArg={optionalArg}&optionalArg2={optionalArg2}**. Also check the following:

```kotlin
Destinations.FifthDestination.buildPath(
                    id = "1",
                    anotherArgument = "hello",
                    andAnother = "hi",
                    optionalArg = "good",
                    optionalArg2 = "last"
)

//creates the string FifthDestination/1/hello/hi?optionalArg=good&optionalArg2=last
```
# How to get the arguments

I recommend to read the official navigation documentation to understand how to obtain the arguments passed to a destination. If you use hilt, you can add the following dependency:

```kotlin
implementation 'androidx.hilt:hilt-navigation-compose:x.y.z'
```

So for each destination of the navigation graph you can scope a ViewModel in this way:

```kotlin
val viewModel: YourViewModel = hiltViewModel()
```

In this ViewModel you can pass a SavedStateInstance object in the constructor. Hilt automatically handle it. The navigation arguments are inside it, and you can get them using the templates passed in the route as keys. For example, for the route DetailScreen/{id}, when a navigation is performed passing 1 as id (so DetailScreen/1), the **1** can be obtained in this way:

```kotlin
val id: String = savedStateHandle["id"] ?: ""
```

This is very confortable to me, since all the business logic is handled on my ViewModels and I prefer to have the navigation arguments in it. But notice that the hardcoded string **id** is required to get the value. Well in the generated objects there are constants available for each arguments. For example, in the DetailScreen:

```kotlin
public object DetailScreen : BaseDestination(arrayOf("id"), arrayOf(), false) {
    public const val KEY_ID: String = "id"
    ...
```

So the statement Destinations.DetailScreen.KEY_ID returns the proper key to use to get the value from the SavedStateHandle object. 

Notice that when the buildPath function is called with the required arguments, such arguments are encoded, because the resulting string must have the format of an url. So depending on what has been passed, a decoding might be needed: **Uri.decode(argument_to_decode, "UTF-8")**

# Obtain the destination from the navController

The NavHostController contains information about the current destination. From its route is it possible to obtain a BaseDestination object:

```kotlin
val currentRoute = navBackStackEntry?.destination?.route

if(currentRoute != null)
  val currentDestination = Destinations.fromPath(currentRoute)
```

# Dynamic title

A very common use case in an Android app is to have a title which must be displayed in an app bar. Very often such title needs to be passed as argument of the destination. Since this can happen often in an app, so I added another attribute to handle it:

```kotlin
@Destination(
    dynamicTitle = true
)
@Composable
fun SixthScreen(){

}
```

Doing this is exaclty like doing:

```kotlin
@Destination(
    paths = ["androidAppTitle"]
)
@Composable
fun SixthScreen(){

}
```

So the route obtained from this destination will be SixthScreen/{androidAppTitle} and calling the buildPath function will require to pass the title attribute. But dynamicTitle is also an attribute of the abstract class BaseDestination, which is the base class of each generated destination, is it possible to know if the title is present doing Destinations.SixthScreen.dynamicTitle, which returns a boolean. Such title can be obtained as any other arguments, and if the app bar is outside the navigation graph, it can be obtained in this way:

```kotlin
val currentRoute = navBackStackEntry?.destination?.route

//obtain the destination
if(currentRoute != null){
  val currentDestination = Destinations.fromPath(currentRoute)
  
  if(currentDestination?.dynamicTitle == true){
      val title = Uri.decode(navBackStackEntry?.arguments?.getString(BaseDestination.ANDROID_TITLE) ?: "")
  }
}
```

Notice that the attribute got from the navBackStackEntry is decoded. As explained above, any attribute used in the buildPath function is encoded to ensure that the generated route is a valid uri. A title will probably contains spaces, so without a decoding the spaces will not be present.

# Custom destination name

By default the route of the generated destination matches the name of the generated class, which matches the name of the function where the annotation is applied. But it is also possible to customize it.

```kotlin
@Destination(
    destinationName = "bettername"
)
@Composable
fun SeventhScreen(){
    
}
```

In this case, **Destinations.SeventhScreen.route()** returns the string **bettername**. While this can be a nice feature to use to improve the generated uri style or even to create multiple destinations from the same composable function (read below), it should be used carefully. If the same destination name is used in the same navigation graph the app will crash, but the build process will succeed, which won't happen if a function with the same name is marked as destination (the build process will fail, since it will try to generate 2 objects with the same name, so it will lead to a compilation error).

# Multimodular/multigraph project

It is a good practice to split the navigation into multiple navigation graph, especially in a multimodular project, where a feature module can contains its own graph. This can cause some problems.

- In a single module with multiple graphs, all the destinations will be wrapped into a unique **Destinations** class, which doesn't give the logical separation which is expected. For example, the function **Destinations.fromPath(route)** will always check all the possible destinations, even if the route passed belongs to a specific navigation graph.
- In multiple modules with different navigation graphs, each module will have its own **Destinations** file with the same package, which will cause conflicts and compile time errors.

To solve these problems there is another attribute which can be used in the annotation:

```kotlin
@Destination(
    graphName = "FeatureGraph"
)
@Composable
fun FeatureDestination(){
    
}
```

This generates a new file called **FeatureGraph** with the following code:

```kotlin
public sealed class FeatureGraph(
  paths: Array<out String>,
  queryParams: Array<out String>,
  dynamicTitle: Boolean,
) : BaseDestination(paths, queryParams, dynamicTitle) {
  public companion object {
    public fun fromPath(path: String): BaseDestination? {
      val name = if(path.contains("/")) {
        path.split("/").first()
      }
      else if (path.contains("?")) {
        path.split("?").first()
      }
      else path
      return when(name){
       "FeatureDestination" -> FeatureDestination
       else -> null
      }
    }
  }

  public object FeatureDestination : FeatureGraph(arrayOf(), arrayOf(), false) {
    public fun buildPath(): String {
      val pathMap = mutableMapOf<String, String>()
      val queryMap = mutableMapOf<String, String?>()
      return super.buildPath(pathMap, queryMap)
    }
  }
}
```

So now there are 2 files, Destinations and FeatureGraph, and no conflicts between them. I recommend to always split the destinations into multiple graphs if the app has multipple modules or if it has multiple graphs. If a destination can belongs to more than 1 graph, the Destination annotation can be re-applied.
