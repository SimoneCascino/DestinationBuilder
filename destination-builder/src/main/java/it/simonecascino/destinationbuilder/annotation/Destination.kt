package it.simonecascino.destinationbuilder.annotation

@MustBeDocumented
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Destination(
    val title: String = "",
    val dynamicTitle: Boolean = false,
    val paths: Array<String> = [],
    val queryParams: Array<String> = []
)
