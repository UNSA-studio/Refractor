package unsa.rfr.com.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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

@Composable
fun RoomScreen(roomId: String, role: String, navController: NavController) {
    val context = LocalContext.current
    val signalingClient = remember { SignalingClient() }
    val eglBase = remember { EglBase.create() }
    var webRtcManager by remember { mutableStateOf<WebRtcManager?>(null) }
    val scope = rememberCoroutineScope()

    // 日志文件
    val logFile = remember {
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(downloadsDir, "log-$dateStr.txt")
    }

    fun writeLog(msg: String) {
        try {
            FileWriter(logFile, true).use { it.append("${SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())} $msg\n") }
        } catch (_: Exception) {}
        Log.d("Refractor", msg)
    }

    // 视频渲染组件
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
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                writeLog("Screen capture permission granted, starting capture service")
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
                            writeLog("Broadcaster WebRTC initialized successfully")
                        } catch (e: Exception) {
                            writeLog("ERROR starting broadcaster: ${e.message}")
                        }
                    } else {
                        writeLog("ERROR videoCapturer is null after 1s")
                    }
                }
            } catch (e: Exception) {
                writeLog("ERROR in screen capture setup: ${e.message}")
            }
        }
    }

    LaunchedEffect(roomId) {
        writeLog("RoomScreen launched: roomId=$roomId, role=$role")

        try {
            signalingClient.connect(roomId)
            writeLog("Signaling client connected")
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
                                    writeLog("WebRTC viewer started")
                                }
                                webRtcManager?.onRemoteSdp(sdpType, sdpStr)
                                writeLog("Received SDP: $sdpType")
                            } else if (json.has("candidate")) {
                                webRtcManager?.addIceCandidate(
                                    json.getString("candidate"),
                                    json.getInt("sdpMLineIndex"),
                                    json.getString("sdpMid")
                                )
                                writeLog("ICE candidate added")
                            }
                        } catch (e: Exception) {
                            writeLog("ERROR parsing signal: ${e.message}")
                        }
                    }
                    is SignalingClient.SignalMessage.UserJoined -> writeLog("User joined, count=${msg.count}")
                    is SignalingClient.SignalMessage.UserLeft -> writeLog("User left, count=${msg.count}")
                    is SignalingClient.SignalMessage.Pong -> writeLog("Heartbeat pong")
                    else -> {}
                }
            }
        }

        if (role == "broadcaster") {
            try {
                val mpm = context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
                writeLog("Launched screen capture request")
            } catch (e: Exception) {
                writeLog("ERROR launching screen capture: ${e.message}")
            }
        }
    }

    // UI
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("直播间: $roomId", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        AndroidView(
            factory = { renderer },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                writeLog("Exiting room")
                try { webRtcManager?.dispose() } catch (_: Exception) {}
                try { signalingClient.disconnect() } catch (_: Exception) {}
                navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("退出直播")
        }
    }
}
