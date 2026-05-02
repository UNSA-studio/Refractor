package unsa.rfr.com.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import unsa.rfr.com.RfrIdGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomScreen(navController: NavController) {
    var name by remember { mutableStateOf("") }
    var maxUsers by remember { mutableStateOf("") }
    var hasPassword by remember { mutableStateOf(false) }
    val roomId = remember { RfrIdGenerator.generate() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("创建直播", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        TextField(value = name, onValueChange = { name = it }, label = { Text("直播名字") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = maxUsers,
            onValueChange = { maxUsers = it },
            label = { Text("最大容量上限") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Checkbox(checked = hasPassword, onCheckedChange = { hasPassword = it })
            Text("需要密码")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("房间RFR-ID:", style = MaterialTheme.typography.titleMedium)
        Text(roomId, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                navController.navigate("room/$roomId/broadcaster")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("开始直播")
        }
    }
}
