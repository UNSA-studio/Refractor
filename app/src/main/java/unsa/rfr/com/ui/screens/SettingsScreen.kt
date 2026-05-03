package unsa.rfr.com.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel) {
    var audioMode by remember { mutableIntStateOf(0) }
    var darkMode by remember { mutableStateOf(false) }
    var dynamicColor by remember { mutableStateOf(true) }

    // 齿轮旋转动画
    val rotation = remember { Animatable(0f) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "设置",
                modifier = Modifier
                    .size(32.dp)
                    .rotate(rotation.value)
                    .clickable {
                        // 点击齿轮转一圈
                        viewModel.rotateGear()
                    },
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("设置", style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 音频模式
        Text("音频接收模式", style = MaterialTheme.typography.titleMedium)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = audioMode == 0, onClick = { audioMode = 0 })
                Text("仅麦克风")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = audioMode == 1, onClick = { audioMode = 1 })
                Text("仅内部音频")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = audioMode == 2, onClick = { audioMode = 2 })
                Text("都接收")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 深色模式
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("深色模式", modifier = Modifier.weight(1f))
            Switch(checked = darkMode, onCheckedChange = { darkMode = it })
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 动态取色
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("动态取色", modifier = Modifier.weight(1f))
            Switch(checked = dynamicColor, onCheckedChange = { dynamicColor = it })
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("返回")
        }
    }
}
