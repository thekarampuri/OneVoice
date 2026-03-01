package com.example.voicetranslate.webrtc

import android.util.Log
import com.example.voicetranslate.data.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for WebRTC signaling
 * 
 * Responsibilities:
 * - Connect to signaling server via WebSocket
 * - Send signaling messages (offer, answer, ICE candidates)
 * - Receive signaling messages from peer
 * - Handle connection lifecycle
 * 
 * No WebRTC PeerConnection logic - only signaling.
 */
class SignalingClient(
    private val serverUrl: String,
    private val listener: SignalingListener
) {
    private val tag = "SignalingClient"
    private val gson = Gson()
    
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    
    /**
     * Connection state
     */
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
    
    /**
     * Listener interface for signaling events
     */
    interface SignalingListener {
        fun onConnected()
        fun onPeerJoined(peerId: String?)
        fun onOfferReceived(sdp: String)
        fun onAnswerReceived(sdp: String)
        fun onIceCandidateReceived(candidate: IceCandidate)
        fun onPeerLeft(peerId: String)
        fun onDisconnected()
        fun onError(error: String)
    }
    
    /**
     * Connect to signaling server
     * 
     * @param callId Room identifier
     * @param userId User's unique identifier (UUID)
     */
    fun connect(callId: String, userId: String) {
        if (_connectionState.value is ConnectionState.Connected) {
            Log.w(tag, "Already connected")
            return
        }
        
        _connectionState.value = ConnectionState.Connecting
        
        // Build WebSocket URL: ws://server/ws/{call_id}/{user_id}
        val wsUrl = buildWebSocketUrl(serverUrl, callId, userId)
        Log.d(tag, "Connecting to: $wsUrl")
        
        val request = Request.Builder()
            .url(wsUrl)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "✅ WebSocket connected to: $wsUrl")
                Log.d(tag, "   Call ID: $callId")
                Log.d(tag, "   User ID: ${userId.take(8)}...")
                _connectionState.value = ConnectionState.Connected
                listener.onConnected()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                // Log full message for debugging (limited to 500 chars)
                Log.d(tag, "📨 Received: ${if (text.length > 500) text.take(500) + "..." else text}")
                handleMessage(text)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val error = "Connection failed: ${t.message}"
                Log.e(tag, "❌ $error")
                _connectionState.value = ConnectionState.Error(error)
                listener.onError(error)
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "🔌 Connection closing: $reason")
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "❌ Connection closed: $reason")
                _connectionState.value = ConnectionState.Disconnected
                listener.onDisconnected()
            }
        })
    }
    
    /**
     * Send SDP offer
     */
    fun sendOffer(callId: String, sdp: String) {
        val message = OfferMessage(callId = callId, sdp = sdp)
        sendMessage(message)
    }
    
    /**
     * Send SDP answer
     */
    fun sendAnswer(callId: String, sdp: String) {
        val message = AnswerMessage(callId = callId, sdp = sdp)
        sendMessage(message)
    }
    
    /**
     * Send ICE candidate
     */
    fun sendIceCandidate(callId: String, candidate: IceCandidate) {
        val message = IceCandidateMessage(callId = callId, candidate = candidate)
        sendMessage(message)
    }
    
    /**
     * Disconnect from signaling server
     */
    fun disconnect() {
        Log.d(tag, "Disconnecting...")
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        disconnect()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }
    
    /**
     * Send message to server
     */
    private fun sendMessage(message: SignalingMessage) {
        val json = gson.toJson(message)
        Log.d(tag, "📤 Sending: ${message.type}")
        
        val success = webSocket?.send(json) ?: false
        if (!success) {
            Log.e(tag, "Failed to send message: ${message.type}")
            listener.onError("Failed to send ${message.type}")
        }
    }
    
    /**
     * Handle incoming message from server
     */
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
                    val candidateJson = json.getAsJsonObject("candidate")
                    val candidate = IceCandidate(
                        candidate = candidateJson.get("candidate").asString,
                        sdpMid = candidateJson.get("sdpMid").asString,
                        sdpMLineIndex = candidateJson.get("sdpMLineIndex").asInt
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
                    val errorMsg = json.get("message")?.asString ?: "Unknown error"
                    Log.e(tag, "⚠️ Server error: $errorMsg")
                    listener.onError(errorMsg)
                }
                
                else -> {
                    Log.w(tag, "Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing message: ${e.message}")
            listener.onError("Failed to parse message: ${e.message}")
        }
    }
    
    /**
     * Build WebSocket URL from server URL, call ID, and user ID
     */
    private fun buildWebSocketUrl(serverUrl: String, callId: String, userId: String): String {
        // Remove any protocol prefix and trailing slashes
        val cleanUrl = serverUrl
            .replace(Regex("^(http://|https://|ws://|wss://|/+)"), "")
            .replace(Regex("/+$"), "")
        
        // Use ws:// for now (wss:// in production)
        return "ws://$cleanUrl/ws/$callId/$userId"
    }
}
