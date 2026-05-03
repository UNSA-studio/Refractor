package unsa.rfr.com.ui.screens

import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import unsa.rfr.com.SignalingClient
import unsa.rfr.com.audio.AudioCaptureManager
import unsa.rfr.com.capture.ScreenCaptureService
import unsa.rfr.com.webrtc.WebRtcManager
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(val text: String, val isMine: Boolean)

@Composable
fun RoomScreen(roomId: String, role: String, navController: NavController) {
    val context = LocalContext.current
    val signalingClient = remember { SignalingClient() }
    val eglBase = remember { EglBase.create() }
    var webRtcManager by remember { mutableStateOf<WebRtcManager?>(null) }
    val scope = rememberCoroutineScope()
    var chatMessages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var chatInput by remember { mutableStateOf("") }

    val logFile = remember {
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "log-$dateStr.txt")
    }

    fun writeLog(msg: String) {
        try { FileWriter(logFile, true).use { it.append("${SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())} $msg\n") } } catch (_: Exception) {}
        Log.d("Refractor", msg)
    }

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
            writeLog("Screen capture permission granted")
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            context.startForegroundService(intent)
            scope.launch {
                delay(1000)
                val capturer = ScreenCaptureService.videoCapturer
                if (capturer != null) {
                    try {
                        val audioManager = AudioCaptureManager(context)
                        val audioSource = audioManager.createAudioSource(AudioCaptureManager.AudioMode.MIC_ONLY)
                        val manager = WebRtcManager(context, signalingClient, eglBase, renderer)
                        manager.startAsBroadcaster(capturer, audioSource!!)
                        webRtcManager = manager
                        writeLog("Broadcaster started")
                    } catch (e: Exception) {
                        writeLog("ERROR broadcaster: ${e.message}")
                    }
                } else {
                    writeLog("ERROR videoCapturer null")
                }
            }
        }
    }

    LaunchedEffect(roomId) {
        writeLog("RoomScreen launched, room=$roomId, role=$role")
        try {
            signalingClient.connect(roomId)
        } catch (e: Exception) {
            writeLog("ERROR connecting signaling: ${e.message}")
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
                            writeLog("ERROR parsing signal: ${e.message}")
                        }
                    }
                    is SignalingClient.SignalMessage.Chat -> {
                        chatMessages = chatMessages + ChatMessage(msg.message, false)
                    }
                    is SignalingClient.SignalMessage.UserJoined -> writeLog("User joined (${msg.count})")
                    is SignalingClient.SignalMessage.UserLeft -> writeLog("User left (${msg.count})")
                    else -> {}
                }
            }
        }

        if (role == "broadcaster") {
            try {
                val mpm = context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
            } catch (e: Exception) {
                writeLog("ERROR launching screen capture: ${e.message}")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 视频区域
        AndroidView(
            factory = { renderer },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
        )

        // 聊天区域
        LazyColumn(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            reverseLayout = true
        ) {
            items(chatMessages.reversed()) { msg ->
                Text(
                    text = msg.text,
                    color = if (msg.isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        // 输入栏
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
                    chatMessages = chatMessages + ChatMessage(chatInput, true)
                    chatInput = ""
                }
            }) {
                Text("发送")
            }
        }

        // 退出按钮
        Button(
            onClick = {
                writeLog("Exiting room")
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
