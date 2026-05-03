package unsa.rfr.com.ui.screens

import android.content.Intent
import android.os.Environment
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
fun BroadcasterScreen(roomId: String, navController: NavController) {
    val context = LocalContext.current
    val signalingClient = remember { SignalingClient() }
    val eglBase = remember { EglBase.create() }
    var webRtcManager by remember { mutableStateOf<WebRtcManager?>(null) }
    val scope = rememberCoroutineScope()
    var chatMessages by remember { mutableStateOf(listOf<BChatMessage>()) }
    var chatInput by remember { mutableStateOf("") }
    var viewerCount by remember { mutableIntStateOf(0) }

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
                delay(1000)
                val capturer = ScreenCaptureService.videoCapturer
                if (capturer != null) {
                    val audioManager = AudioCaptureManager(context)
                    val audioSource = audioManager.createAudioSource(AudioCaptureManager.AudioMode.MIC_ONLY)
                    val manager = WebRtcManager(context, signalingClient, eglBase, renderer)
                    manager.startAsBroadcaster(capturer, audioSource!!)
                    webRtcManager = manager
                    RefractorLog.write("直播已开始")
                } else {
                    RefractorLog.write("视频采集器为空")
                }
            }
        }
    }

    LaunchedEffect(roomId) {
        RefractorLog.write("BroadcasterScreen 进入房间 $roomId")
        signalingClient.connect(roomId)

        scope.launch {
            for (msg in signalingClient.signalChannel) {
                when (msg) {
                    is SignalingClient.SignalMessage.Chat -> chatMessages = chatMessages + BChatMessage(msg.message, false)
                    is SignalingClient.SignalMessage.UserJoined -> viewerCount = msg.count
                    is SignalingClient.SignalMessage.UserLeft -> viewerCount = msg.count
                    else -> {}
                }
            }
        }

        val mpm = context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
        screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
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

            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = {}) { Text("音频设置") }
                Button(onClick = {}) { Text("停止投射") }
                Button(onClick = {
                    webRtcManager?.dispose()
                    signalingClient.disconnect()
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
                        signalingClient.send("{\"type\":\"chat\",\"data\":\"$chatInput\"}")
                        chatMessages = chatMessages + BChatMessage(chatInput, true)
                        chatInput = ""
                    }
                }) { Text("发送") }
            }
        }
    }
}
