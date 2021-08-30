package it.simonecascino.destinationbuilder

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
                else Destinations.HomePage

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

                    NavHost(navController = navController, startDestination = Destinations.HomePage.route()){

                        composable(Destinations.HomePage.route()){
                            HomePage{
                                navController.navigate(
                                    Destinations.NextDestination.buildPath(URLEncoder.encode("Dynamic title", "UTF-8"))
                                )
                            }
                        }

                        composable(Destinations.NextDestination.route()){
                            NextDestination()
                        }

                    }

                }

            }
        }
    }
}

@Composable
@Destination(
    title = "Home"
)
fun HomePage(goToNext: () -> Unit){

    Column {
        Text(
            text = "Start destination"
        )

        Button(onClick = { goToNext() }) {
            Text(text= "go to next")
        }
    }

}

@Destination(
    dynamicTitle = true
)
@Composable
fun NextDestination(){



}

