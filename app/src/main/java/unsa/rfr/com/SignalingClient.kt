package unsa.rfr.com

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
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
        data class Chat(val message: String, val from: String) : SignalMessage()
    }

    data class RoomInfo(val online: Int, val name: String, val hasPassword: Boolean, val limit: Int)

    /** 注册房间（主播端） */
    suspend fun createRoom(roomId: String, name: String, hasPassword: Boolean, passwordHash: String, limit: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://rfr-sl.cc.cd/create")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val body = JSONObject().apply {
                    put("roomId", roomId)
                    put("name", name)
                    put("hasPassword", hasPassword)
                    put("passwordHash", passwordHash)
                    put("limit", limit)
                }.toString()
                conn.outputStream.write(body.toByteArray())
                val code = conn.responseCode
                val response = conn.inputStream.bufferedReader().readText()
                RefractorLog.write("Create room $roomId: HTTP $code $response")
                code == 200
            } catch (e: Exception) {
                RefractorLog.write("Create room failed: ${e.message}")
                false
            }
        }
    }

    /** 查询房间状态 */
    suspend fun checkRoom(roomId: String): RoomInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://rfr-sl.cc.cd/check/$roomId")
                RefractorLog.write("HTTP GET $url")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val obj = JSONObject(json)
                    if (obj.has("error")) {
                        RefractorLog.write("Check room error: $json")
                        null
                    } else {
                        RoomInfo(
                            online = obj.getInt("online"),
                            name = obj.optString("name", ""),
                            hasPassword = obj.optBoolean("hasPassword", false),
                            limit = obj.optInt("limit", 0)
                        )
                    }
                } else {
                    RefractorLog.write("Check room HTTP error: ${conn.responseCode}")
                    null
                }
            } catch (e: Exception) {
                RefractorLog.write("Check room exception: ${e.message}")
                null
            }
        }
    }

    fun connect(roomId: String) {
        currentRoomId = roomId
        RefractorLog.write("开始连接信令, room=$roomId")
        doConnect()
        startHeartbeat()
    }

    private fun doConnect() {
        val roomId = currentRoomId ?: return
        val uri = URI("$serverBaseUrl/room/$roomId")

        webSocketClient = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d(TAG, "Connected to room $roomId")
                RefractorLog.write("信令WebSocket已连接: $roomId")
                connected.set(true)
                send("{\"type\":\"join\"}")
                reconnectJob?.cancel()
            }

            override fun onMessage(message: String?) {
                message?.let {
                    if (it.length < 200) RefractorLog.write("收到信令消息: $it")
                    handleMessage(it)
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.d(TAG, "Disconnected: $reason")
                RefractorLog.write("信令断开: code=$code reason=$reason")
                connected.set(false)
                scheduleReconnect()
            }

            override fun onError(ex: Exception?) {
                Log.e(TAG, "WebSocket error", ex)
                RefractorLog.write("信令异常: ${ex?.message}")
            }
        }.apply { connect() }
    }

    private fun handleMessage(raw: String) {
        try {
            val json = org.json.JSONObject(raw)
            when (json.optString("type")) {
                "signal" -> {
                    val data = json.optString("data")
                    if (data != null) signalChannel.trySend(SignalMessage.Signal(data))
                }
                "user-joined" -> signalChannel.trySend(SignalMessage.UserJoined(json.optInt("count")))
                "user-left" -> signalChannel.trySend(SignalMessage.UserLeft(json.optInt("count")))
                "pong" -> signalChannel.trySend(SignalMessage.Pong)
                "error" -> signalChannel.trySend(SignalMessage.Error(json.optString("message")))
                "chat" -> signalChannel.trySend(SignalMessage.Chat(json.optString("data"), json.optString("from")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message", e)
            RefractorLog.write("解析消息失败: ${e.message}")
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
                        RefractorLog.write("心跳失败: ${e.message}")
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
                RefractorLog.write("尝试重连信令...")
                doConnect()
            }
        }
    }

    fun send(data: String) {
        try {
            webSocketClient?.send(data)
        } catch (e: Exception) {
            Log.e(TAG, "Send failed", e)
            RefractorLog.write("发送失败: ${e.message}")
        }
    }

    fun disconnect() {
        connected.set(false)
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocketClient?.close()
        currentRoomId = null
        RefractorLog.write("信令已断开")
    }
}
