package unsa.rfr.com.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import unsa.rfr.com.RfrIdGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomScreen(navController: NavController) {
    var name by remember { mutableStateOf("") }
    var maxUsers by remember { mutableStateOf("") }
    var hasPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var maxUsersError by remember { mutableStateOf(false) }
    val roomId = remember { RfrIdGenerator.generate() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("创建直播", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        // 直播名字
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = false },
            label = { Text("直播名字") },
            isError = nameError,
            modifier = Modifier.fillMaxWidth()
        )
        if (nameError) Text("名字不能为空", color = MaterialTheme.colorScheme.error)

        Spacer(modifier = Modifier.height(8.dp))

        // 最大容量
        OutlinedTextField(
            value = maxUsers,
            onValueChange = { newVal ->
                if (newVal.all { it.isDigit() }) {
                    maxUsers = newVal
                    maxUsersError = false
                }
            },
            label = { Text("最大容量上限") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = maxUsersError,
            modifier = Modifier.fillMaxWidth()
        )
        if (maxUsersError) Text("请输入有效数字", color = MaterialTheme.colorScheme.error)

        Spacer(modifier = Modifier.height(8.dp))

        // 需要密码
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = hasPassword, onCheckedChange = {
                hasPassword = it
                if (!it) password = ""
            })
            Text("需要密码")
        }

        if (hasPassword) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("直播间密码") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // RFR-ID 显示
        Text("生成 RFR-ID: $roomId", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // 开始直播按钮
        Button(
            onClick = {
                var valid = true
                if (name.isBlank()) { nameError = true; valid = false }
                if (maxUsers.isBlank() || maxUsers.toIntOrNull() == null) { maxUsersError = true; valid = false }
                if (valid) {
                    navController.navigate("room/$roomId/broadcaster")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("开始直播")
        }
    }
}
