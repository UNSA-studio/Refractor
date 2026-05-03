package unsa.rfr.com.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import unsa.rfr.com.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    var roomId by remember { mutableStateOf("") }
    var idCheckResult by remember { mutableStateOf<String?>(null) } // null=未校验, "valid"/"invalid"
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Refractor") },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            rotation.animateTo(rotation.value + 360f)
                        }
                        navController.navigate("settings")
                    }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            modifier = Modifier.rotate(rotation.value)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = roomId,
                onValueChange = {
                    roomId = it
                    idCheckResult = null // 输入变化后重置检查
                },
                label = { Text("输入 RFR-ID 加入直播间") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                trailingIcon = {
                    // 简单校验：格式大致符合 xxxx-xxxx-xxxx-userxxx
                    if (roomId.isNotBlank()) {
                        val isValid = roomId.matches(Regex("^[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-user\\d{3}$"))
                        idCheckResult = if (isValid) "valid" else "invalid"
                    }
                }
            )

            if (idCheckResult == "invalid") {
                Text("ID 格式错误", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 如果 ID 有效，显示直播信息占位卡片（未来可查询信令服务器获取实际详情）
            if (idCheckResult == "valid") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("直播间预览", style = MaterialTheme.typography.titleMedium)
                        Text("ID: $roomId")
                        Text("状态：正在直播 / 等待中")
                        // 宣传图（默认使用图标）
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { navController.navigate("room/$roomId/viewer") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("进入直播间")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { navController.navigate("create") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("创建直播")
            }
        }
    }
}
