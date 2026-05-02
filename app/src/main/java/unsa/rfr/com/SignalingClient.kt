package unsa.rfr.com

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

class SignalingClient(
    private val serverBaseUrl: String = "wss://rfr-sl.cc.cd"
) {
    companion object {
        private const val TAG = "SignalingClient"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    private var webSocketClient: WebSocketClient? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private val connected = AtomicBoolean(false)
    private var currentRoomId: String? = null

    val signalChannel = Channel<SignalMessage>(Channel.BUFFERED)

    sealed class SignalMessage {
        data class Signal(val data: String) : SignalMessage()
        data class UserJoined(val count: Int) : SignalMessage()
        data class UserLeft(val count: Int) : SignalMessage()
        object Pong : SignalMessage()
        data class Error(val message: String) : SignalMessage()
    }

    fun connect(roomId: String) {
        currentRoomId = roomId
        doConnect()
        startHeartbeat()
    }

    private fun doConnect() {
        val roomId = currentRoomId ?: return
        val uri = URI("$serverBaseUrl/room/$roomId")

        webSocketClient = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d(TAG, "Connected to room $roomId")
                connected.set(true)
                send("{\"type\":\"join\"}")
                reconnectJob?.cancel()
            }

            override fun onMessage(message: String?) {
                message?.let { handleMessage(it) }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.d(TAG, "Disconnected: $reason")
                connected.set(false)
                scheduleReconnect()
            }

            override fun onError(ex: Exception?) {
                Log.e(TAG, "WebSocket error", ex)
            }
        }.apply {
            connect()
        }
    }

    private fun handleMessage(raw: String) {
        try {
            val json = org.json.JSONObject(raw)
            when (json.optString("type")) {
                "signal" -> {
                    val data = json.optString("data")
                    if (data != null) {
                        signalChannel.trySend(SignalMessage.Signal(data))
                    }
                }
                "user-joined" -> {
                    val count = json.optInt("count")
                    signalChannel.trySend(SignalMessage.UserJoined(count))
                }
                "user-left" -> {
                    val count = json.optInt("count")
                    signalChannel.trySend(SignalMessage.UserLeft(count))
                }
                "pong" -> signalChannel.trySend(SignalMessage.Pong)
                "error" -> signalChannel.trySend(SignalMessage.Error(json.optString("message")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: $raw", e)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (connected.get()) {
                    try {
                        webSocketClient?.send("{\"type\":\"ping\"}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Heartbeat failed", e)
                    }
                }
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (!connected.get()) {
                Log.d(TAG, "Attempting reconnect...")
                doConnect()
            }
        }
    }

    fun send(data: String) {
        try {
            webSocketClient?.send(data)
        } catch (e: Exception) {
            Log.e(TAG, "Send failed", e)
        }
    }

    fun disconnect() {
        connected.set(false)
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocketClient?.close()
        currentRoomId = null
    }
}
