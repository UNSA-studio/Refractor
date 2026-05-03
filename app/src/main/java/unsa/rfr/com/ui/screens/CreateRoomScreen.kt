package unsa.rfr.com.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import unsa.rfr.com.RfrIdGenerator
import unsa.rfr.com.SignalingClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomScreen(navController: NavController) {
    var name by remember { mutableStateOf("") }
    var maxUsers by remember { mutableStateOf("") }
    var hasPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var maxUsersError by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    val roomId = remember { RfrIdGenerator.generate() }
    val scope = rememberCoroutineScope()
    val signalingClient = remember { SignalingClient() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("创建直播", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name, onValueChange = { name = it; nameError = false },
            label = { Text("直播名字") }, isError = nameError,
            modifier = Modifier.fillMaxWidth()
        )
        if (nameError) Text("名字不能为空", color = MaterialTheme.colorScheme.error)

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = maxUsers,
            onValueChange = { newVal -> if (newVal.all { it.isDigit() }) { maxUsers = newVal; maxUsersError = false } },
            label = { Text("最大容量上限") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = maxUsersError, modifier = Modifier.fillMaxWidth()
        )
        if (maxUsersError) Text("请输入有效数字", color = MaterialTheme.colorScheme.error)

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = hasPassword, onCheckedChange = { hasPassword = it; if (!it) password = "" })
            Text("需要密码")
        }
        if (hasPassword) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("直播间密码") }, modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("生成 RFR-ID: $roomId", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                var valid = true
                if (name.isBlank()) { nameError = true; valid = false }
                val limit = maxUsers.toIntOrNull()
                if (limit == null || limit <= 0) { maxUsersError = true; valid = false }
                if (valid) {
                    isCreating = true
                    val passwordHash = if (hasPassword) java.util.Base64.getEncoder().encodeToString(password.toByteArray()) else ""
                    scope.launch {
                        val success = signalingClient.createRoom(roomId, name, hasPassword, passwordHash, limit!!)
                        if (success) {
                            navController.navigate("room/$roomId/broadcaster")
                        } else {
                            // 可显示提示
                        }
                        isCreating = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isCreating
        ) {
            Text(if (isCreating) "创建中…" else "开始直播")
        }
    }
}
