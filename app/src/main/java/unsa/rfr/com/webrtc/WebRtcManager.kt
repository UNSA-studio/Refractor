package unsa.rfr.com.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import unsa.rfr.com.SignalingClient

class WebRtcManager(
    private val context: Context,
    private val signalingClient: SignalingClient,
    private val eglBase: EglBase,
    private val videoSink: VideoSink
) {
    companion object {
        private const val TAG = "WebRtcManager"
    }

    private var peerConnection: PeerConnection? = null
    private var videoTrack: VideoTrack? = null
    private var audioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null

    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    fun startAsBroadcaster(videoCapturer: VideoCapturer, audioSource: AudioSource) {
        this.videoCapturer = videoCapturer
        createPeerConnection()

        val videoSource = peerConnectionFactory.createVideoSource(false)
        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
        videoCapturer.startCapture(720, 1280, 30)

        videoTrack = peerConnectionFactory.createVideoTrack("video", videoSource)
        videoTrack?.addSink(videoSink)
        peerConnection?.addTrack(videoTrack)

        audioTrack = peerConnectionFactory.createAudioTrack("audio", audioSource)
        peerConnection?.addTrack(audioTrack)

        createOffer()
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
                override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
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
                override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState) {}
                override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {}
                override fun onSelectedCandidatePairChange(event: CandidatePairChangeEvent) {}
                override fun onIceCandidateError(event: IceCandidateErrorEvent) {}
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
        peerConnection?.dispose()
    }

    private inner class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}
