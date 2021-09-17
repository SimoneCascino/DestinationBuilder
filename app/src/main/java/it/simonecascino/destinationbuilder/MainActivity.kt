package it.simonecascino.destinationbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.simonecascino.destination.Destinations
import it.simonecascino.destinationbuilder.annotation.Destination
import it.simonecascino.destinationbuilder.ui.theme.DestinationBuilderTheme
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DestinationBuilderTheme {

                val navController = rememberNavController()

                val navBackStackEntry by navController.currentBackStackEntryAsState()

                val currentRoute = navBackStackEntry?.destination?.route

                val currentDestination: Destinations = if(currentRoute != null)
                    Destinations.fromPath(currentRoute)
                else Destinations.FirstDestination

                val title = URLDecoder.decode(
                    navBackStackEntry?.arguments?.getString(Destinations.ANDROID_TITLE) ?: currentDestination.title,
                    "UTF-8"
                ).let {
                    if(it.length > 30)
                        it.take(27).plus("...")

                    else it
                }


                Scaffold(
                    topBar = {

                        TopAppBar(
                            title = {
                                Text(text = title)
                            }
                        )

                    }
                ) {

                    NavHost(navController = navController, startDestination = Destinations.FirstDestination.route()){

                        composable(route = Destinations.FirstDestination.route()){

                            FirstDestination {
                                navController.navigate(
                                    Destinations.SecondDestination.buildPath("Ciao", 2.toString())
                                )
                            }
                        }

                        composable(
                            route = Destinations.SecondDestination.route(),
                            arguments = listOf(
                                navArgument(Destinations.SecondDestination.KEY_param1){
                                    type = NavType.StringType
                                },
                                navArgument(Destinations.SecondDestination.KEY_param2){
                                    type = NavType.IntType
                                }
                            )
                        ){

                            val param1 = it.arguments?.getString(Destinations.SecondDestination.KEY_param1) ?: ""
                            val param2 = it.arguments?.getInt(Destinations.SecondDestination.KEY_param2) ?: 0

                            SecondDestination(param1, param2){
                                navController.navigate(
                                    Destinations.ThirdDestination.buildPath(URLEncoder.encode("Third destination", "UTF-8"))
                                )
                            }
                        }

                        composable(
                            route = Destinations.ThirdDestination.route(),
                            arguments = listOf(
                                navArgument(Destinations.ANDROID_TITLE){
                                    type = NavType.StringType
                                }
                            )
                        ){

                            ThirdDestination{
                                navController.navigate(
                                    Destinations.FourthDestination.buildPath("Query1")
                                )
                            }
                        }

                        composable(
                            route = Destinations.FourthDestination.route(),
                            arguments = listOf(
                                navArgument(Destinations.FourthDestination.KEY_query1){
                                    type = NavType.StringType
                                    nullable = true
                                },
                                navArgument(Destinations.FourthDestination.KEY_query2){
                                    type = NavType.StringType
                                    nullable = true
                                },
                                navArgument(Destinations.FourthDestination.KEY_query3){
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = "queryDefault"
                                }
                            )

                        ){

                            val query1 = it.arguments?.getString(Destinations.FourthDestination.KEY_query1)
                            val query2 = it.arguments?.getString(Destinations.FourthDestination.KEY_query2)
                            val query3 = it.arguments?.getString(Destinations.FourthDestination.KEY_query3)

                            FourthDestination(query1 = query1, query2 = query2, query3 = query3)

                        }

                    }

                }

            }
        }
    }
}

@Composable
@Destination
fun FirstDestination(goToNext: () -> Unit){

    DestinationLayout(text = "First destination") {
        goToNext()
    }

}

@Destination(
    paths = ["param1", "param2"]
)
@Composable
fun SecondDestination(param1: String, param2: Int, goToNext: () -> Unit){

    DestinationLayout(text = "First param is $param1, second param (Int) is $param2") {
        goToNext()
    }

}

@Destination(
    dynamicTitle = true
)
@Composable
fun ThirdDestination(goToNext: () -> Unit){

    DestinationLayout(text = "Third destination") {
        goToNext()
    }

}

@Destination(
    queryParams = ["query1", "query2", "query3"]
)
@Composable
fun FourthDestination(query1: String?, query2: String?, query3: String?){

    DestinationLayout(text = "First query is $query1, second query is $query2, third query is $query3") {

    }

}

@Composable
@Destination(
    paths = ["param1", "param2"],
    queryParams = ["query1", "query2", "query3"]
)
fun FifthDestination(){

}

@Destination(
    title = "Title"
)
@Composable
fun SixthDestination(){

}


@Composable
fun DestinationLayout(text: String, goToNext: () -> Unit){

    Column(modifier = Modifier.padding(16.dp)) {

        Text(text = text)

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { goToNext() }) {
            Text(text= "go to next")
        }
    }

}

