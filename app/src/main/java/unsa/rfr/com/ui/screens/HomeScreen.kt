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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import unsa.rfr.com.RefractorLog
import unsa.rfr.com.SignalingClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    var roomId by remember { mutableStateOf("") }
    var idCheckResult by remember { mutableStateOf<String?>(null) }
    var needPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var roomPasswordError by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val signalingClient = remember { SignalingClient() }

    val isValidFormat = roomId.matches(Regex("^[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-user\\d{3}$"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Refractor", style = MaterialTheme.typography.headlineLarge) },
                actions = {
                    IconButton(onClick = {
                        scope.launch { rotation.animateTo(rotation.value + 360f) }
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
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = roomId,
                onValueChange = { roomId = it; idCheckResult = null; needPassword = false; password = "" },
                label = { Text("输入 RFR-ID 加入直播间") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )

            if (idCheckResult == "invalid") Text("ID 格式错误", color = MaterialTheme.colorScheme.error)
            if (idCheckResult == "offline") Text("该直播间不存在或已结束", color = MaterialTheme.colorScheme.error)

            if (needPassword) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; roomPasswordError = false },
                    label = { Text("该直播间需要密码") },
                    isError = roomPasswordError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (roomPasswordError) Text("请输入密码", color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (!isValidFormat) {
                        idCheckResult = "invalid"
                        return@Button
                    }
                    if (needPassword && password.isBlank()) {
                        roomPasswordError = true
                        return@Button
                    }
                    RefractorLog.write("用户点击进入直播间, ID=$roomId")
                    idCheckResult = "checking"
                    scope.launch {
                        val roomInfo = signalingClient.checkRoom(roomId)
                        if (roomInfo != null) {
                            if (roomInfo.hasPassword && !needPassword) {
                                // 该房间需要密码，先让用户输入
                                needPassword = true
                                idCheckResult = null
                            } else {
                                RefractorLog.write("房间在线: ${roomInfo.name} 人数:${roomInfo.online}/${roomInfo.limit}")
                                val encodedPwd = URLEncoder.encode(password, StandardCharsets.UTF_8.name())
                                navController.navigate("room/$roomId/viewer?password=$encodedPwd")
                            }
                        } else {
                            RefractorLog.write("房间不存在或查询失败")
                            idCheckResult = "offline"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = idCheckResult != "checking"
            ) {
                Text(if (idCheckResult == "checking") "检查中…" else if (needPassword) "验证并进入" else "进入直播间")
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
