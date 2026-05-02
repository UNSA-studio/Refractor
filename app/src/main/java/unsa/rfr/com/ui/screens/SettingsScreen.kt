package unsa.rfr.com.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun SettingsScreen(navController: NavController) {
    var audioMode by remember { mutableStateOf(0) }
    var darkMode by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Text("音频接收模式：", style = MaterialTheme.typography.bodyLarge)
        RadioButton(selected = audioMode == 0, onClick = { audioMode = 0 })
        Text("仅麦克风")
        RadioButton(selected = audioMode == 1, onClick = { audioMode = 1 })
        Text("仅内部音频")
        RadioButton(selected = audioMode == 2, onClick = { audioMode = 2 })
        Text("都接收")

        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Text("深色模式")
            Switch(checked = darkMode, onCheckedChange = { darkMode = it })
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("返回")
        }
    }
}
