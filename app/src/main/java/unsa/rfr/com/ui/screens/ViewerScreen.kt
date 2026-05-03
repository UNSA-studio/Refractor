package unsa.rfr.com.ui.screens

import android.os.Environment
import android.util.Log
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
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import unsa.rfr.com.SignalingClient
import unsa.rfr.com.webrtc.WebRtcManager
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

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

    val logFile = remember {
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "log-$dateStr.txt")
    }
    fun writeLog(msg: String) {
        try { FileWriter(logFile, true).use { it.append("${SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())} $msg\n") } } catch (_: Exception) {}
    }

    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(eglBase.eglBaseContext, null)
            setEnableHardwareScaler(true)
            setZOrderMediaOverlay(true)
        }
    }

    LaunchedEffect(roomId) {
        writeLog("ViewerScreen launched, room=$roomId")
        try {
            signalingClient.connect(roomId)
        } catch (e: Exception) {
            writeLog("ERROR signaling connect: ${e.message}")
            return@LaunchedEffect
        }

        scope.launch {
            for (msg in signalingClient.signalChannel) {
                when (msg) {
                    is SignalingClient.SignalMessage.Signal -> {
                        try {
                            val json = org.json.JSONObject(msg.data)
                            if (json.has("type") && json.has("sdp")) {
                                val sdpType = json.getString("type")
                                val sdpStr = json.getString("sdp")
                                if (webRtcManager == null) {
                                    webRtcManager = WebRtcManager(context, signalingClient, eglBase, renderer)
                                    webRtcManager?.startAsViewer()
                                }
                                webRtcManager?.onRemoteSdp(sdpType, sdpStr)
                            } else if (json.has("candidate")) {
                                webRtcManager?.addIceCandidate(
                                    json.getString("candidate"),
                                    json.getInt("sdpMLineIndex"),
                                    json.getString("sdpMid")
                                )
                            }
                        } catch (e: Exception) {
                            writeLog("ERROR signal parse: ${e.message}")
                        }
                    }
                    is SignalingClient.SignalMessage.Chat -> {
                        chatMessages = chatMessages + VChatMessage(msg.message, false)
                    }
                    else -> {}
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 全屏视频区
        AndroidView(
            factory = { renderer },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f)
        )

        // 聊天区
        LazyColumn(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxWidth()
                .padding(8.dp),
            reverseLayout = true
        ) {
            items(chatMessages.reversed()) { msg ->
                Text(
                    text = msg.text,
                    color = if (msg.isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 消息输入
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            OutlinedTextField(
                value = chatInput,
                onValueChange = { chatInput = it },
                modifier = Modifier.weight(1f),
                label = { Text("消息") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (chatInput.isNotBlank()) {
                    signalingClient.send("{\"type\":\"chat\",\"data\":\"$chatInput\"}")
                    chatMessages = chatMessages + VChatMessage(chatInput, true)
                    chatInput = ""
                }
            }) { Text("发送") }
        }

        // 退出
        Button(
            onClick = {
                webRtcManager?.dispose()
                signalingClient.disconnect()
                navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("退出直播")
        }
    }
}
