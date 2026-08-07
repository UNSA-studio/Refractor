package unsa.rfr.com.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import unsa.rfr.com.RefractorLog
import unsa.rfr.com.SignalingClient
import unsa.rfr.com.audio.AudioCaptureManager
import unsa.rfr.com.capture.ScreenCaptureService
import unsa.rfr.com.webrtc.WebRtcManager

data class BChatMessage(val text: String, val isMine: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcasterScreen(roomId: String, password: String?, navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val signalingClient = remember { SignalingClient() }
    val eglBase = remember { EglBase.create() }
    var webRtcManager by remember { mutableStateOf<WebRtcManager?>(null) }
    val scope = rememberCoroutineScope()
    var chatMessages by remember { mutableStateOf(listOf<BChatMessage>()) }
    var chatInput by remember { mutableStateOf("") }
    var viewerCount by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var projectionActive by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    // 音频模式：0=仅麦克风 1=仅内部音频 2=都接收（与设置页一致）
    var audioMode by remember { mutableIntStateOf(prefs.getInt("audio_mode", 0)) }

    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(eglBase.eglBaseContext, null)
            setEnableHardwareScaler(true)
            setZOrderMediaOverlay(true)
        }
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            RefractorLog.write("屏幕录制权限已获取")
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            context.startForegroundService(intent)
            scope.launch {
                delay(1500) // 多等一会儿，确保 Service 准备好
                val capturer = ScreenCaptureService.videoCapturer
                if (capturer == null) {
                    RefractorLog.write("ERROR: videoCapturer 仍为空")
                    errorMessage = "屏幕捕获初始化失败，请重试"
                    return@launch
                }
                try {
                    val audioManager = AudioCaptureManager(context)
                    val mode = AudioCaptureManager.AudioMode.entries[audioMode.coerceIn(0, 2)]
                    val adm = audioManager.createAudioDeviceModule(mode, ScreenCaptureService.mediaProjection)
                    val manager = WebRtcManager(context, signalingClient, eglBase, renderer)
                    manager.startAsBroadcaster(capturer, adm, audioManager)
                    webRtcManager = manager
                    projectionActive = true
                    RefractorLog.write("直播已开始, 音频模式=$mode")
                } catch (e: Exception) {
                    RefractorLog.write("启动直播失败: ${e.stackTraceToString()}")
                    errorMessage = "启动直播失败: ${e.message}"
                }
            }
        } else {
            RefractorLog.write("屏幕录制权限被拒绝")
        }
    }

    LaunchedEffect(roomId) {
        RefractorLog.write("BroadcasterScreen 进入房间 $roomId")
        try {
            signalingClient.connect(roomId, password?.takeIf { it.isNotBlank() })
        } catch (e: Exception) {
            RefractorLog.write("信令连接失败: ${e.stackTraceToString()}")
            errorMessage = "信令连接失败"
            return@LaunchedEffect
        }

        scope.launch {
            for (msg in signalingClient.signalChannel) {
                when (msg) {
                    is SignalingClient.SignalMessage.Chat -> {
                        val mine = msg.from == signalingClient.clientId
                        chatMessages = chatMessages + BChatMessage(if (mine) "我: ${msg.message}" else "${msg.from}: ${msg.message}", mine)
                    }
                    is SignalingClient.SignalMessage.UserJoined -> viewerCount = msg.count
                    is SignalingClient.SignalMessage.UserLeft -> viewerCount = msg.count
                    is SignalingClient.SignalMessage.Error -> errorMessage = msg.message
                    else -> {}
                }
            }
        }

        val mpm = context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
        screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    // 音频设置弹窗：选择后保存，下次开播生效
    if (showAudioDialog) {
        var tempMode by remember { mutableIntStateOf(audioMode) }
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            title = { Text("音频设置") },
            text = {
                Column {
                    listOf("仅麦克风", "仅内部音频", "都接收").forEachIndexed { index, label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = tempMode == index, onClick = { tempMode = index })
                            Text(label)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("更改后将在下次开播时生效", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    audioMode = tempMode
                    prefs.edit().putInt("audio_mode", tempMode).apply()
                    RefractorLog.write("音频模式已修改为 $tempMode")
                    showAudioDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showAudioDialog = false }) { Text("取消") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("直播: $roomId") },
                actions = { Text("${viewerCount}人观看", modifier = Modifier.padding(end = 8.dp)) }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { renderer }, modifier = Modifier.fillMaxWidth().weight(0.3f))

            if (errorMessage != null) {
                Text(
                    errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { showAudioDialog = true }) { Text("音频设置") }
                Button(onClick = {
                    val manager = webRtcManager ?: return@Button
                    projectionActive = !projectionActive
                    manager.setVideoEnabled(projectionActive)
                    RefractorLog.write(if (projectionActive) "已恢复投射" else "已停止投射")
                }) { Text(if (projectionActive) "停止投射" else "恢复投射") }
                Button(onClick = {
                    webRtcManager?.dispose()
                    webRtcManager = null
                    signalingClient.disconnect()
                    context.stopService(Intent(context, ScreenCaptureService::class.java))
                    navController.popBackStack()
                }) { Text("结束直播") }
            }

            LazyColumn(Modifier.weight(0.5f).fillMaxWidth().padding(8.dp), reverseLayout = true) {
                items(chatMessages.reversed()) { msg ->
                    Text(text = msg.text, color = if (msg.isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }

            Row(Modifier.fillMaxWidth().padding(8.dp)) {
                OutlinedTextField(value = chatInput, onValueChange = { chatInput = it }, modifier = Modifier.weight(1f), label = { Text("消息") })
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (chatInput.isNotBlank()) {
                        signalingClient.sendChat(chatInput)
                        chatMessages = chatMessages + BChatMessage("我: $chatInput", true)
                        chatInput = ""
                    }
                }) { Text("发送") }
            }
        }
    }
}
