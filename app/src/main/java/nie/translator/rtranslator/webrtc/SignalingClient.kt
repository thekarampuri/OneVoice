package nie.translator.rtranslator.webrtc

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import nie.translator.rtranslator.webrtc.model.*
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for WebRTC signaling.
 * Adapted from BhashaSetu (com.example.voicetranslate.webrtc.SignalingClient).
 * Package: nie.translator.rtranslator.webrtc
 *
 * Responsibilities:
 * - Connect to signaling server via WebSocket
 * - Send signaling messages (offer, answer, ICE candidates)
 * - Receive signaling messages from peer
 * - Handle connection lifecycle
 *
 * No WebRTC PeerConnection logic — only signaling.
 */
class SignalingClient(
    private val serverUrl: String,
    private val listener: SignalingListener
) {
    private val tag = "SignalingClient"
    private val gson = Gson()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------


    /**
     * Connect to signaling server.
     * @param callId  Room identifier (shared between both peers).
     * @param userId  Unique user ID (UUID).
     */
    fun connect(callId: String, userId: String) {
        val wsUrl = buildWebSocketUrl(serverUrl, callId, userId)
        Log.d(tag, "Connecting to: $wsUrl")

        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "✅ WebSocket connected  callId=$callId  userId=${userId.take(8)}…")
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(tag, "📨 Received: ${if (text.length > 500) text.take(500) + "…" else text}")
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val error = "Connection failed: ${t.message}"
                Log.e(tag, "❌ $error")
                listener.onError(error)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "🔌 Connection closing: $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "❌ Connection closed: $reason")
                listener.onDisconnected()
            }
        })
    }

    /** Send SDP offer. */
    fun sendOffer(callId: String, sdp: String) {
        sendMessage(OfferMessage(callId = callId, sdp = sdp))
    }

    /** Send SDP answer. */
    fun sendAnswer(callId: String, sdp: String) {
        sendMessage(AnswerMessage(callId = callId, sdp = sdp))
    }

    /** Send ICE candidate. */
    fun sendIceCandidate(callId: String, candidate: nie.translator.rtranslator.webrtc.model.IceCandidate) {
        sendMessage(IceCandidateMessage(callId = callId, candidate = candidate))
    }

    /** Disconnect and release resources. */
    fun disconnect() {
        Log.d(tag, "Disconnecting…")
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }

    /** Full cleanup. */
    fun close() {
        disconnect()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun sendMessage(message: SignalingMessage) {
        val json = gson.toJson(message)
        Log.d(tag, "📤 Sending: ${message.type}")
        val ok = webSocket?.send(json) ?: false
        if (!ok) {
            Log.e(tag, "Failed to send: ${message.type}")
            listener.onError("Failed to send ${message.type}")
        }
    }

    private fun handleMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return

            when (type) {
                "peer-joined" -> {
                    val peerId = json.get("peerId")?.asString
                    Log.d(tag, "👤 Peer joined: $peerId")
                    listener.onPeerJoined(peerId)
                }

                "existing-peer" -> {
                    val peerId = json.get("peerId")?.asString
                    Log.d(tag, "👤 Existing peer: $peerId")
                    listener.onExistingPeer(peerId)
                }

                "offer" -> {
                    val sdp = json.get("sdp")?.asString ?: return
                    Log.d(tag, "📞 Offer received")
                    listener.onOfferReceived(sdp)
                }

                "answer" -> {
                    val sdp = json.get("sdp")?.asString ?: return
                    Log.d(tag, "✅ Answer received")
                    listener.onAnswerReceived(sdp)
                }

                "ice-candidate" -> {
                    val c = json.getAsJsonObject("candidate")
                    val candidate = IceCandidate(
                        candidate    = c.get("candidate").asString,
                        sdpMid       = c.get("sdpMid").asString,
                        sdpMLineIndex = c.get("sdpMLineIndex").asInt
                    )
                    Log.d(tag, "🧊 ICE candidate received")
                    listener.onIceCandidateReceived(candidate)
                }

                "peer-left" -> {
                    val peerId = json.get("peerId")?.asString ?: return
                    Log.d(tag, "👋 Peer left: $peerId")
                    listener.onPeerLeft(peerId)
                }

                "error" -> {
                    val msg = json.get("message")?.asString ?: "Unknown error"
                    Log.e(tag, "⚠️ Server error: $msg")
                    listener.onError(msg)
                }

                else -> Log.w(tag, "Unknown message type: $type")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing message: ${e.message}")
            listener.onError("Failed to parse message: ${e.message}")
        }
    }

    /**
     * Build WebSocket URL:  ws://<host:port>/ws/<callId>/<userId>
     */
    private fun buildWebSocketUrl(serverUrl: String, callId: String, userId: String): String {
        val clean = serverUrl
            .replace(Regex("^(http://|https://|ws://|wss://|/+)"), "")
            .trimEnd('/')
        return "ws://$clean/ws/$callId/$userId"
    }
}

/**
 * Listener interface for signaling events.
 */
interface SignalingListener {
    fun onConnected()
    fun onPeerJoined(peerId: String?)
    fun onExistingPeer(peerId: String?)
    fun onOfferReceived(sdp: String)
    fun onAnswerReceived(sdp: String)
    fun onIceCandidateReceived(candidate: nie.translator.rtranslator.webrtc.model.IceCandidate)
    fun onPeerLeft(peerId: String)
    fun onDisconnected()
    fun onError(error: String)
}
