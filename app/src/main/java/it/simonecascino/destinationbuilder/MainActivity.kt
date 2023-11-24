package it.simonecascino.destinationbuilder

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import it.simonecascino.destination.AppGraphResolver
import it.simonecascino.destination.FeatureGraph
import it.simonecascino.destination.MainGraph
import it.simonecascino.destinationbuilder.annotations.Destination
import it.simonecascino.destinationbuilder.annotations.Graph
import it.simonecascino.destinationbuilder.base.BaseDestination
import it.simonecascino.destinationbuilder.ui.theme.DestinationBuilderTheme
import java.net.URLDecoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DestinationBuilderTheme {

                val navController = rememberNavController()

                val navBackStackEntry by navController.currentBackStackEntryAsState()

                val currentRoute = navBackStackEntry?.destination?.route

                val currentDestination = if(currentRoute != null)
                    AppGraphResolver.resolve(currentRoute)
                else MainGraph.FirstDestination

                val title = URLDecoder.decode(
                    navBackStackEntry?.arguments?.getString(BaseDestination.ANDROID_TITLE) ?: currentDestination.route(),
                    "UTF-8"
                ).let {
                    if(it.length > 30)
                        it.take(27).plus("...")

                    else it
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {

                        TopAppBar(
                            title = {
                                Text(text = title)
                            }
                        )

                    }
                ) {
                    AppGraph(
                        contentPadding = it,
                        navController = navController
                    )
                }

            }
        }
    }
}

@Graph(
    graphs = [
        MainGraph::class,
        FeatureGraph::class
    ]
)
@Composable
fun AppGraph(
    contentPadding: PaddingValues,
    navController: NavHostController
){
    NavHost(
        modifier = Modifier.padding(contentPadding),
        navController = navController,
        startDestination = MainGraph.FirstDestination.route()
    ){

        composable(route = MainGraph.FirstDestination.route()){

            FirstDestination {
                navController.navigate(
                    MainGraph.SecondDestination.buildPath("Ciao", 2.toString())
                )
            }
        }

        composable(
            route = MainGraph.SecondDestination.route(),
            arguments = listOf(
                navArgument(MainGraph.SecondDestination.KEY_PARAM_1){
                    type = NavType.StringType
                },
                navArgument(MainGraph.SecondDestination.KEY_PARAM_2){
                    type = NavType.IntType
                }
            )
        ){

            val param1 = it.arguments?.getString(MainGraph.SecondDestination.KEY_PARAM_1) ?: ""
            val param2 = it.arguments?.getInt(MainGraph.SecondDestination.KEY_PARAM_2) ?: 0

            SecondDestination(param1, param2){
                navController.navigate(
                    MainGraph.ThirdDestination.buildPath("Third destination")
                )
            }
        }

        composable(
            route = MainGraph.ThirdDestination.route(),
            arguments = listOf(
                navArgument(BaseDestination.ANDROID_TITLE){
                    type = NavType.StringType
                }
            )
        ){

            ThirdDestination{
                navController.navigate(
                    MainGraph.FourthDestination.buildPath("https://www.google.it")
                )
            }
        }

        composable(
            route = MainGraph.FourthDestination.route(),
            arguments = listOf(
                navArgument(MainGraph.FourthDestination.KEY_QUERY1){
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(MainGraph.FourthDestination.KEY_QUERY2){
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(MainGraph.FourthDestination.KEY_QUERY3){
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "queryDefault"
                }
            )

        ){

            val query1 = it.arguments?.getString(MainGraph.FourthDestination.KEY_QUERY1)
            val query2 = it.arguments?.getString(MainGraph.FourthDestination.KEY_QUERY2)
            val query3 = it.arguments?.getString(MainGraph.FourthDestination.KEY_QUERY3)

            FourthDestination(query1 = query1, query2 = query2, query3 = query3){
                navController.navigate(FeatureGraph.SixthDestination.buildPath("test"))
            }

        }

        navigation(
            FeatureGraph.SixthDestination.route(),
            FeatureGraph.graphRoute
        ){
            composable(FeatureGraph.SixthDestination.route()){
                SixthDestination()
            }
        }

    }

}

@Composable
@Destination(graphName = "MainGraph")
fun FirstDestination(goToNext: () -> Unit){

    DestinationLayout(text = stringResource(id = R.string.app_name)) {
        goToNext()
    }

}

@Destination(
    graphName = "MainGraph",
    paths = ["param1", "param2"]
)
@Composable
fun SecondDestination(param1: String, param2: Int, goToNext: () -> Unit){

    DestinationLayout(text = "First param is $param1, second param (Int) is $param2") {
        goToNext()
    }

}

@Destination(
    graphName = "MainGraph",
    dynamicTitle = true
)
@Composable
fun ThirdDestination(goToNext: () -> Unit){

    DestinationLayout(text = "Third destination") {
        goToNext()
    }

}

@Destination(
    graphName = "MainGraph",
    queryParams = ["query1", "query2", "query3"]
)
@Composable
fun FourthDestination(query1: String?, query2: String?, query3: String?, onClick: () -> Unit){

    DestinationLayout(text = "First query is $query1, second query is $query2, third query is $query3") {
        onClick()
    }

}

@Composable
@Destination(
    graphName = "MainGraph",
    paths = ["param1", "param2"],
    queryParams = ["query1", "query2", "query3"]
)
fun FifthDestination(){

}

@Destination(
    graphName = "FeatureGraph",
    destinationName = "testname",
    paths = ["test"]
)
@Composable
fun SixthDestination(){
    Text("Hello")
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

