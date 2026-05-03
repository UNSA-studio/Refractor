package unsa.rfr.com

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import unsa.rfr.com.ui.screens.*
import unsa.rfr.com.ui.theme.RefractorTheme
import unsa.rfr.com.ui.theme.ThemeColor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val themeColorName = prefs.getString("theme_color", "BLUE") ?: "BLUE"
            val themeColor = try { ThemeColor.valueOf(themeColorName) } catch (e: Exception) { ThemeColor.BLUE }
            val dynamicColor = prefs.getBoolean("dynamic_color", false)

            var showWelcome by remember { mutableStateOf(prefs.getBoolean("first_launch", true)) }

            RefractorTheme(themeColor = themeColor, dynamicColor = dynamicColor) {
                if (showWelcome) {
                    AlertDialog(
                        onDismissRequest = { showWelcome = false },
                        title = { Text("欢迎来到 Refractor!") },
                        text = {
                            Text("这是一个完全免费的直播软件，使用 WebRTC 和 Cloudflare Workers 等技术实现低延迟、高画质的端到端直播。\n无需注册、无隐藏费用。")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showWelcome = false
                                prefs.edit().putBoolean("first_launch", false).apply()
                            }) {
                                Text("OK")
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AppNavGraph()
            }
        }
    }
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
    ) {
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
            if (role == "broadcaster") {
                BroadcasterScreen(roomId, navController)
            } else {
                ViewerScreen(roomId, navController)
            }
        }
        composable("settings") { SettingsScreen(navController) }
    }
}
