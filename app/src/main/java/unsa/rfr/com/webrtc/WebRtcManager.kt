package unsa.rfr.com.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import unsa.rfr.com.SignalingClient
import unsa.rfr.com.audio.AudioCaptureManager

class WebRtcManager(
    private val context: Context,
    private val signalingClient: SignalingClient,
    private val eglBase: EglBase,
    private val videoSink: VideoSink
) {
    companion object {
        private const val TAG = "WebRtcManager"
        private const val CAPTURE_WIDTH = 720
        private const val CAPTURE_HEIGHT = 1280
        private const val CAPTURE_FPS = 30
    }

    private var peerConnection: PeerConnection? = null
    private var videoTrack: VideoTrack? = null
    private var audioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var audioDeviceModule: AudioDeviceModule? = null
    private var audioCaptureManager: AudioCaptureManager? = null

    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .apply { audioDeviceModule?.let { setAudioDeviceModule(it) } }
            .createPeerConnectionFactory()
    }

    /**
     * 主播端开始直播。
     * @param audioDeviceModule 自定义音频设备模块（由 AudioCaptureManager 按音频模式创建），
     *                          传入 null 时使用默认麦克风。必须在首次访问 factory 之前设置。
     * @param audioCaptureManager 音频采集管理器（内部音频线程等），dispose 时一并停止。
     */
    fun startAsBroadcaster(
        videoCapturer: VideoCapturer,
        audioDeviceModule: AudioDeviceModule?,
        audioCaptureManager: AudioCaptureManager? = null
    ) {
        this.videoCapturer = videoCapturer
        this.audioDeviceModule = audioDeviceModule
        this.audioCaptureManager = audioCaptureManager
        createPeerConnection()

        val source = peerConnectionFactory.createVideoSource(false)
        this.videoSource = source
        val stHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        this.surfaceTextureHelper = stHelper
        videoCapturer.initialize(stHelper, context, source.capturerObserver)
        videoCapturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS)

        videoTrack = peerConnectionFactory.createVideoTrack("video", source)
        videoTrack?.addSink(videoSink)
        peerConnection?.addTrack(videoTrack)

        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        audioTrack = peerConnectionFactory.createAudioTrack("audio", audioSource)
        peerConnection?.addTrack(audioTrack)

        createOffer()
    }

    /** 暂停/恢复画面投射（音频继续）。 */
    fun setVideoEnabled(enabled: Boolean) {
        videoTrack?.setEnabled(enabled)
        if (!enabled) {
            videoCapturer?.stopCapture()
        } else {
            videoCapturer?.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS)
        }
    }

    /** 用新的屏幕捕获源替换当前视频源（重新授权后调用）。 */
    fun replaceVideoCapturer(newCapturer: VideoCapturer) {
        val old = videoCapturer
        old?.stopCapture()
        old?.dispose()
        videoCapturer = newCapturer
        val stHelper = surfaceTextureHelper
        val source = videoSource
        if (stHelper != null && source != null) {
            newCapturer.initialize(stHelper, context, source.capturerObserver)
            newCapturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS)
        }
        videoTrack?.setEnabled(true)
    }

    fun startAsViewer() {
        createPeerConnection()
    }

    private fun createPeerConnection() {
        val config = PeerConnection.RTCConfiguration(ArrayList<PeerConnection.IceServer>().apply {
            add(PeerConnection.IceServer("stun:stun.l.google.com:19302"))
            add(PeerConnection.IceServer("stun:stun.cloudflare.com:3478"))
        })

        peerConnection = peerConnectionFactory.createPeerConnection(
            config,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        signalingClient.send(
                            "{\"type\":\"signal\",\"data\":{\"candidate\":\"${it.sdp}\",\"sdpMLineIndex\":${it.sdpMLineIndex},\"sdpMid\":\"${it.sdpMid}\"}}"
                        )
                    }
                }
                override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                    // 远端视频流自动渲染
                    streams.forEach { stream ->
                        stream.videoTracks.forEach { track ->
                            track.addSink(videoSink)
                        }
                    }
                }
                override fun onRemoveTrack(receiver: RtpReceiver) {}
                override fun onDataChannel(channel: DataChannel) {}
                override fun onRenegotiationNeeded() {}
                override fun onSignalingChange(state: PeerConnection.SignalingState) {
                    Log.d(TAG, "Signaling state: $state")
                }
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    Log.d(TAG, "ICE state: $state")
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    Log.d(TAG, "ICE receiving change: $receiving")
                }
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                    Log.d(TAG, "ICE gathering state: $state")
                }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
            }
        )
    }

    private fun createOffer() {
        val pc = peerConnection ?: return
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                sessionDescription?.let {
                    pc.setLocalDescription(SdpObserverAdapter(), it)
                    signalingClient.send(
                        "{\"type\":\"signal\",\"data\":{\"type\":\"offer\",\"sdp\":\"${it.description.replace("\"", "\\\"")}\"}}"
                    )
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) { Log.e(TAG, "Create offer failed: $p0") }
            override fun onSetFailure(p0: String?) { Log.e(TAG, "Set offer failed: $p0") }
        }, MediaConstraints())
    }

    fun onRemoteSdp(type: String, sdpStr: String) {
        val pc = peerConnection ?: run {
            startAsViewer()
            return onRemoteSdp(type, sdpStr)
        }

        val sdpType = if (type == "offer") SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER
        val sdp = SessionDescription(sdpType, sdpStr)
        pc.setRemoteDescription(SdpObserverAdapter(), sdp)

        if (type == "offer") {
            pc.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                    sessionDescription?.let {
                        pc.setLocalDescription(SdpObserverAdapter(), it)
                        signalingClient.send(
                            "{\"type\":\"signal\",\"data\":{\"type\":\"answer\",\"sdp\":\"${it.description.replace("\"", "\\\"")}\"}}"
                        )
                    }
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(p0: String?) { Log.e(TAG, "Create answer failed: $p0") }
                override fun onSetFailure(p0: String?) { Log.e(TAG, "Set answer failed: $p0") }
            }, MediaConstraints())
        }
    }

    fun addIceCandidate(sdp: String, sdpMLineIndex: Int, sdpMid: String) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        peerConnection?.addIceCandidate(candidate)
    }

    fun dispose() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoTrack?.dispose()
        audioTrack?.dispose()
        audioDeviceModule?.release()
        audioCaptureManager?.stop()
        peerConnection?.dispose()
    }

    private inner class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}
