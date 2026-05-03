package unsa.rfr.com.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import unsa.rfr.com.webrtc.WebRtcManager

data class VChatMessage(val text: String, val isMine: Boolean)

@Composable
fun ViewerScreen(roomId: String, navController: NavController) {
    val context = LocalContext.current
    val signalingClient = remember { SignalingClient() }
    val eglBase = remember { EglBase.create() }
    var webRtcManager by remember { mutableStateOf<WebRtcManager?>(null) }
    val scope = rememberCoroutineScope()
    var chatMessages by remember { mutableStateOf(listOf<VChatMessage>()) }
    var chatInput by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf("连接中…") }

    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(eglBase.eglBaseContext, null)
            setEnableHardwareScaler(true)
            setZOrderMediaOverlay(true)
        }
    }

    LaunchedEffect(roomId) {
        RefractorLog.write("ViewerScreen 进入房间 $roomId")
        signalingClient.connect(roomId)

        val timeoutJob = scope.launch {
            delay(15_000)
            if (connectionStatus == "连接中…") connectionStatus = "连接超时，请确认房间ID正确或主播已开播"
        }

        scope.launch {
            for (msg in signalingClient.signalChannel) {
                when (msg) {
                    is SignalingClient.SignalMessage.Signal -> {
                        try {
                            val json = org.json.JSONObject(msg.data)
                            if (json.has("type") && json.has("sdp")) {
                                if (webRtcManager == null) {
                                    webRtcManager = WebRtcManager(context, signalingClient, eglBase, renderer)
                                    webRtcManager?.startAsViewer()
                                    connectionStatus = "已连接，等待视频流…"
                                }
                                webRtcManager?.onRemoteSdp(json.getString("type"), json.getString("sdp"))
                                timeoutJob.cancel()
                            } else if (json.has("candidate")) {
                                webRtcManager?.addIceCandidate(json.getString("candidate"), json.getInt("sdpMLineIndex"), json.getString("sdpMid"))
                            }
                        } catch (e: Exception) {
                            RefractorLog.write("信令解析失败: ${e.message}")
                        }
                    }
                    is SignalingClient.SignalMessage.Chat -> chatMessages = chatMessages + VChatMessage(msg.message, false)
                    is SignalingClient.SignalMessage.UserJoined -> if (connectionStatus.startsWith("连接")) connectionStatus = "等待主播开始直播…"
                    else -> {}
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(connectionStatus, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
        AndroidView(factory = { renderer }, modifier = Modifier.fillMaxWidth().weight(0.7f))

        LazyColumn(Modifier.weight(0.3f).fillMaxWidth().padding(8.dp), reverseLayout = true) {
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
                    chatMessages = chatMessages + VChatMessage(chatInput, true)
                    chatInput = ""
                }
            }) { Text("发送") }
        }

        Button(onClick = {
            webRtcManager?.dispose()
            signalingClient.disconnect()
            navController.popBackStack()
        }, modifier = Modifier.fillMaxWidth()) { Text("退出直播") }
    }
}
