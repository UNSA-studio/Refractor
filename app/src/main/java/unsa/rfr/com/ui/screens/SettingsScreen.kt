package unsa.rfr.com.ui.screens

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import unsa.rfr.com.RefractorLog
import unsa.rfr.com.ui.theme.ThemeColor
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var audioMode by remember { mutableIntStateOf(prefs.getInt("audio_mode", 0)) }
    var selectedThemeName by remember { mutableStateOf(prefs.getString("theme_color", "BLUE") ?: "BLUE") }
    var themeExpanded by remember { mutableStateOf(false) }
    val themeOptions = ThemeColor.entries.map { it.name }

    fun saveSettings() {
        prefs.edit().putInt("audio_mode", audioMode).putString("theme_color", selectedThemeName).apply()
    }

    BackHandler {
        saveSettings()
        navController.popBackStack()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("设置", style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

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

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { themeExpanded = !themeExpanded }) {
            Text("颜色主题", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                if (themeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "展开/折叠", modifier = Modifier.size(24.dp)
            )
        }
        AnimatedVisibility(visible = themeExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                themeOptions.forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedThemeName = name }) {
                        RadioButton(selected = selectedThemeName == name, onClick = { selectedThemeName = name })
                        Text(name)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 导出日志：直接存到 Download/Refractor/log-xxxx.txt
        Button(
            onClick = {
                try {
                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Refractor")
                    if (!targetDir.exists()) targetDir.mkdirs()
                    val outFile = File(targetDir, "log-$dateStr.txt")

                    // 写入应用内部日志内容
                    FileOutputStream(outFile).use { fos ->
                        fos.write(RefractorLog.getLogContent().toByteArray())
                    }

                    Toast.makeText(context, "日志已保存到 ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("导出日志") }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                saveSettings()
                navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("返回") }
    }
}
