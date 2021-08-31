# DestinationBuilder
Google released Jetpack compose, the new UI toolkit for Android. It also released navigation for Jetpack compose, a component which help you to navigate between composable functions.
Navigation provide some nice features, but requires the definition of all the destinations. It also requires to handle some logic in the generation of the routes which are used to navigate. The most recommended approach is to use enum or sealed classes to do this. Destination Builder autogenerate the sealed classes for you, via annotation processor, handling several things, avoiding boilerplate and mainteinance problems.
