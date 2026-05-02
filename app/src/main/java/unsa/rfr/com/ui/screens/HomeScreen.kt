package unsa.rfr.com.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun HomeScreen(navController: NavController) {
    var roomId by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextField(
            value = roomId,
            onValueChange = { roomId = it },
            label = { Text("输入 RFR-ID 加入直播间") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (roomId.isNotBlank()) navController.navigate("room/$roomId/viewer")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("进入直播间")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { navController.navigate("create") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("创建直播")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { navController.navigate("settings") }) {
            Text("设置")
        }
    }
}
