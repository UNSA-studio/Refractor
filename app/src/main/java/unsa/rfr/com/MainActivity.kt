package unsa.rfr.com

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import unsa.rfr.com.ui.screens.*
import unsa.rfr.com.ui.theme.RefractorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RefractorTheme {
                AppNavGraph()
            }
        }
    }
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("create") { CreateRoomScreen(navController) }
        composable(
            "room/{roomId}/{role}",
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("role") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            val role = backStackEntry.arguments?.getString("role") ?: "viewer"
            RoomScreen(roomId, role, navController)
        }
        composable("settings") { SettingsScreen(navController) }
    }
}
