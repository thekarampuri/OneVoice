package com.example.voicetranslate.data.repository

import android.content.Context
import android.util.Log
import com.example.voicetranslate.data.model.*
import com.example.voicetranslate.webrtc.SignalingClient
import com.example.voicetranslate.webrtc.WebRtcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.*
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

/**
 * Repository coordinating SignalingClient and WebRtcClient
 * 
 * Responsibilities:
 * - Manage call lifecycle
 * - Coordinate signaling and WebRTC
 * - Handle offer/answer exchange
 * - Handle ICE candidate exchange
 * - Manage call state
 */
class CallRepository(
    private val context: Context,
    private val userRepository: UserRepository
) {
    private val tag = "CallRepository"
    
    private var webRtcClient: WebRtcClient? = null
    private var signalingClient: SignalingClient? = null
    
    private val _callState = MutableStateFlow<CallState>(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState
    
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted
    
    private var currentCallId: String = ""
    private var currentUserId: String = ""
    private var isInitiator: Boolean = false
    private var connectionTimeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * Start outgoing call
     */
    suspend fun startCall(serverUrl: String, callId: String) {
        Log.d(tag, "Starting call: $callId")
        
        try {
            currentCallId = callId
            // Don't set isInitiator here - it will be determined when we connect
            
            val user = userRepository.getUser()
            currentUserId = user.userId
            
            // Initialize WebRTC
            webRtcClient = WebRtcClient(context, webRtcListener)
            webRtcClient?.initialize()
            webRtcClient?.createPeerConnection()
            
            // Connect to signaling server
            signalingClient = SignalingClient(serverUrl, signalingListener)
            signalingClient?.connect(callId, user.userId)
            
            _callState.value = CallState.CALLING
            
            // Start connection timeout (30 seconds)
            startConnectionTimeout()
        } catch (e: Exception) {
            Log.e(tag, "❌ Failed to start call: ${e.message}", e)
            _callState.value = CallState.ENDED
            throw e
        }
    }
    
    /**
     * Start timeout for connection establishment
     */
    private fun startConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = scope.launch {
            delay(30000) // 30 seconds
            if (_callState.value != CallState.CONNECTED) {
                Log.e(tag, "❌ Connection timeout - call failed to connect")
                endCall()
            }
        }
    }
    
    /**
     * Cancel connection timeout
     */
    private fun cancelConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null
    }

    
    /**
     * SignalingClient listener
     */
    private val signalingListener = object : SignalingClient.SignalingListener {
        override fun onConnected() {
            Log.d(tag, "✅ Connected to signaling server")
            // We'll determine if we're the initiator when peer-joined event arrives
            // If we get peer-joined immediately, we're the first (initiator)
            // If we don't get it, we're waiting for the second user
        }
        
        override fun onPeerJoined(peerId: String?) {
            Log.d(tag, "👤 Peer joined: $peerId")
            
            isInitiator = true
            Log.d(tag, "📞 We're the INITIATOR (joined first) - creating offer")
            _callState.value = CallState.CONNECTING
            // Small delay to ensure peer is ready
            scope.launch {
                delay(500)
                webRtcClient?.createOffer()
            }
        }
        
        override fun onExistingPeer(peerId: String?) {
            Log.d(tag, "👤 Existing peer: $peerId")
            
            isInitiator = false
            Log.d(tag, "📞 We're the CALLEE (joined second) - waiting for offer")
            _callState.value = CallState.RINGING
        }
        
        override fun onOfferReceived(sdp: String) {
            Log.d(tag, "📞 Offer received")
            
            // Set remote description (offer)
            val sessionDescription = SessionDescription(
                SessionDescription.Type.OFFER,
                sdp
            )
            
            _callState.value = CallState.CONNECTING
            
            // Fixed race condition: Create answer ONLY AFTER remote description is set
            webRtcClient?.setRemoteDescription(sessionDescription) {
                Log.d(tag, "✅ Remote description set, creating answer...")
                webRtcClient?.createAnswer()
            }
        }
        
        override fun onAnswerReceived(sdp: String) {
            Log.d(tag, "✅ Answer received")
            
            // Set remote description (answer)
            val sessionDescription = SessionDescription(
                SessionDescription.Type.ANSWER,
                sdp
            )
            webRtcClient?.setRemoteDescription(sessionDescription)
        }
        
        override fun onIceCandidateReceived(candidate: com.example.voicetranslate.data.model.IceCandidate) {
            Log.d(tag, "🧊 ICE candidate received")
            
            // Add ICE candidate to peer connection
            val iceCandidate = IceCandidate(
                candidate.sdpMid,
                candidate.sdpMLineIndex,
                candidate.candidate
            )
            webRtcClient?.addIceCandidate(iceCandidate)
        }
        
        override fun onPeerLeft(peerId: String) {
            Log.d(tag, "👋 Peer left: $peerId")
            endCall()
        }
        
        override fun onDisconnected() {
            Log.d(tag, "❌ Disconnected from signaling server")
            if (_callState.value != CallState.ENDED) {
                endCall()
            }
        }
        
        override fun onError(error: String) {
            Log.e(tag, "⚠️ Signaling error: $error")
            endCall()
        }
    }
    
    /**
     * WebRtcClient listener
     */
    private val webRtcListener = object : WebRtcClient.WebRtcListener {
        override fun onLocalSdpCreated(sdp: SessionDescription) {
            val sdpString = sdp.description
            val type = sdp.type.canonicalForm()
            
            Log.d(tag, "📤 Local SDP created: $type")
            
            // Send SDP to peer via signaling
            if (type == "offer") {
                signalingClient?.sendOffer(currentCallId, sdpString)
            } else if (type == "answer") {
                signalingClient?.sendAnswer(currentCallId, sdpString)
            }
        }
        
        override fun onIceCandidateGenerated(candidate: IceCandidate) {
            Log.d(tag, "📤 ICE candidate generated")
            
            // Send ICE candidate to peer via signaling.
            // WebRTC Java API may return null for sdpMid, which throws NPE in Kotlin. Use safe calls.
            val iceCandidate = com.example.voicetranslate.data.model.IceCandidate(
                candidate = candidate.sdp ?: "",
                sdpMid = candidate.sdpMid ?: "",
                sdpMLineIndex = candidate.sdpMLineIndex
            )
            signalingClient?.sendIceCandidate(currentCallId, iceCandidate)
        }
        
        override fun onConnectionStateChanged(state: PeerConnection.IceConnectionState) {
            Log.d(tag, "🔌 Connection state: $state")
            
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED -> {
                    Log.d(tag, "✅ Call connected!")
                    _callState.value = CallState.CONNECTED
                    cancelConnectionTimeout()
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    Log.d(tag, "⚠️ Call DISCONNECTED (WebRTC gathering or network hop) - waiting to see if it recovers")
                    // Do NOT call endCall() here as WebRTC often transitions through DISCONNECTED during ICE gathering or handover
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    Log.e(tag, "❌ Connection failed")
                    endCall()
                }
                PeerConnection.IceConnectionState.CHECKING -> {
                    Log.d(tag, "🔍 Checking connectivity...")
                }
                else -> {
                    Log.d(tag, "State: $state")
                }
            }
        }
        
        override fun onAudioTrackReceived() {
            Log.d(tag, "🎵 Remote audio track received")
            // Audio will play automatically
        }
        
        override fun onError(error: String) {
            Log.e(tag, "⚠️ WebRTC error: $error")
            endCall()
        }
    }
    
    /**
     * Toggle mute
     */
    fun toggleMute() {
        val newMutedState = !_isMuted.value
        _isMuted.value = newMutedState
        webRtcClient?.setMuted(newMutedState)
        Log.d(tag, if (newMutedState) "🔇 Muted" else "🔊 Unmuted")
    }
    
    /**
     * End call
     */
    fun endCall() {
        Log.d(tag, "Ending call...")
        
        webRtcClient?.close()
        webRtcClient = null
        
        signalingClient?.disconnect()
        signalingClient = null
        
        _callState.value = CallState.ENDED
        _isMuted.value = false
        
        cancelConnectionTimeout()
        scope.cancel()
        
        Log.d(tag, "✅ Call ended")
    }
}
