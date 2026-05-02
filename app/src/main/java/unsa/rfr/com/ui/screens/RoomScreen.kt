package unsa.rfr.com.ui.screens

import android.app.Activity
import android.content.Intent
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
import org.webrtc.VideoTrack
import unsa.rfr.com.SignalingClient
import unsa.rfr.com.audio.AudioCaptureManager
import unsa.rfr.com.capture.ScreenCaptureService
import unsa.rfr.com.webrtc.WebRtcManager

@Composable
fun RoomScreen(roomId: String, role: String, navController: NavController) {
    val context = LocalContext.current
    val signalingClient = remember { SignalingClient() }
    val eglBase = remember { EglBase.create() }
    var webRtcManager by remember { mutableStateOf<WebRtcManager?>(null) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }

    // 创建 SurfaceViewRenderer，它就是 VideoSink
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(eglBase.eglBaseContext, null)
            setEnableHardwareScaler(true)
            setZOrderMediaOverlay(true)
        }
    }

    val scope = rememberCoroutineScope()

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            context.startForegroundService(intent)

            scope.launch {
                delay(1000)
                val capturer = ScreenCaptureService.videoCapturer
                if (capturer != null && webRtcManager == null) {
                    val audioManager = AudioCaptureManager(context)
                    val audioSource = audioManager.createAudioSource(AudioCaptureManager.AudioMode.MIC_ONLY)
                    val manager = WebRtcManager(context, signalingClient, eglBase, renderer)
                    manager.startAsBroadcaster(capturer, audioSource!!)
                    webRtcManager = manager
                }
            }
        }
    }

    LaunchedEffect(roomId) {
        signalingClient.connect(roomId)

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
                            Log.e("RoomScreen", "信令解析失败", e)
                        }
                    }
                    else -> {}
                }
            }
        }

        if (role == "broadcaster") {
            val mpm = context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
            screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
        }
    }

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
