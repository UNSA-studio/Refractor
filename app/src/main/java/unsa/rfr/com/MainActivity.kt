package unsa.rfr.com

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import unsa.rfr.com.ui.screens.*
import unsa.rfr.com.ui.theme.RefractorTheme
import unsa.rfr.com.ui.theme.ThemeColor
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    companion object {
        // refractor-account 账户服务部署地址（workers.dev 或自定义域名）
        private const val ACCOUNT_HOST = "account-rfr.cc.cd"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 冷启动：若由 refractor://login deep link 拉起，直接处理
        handleDeepLink(intent)
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

    /** singleTask 模式下，应用已在前台时通过 deep link 再次进入会走这里 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /** 解析 refractor://login?code=xxx 并验证通过码 */
    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "refractor" || uri.host != "login") return

        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            RefractorLog.write("收到登录回调但缺少 code 参数")
            Toast.makeText(this, "通过码无效或已过期，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }
        RefractorLog.write("收到账户登录回调，开始验证通过码")
        verifyCode(code)
    }

    /** 调用账户服务验证接口换取用户信息（通过码本身不写入日志） */
    private fun verifyCode(code: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = URL("https://$ACCOUNT_HOST/api/verify?code=${URLEncoder.encode(code, StandardCharsets.UTF_8.name())}")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.requestMethod = "GET"
                    val responseCode = conn.responseCode
                    val body = if (responseCode in 200..299) conn.inputStream.bufferedReader().readText() else null
                    RefractorLog.write("账户验证接口返回: HTTP $responseCode")
                    conn.disconnect()
                    responseCode to body
                } catch (e: Exception) {
                    RefractorLog.write("账户验证请求失败: ${e.message}")
                    null
                }
            }

            val (responseCode, body) = result ?: run {
                Toast.makeText(this@MainActivity, "网络异常，请稍后重试", Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (responseCode == 200 && body != null) {
                try {
                    val json = JSONObject(body)
                    if (json.optBoolean("success")) {
                        val user = json.optJSONObject("user")
                        if (user != null) {
                            val id = user.optString("id")
                            val username = user.optString("username")
                            val email = user.optString("email")
                            // 登录状态存入设置偏好文件，供后续 UI 展示"已登录"
                            getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                                .putBoolean("account_logged_in", true)
                                .putString("account_id", id)
                                .putString("account_username", username)
                                .putString("account_email", email)
                                .apply()
                            RefractorLog.write("账户登录成功: $username")
                            Toast.makeText(this@MainActivity, "欢迎，$username", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    RefractorLog.write("账户验证响应解析失败: ${e.message}")
                }
            }
            Toast.makeText(this@MainActivity, "通过码无效或已过期，请重新登录", Toast.LENGTH_SHORT).show()
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
            "room/{roomId}/{role}?password={password}",
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("role") { type = NavType.StringType },
                navArgument("password") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            val role = backStackEntry.arguments?.getString("role") ?: "viewer"
            val password = backStackEntry.arguments?.getString("password")
            if (role == "broadcaster") {
                BroadcasterScreen(roomId, password, navController)
            } else {
                ViewerScreen(roomId, password, navController)
            }
        }
        composable("settings") { SettingsScreen(navController) }
    }
}
