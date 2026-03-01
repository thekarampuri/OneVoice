package com.example.voicetranslate.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import android.os.Handler
import android.os.Looper

/**
 * WebRTC Client for audio-only calls
 * 
 * Responsibilities:
 * - Create and manage PeerConnection
 * - Handle local audio track (microphone)
 * - Handle remote audio track (peer's audio)
 * - Create/answer SDP offers
 * - Handle ICE candidates
 * - Manage connection lifecycle
 */
class WebRtcClient(
    private val context: Context,
    private val listener: WebRtcListener
) {
    private val tag = "WebRtcClient"
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    
    // ICE servers (STUN + TURN for NAT traversal)
    // Using free TURN servers from metered.ca for better connectivity
    private val iceServers = listOf(
        // Google STUN servers
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        // Free TURN servers (metered.ca)
        PeerConnection.IceServer.builder("turn:a.relay.metered.ca:80")
            .setUsername("87e69f8c0c87b0fc5e056a36")
            .setPassword("sBP6FRtpEfj3MgDL")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:a.relay.metered.ca:443")
            .setUsername("87e69f8c0c87b0fc5e056a36")
            .setPassword("sBP6FRtpEfj3MgDL")
            .createIceServer()
    )
    
    interface WebRtcListener {
        fun onLocalSdpCreated(sdp: SessionDescription)
        fun onIceCandidateGenerated(candidate: IceCandidate)
        fun onConnectionStateChanged(state: PeerConnection.IceConnectionState)
        fun onAudioTrackReceived()
        fun onError(error: String)
    }
    
    fun initialize() {
        Log.d(tag, "Initializing WebRTC...")
        
        // Initialize PeerConnectionFactory
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .setFieldTrials("")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        
        // Create audio device module
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .createAudioDeviceModule()
        
        // Build PeerConnectionFactory with audio support
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()
        
        // Release audio device module after factory creation
        audioDeviceModule.release()
        
        Log.d(tag, "✅ WebRTC initialized")
    }
    
    fun createPeerConnection() {
        Log.d(tag, "Creating PeerConnection...")
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            peerConnectionObserver
        )
        
        if (peerConnection == null) {
            listener.onError("Failed to create PeerConnection")
            return
        }
        
        addLocalAudioTrack()
        Log.d(tag, "✅ PeerConnection created")
    }
    
    private fun addLocalAudioTrack() {
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("local_audio", audioSource)
        localAudioTrack?.setEnabled(true)
        peerConnection?.addTrack(localAudioTrack, listOf("local_stream"))
    }
    
    fun createOffer() {
        Log.d(tag, "Creating SDP offer...")
        
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        
        Handler(Looper.getMainLooper()).post {
            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp == null) {
                        Log.e(tag, "❌ Created SDP is null")
                        listener.onError("Failed to create offer: SDP is null")
                        return
                    }
                    
                    Log.d(tag, "✅ Offer created successfully")
                    // Execute setLocalDescription on main thread to avoid crash
                    Handler(Looper.getMainLooper()).post {
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                Handler(Looper.getMainLooper()).post {
                                    Log.d(tag, "✅ Local description set (offer)")
                                    isLocalDescriptionSet = true
                                    listener.onLocalSdpCreated(sdp)
                                    drainLocalIceCandidateQueue()
                                }
                            }
                            
                            override fun onSetFailure(error: String?) {
                                Log.e(tag, "❌ Failed to set local description: $error")
                                listener.onError("Failed to set local description: $error")
                            }
                            
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onCreateFailure(p0: String?) {}
                        }, sdp)
                    }
                }
                
                override fun onCreateFailure(error: String?) {
                    Log.e(tag, "❌ Failed to create offer: $error")
                    listener.onError("Failed to create offer: $error")
                }
                
                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, sdpConstraints)
        }
    }
    
    fun createAnswer() {
        Log.d(tag, "Creating SDP answer...")
        
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        
        Handler(Looper.getMainLooper()).post {
            peerConnection?.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp == null) {
                        Log.e(tag, "❌ Created SDP is null")
                        listener.onError("Failed to create answer: SDP is null")
                        return
                    }
                    
                    Log.d(tag, "✅ Answer created successfully")
                    // Execute setLocalDescription on main thread to avoid crash
                    Handler(Looper.getMainLooper()).post {
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                Handler(Looper.getMainLooper()).post {
                                    Log.d(tag, "✅ Local description set (answer)")
                                    isLocalDescriptionSet = true
                                    listener.onLocalSdpCreated(sdp)
                                    drainLocalIceCandidateQueue()
                                }
                            }
                            
                            override fun onSetFailure(error: String?) {
                                Log.e(tag, "❌ Failed to set local description: $error")
                                listener.onError("Failed to set local description: $error")
                            }
                            
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onCreateFailure(p0: String?) {}
                        }, sdp)
                    }
                }
                
                override fun onCreateFailure(error: String?) {
                    Log.e(tag, "❌ Failed to create answer: $error")
                    listener.onError("Failed to create answer: $error")
                }
                
                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, sdpConstraints)
        }
    }
    
    // Queue for ICE candidates received before remote description
    private val iceCandidateQueue = mutableListOf<IceCandidate>()
    private var isRemoteDescriptionSet = false

    // Queue for local ICE candidates generated before local description is set
    private val localIceCandidateQueue = mutableListOf<IceCandidate>()
    private var isLocalDescriptionSet = false
    
    private fun drainLocalIceCandidateQueue() {
        if (localIceCandidateQueue.isNotEmpty()) {
            Log.d(tag, "Processing ${localIceCandidateQueue.size} queued local ICE candidates...")
            localIceCandidateQueue.forEach { candidate ->
                listener.onIceCandidateGenerated(candidate)
            }
            localIceCandidateQueue.clear()
        }
    }

    fun setRemoteDescription(sdp: SessionDescription, onComplete: () -> Unit = {}) {
        Log.d(tag, "Setting remote description (${sdp.type})...")
        
        // Execute on main thread to avoid crash
        Handler(Looper.getMainLooper()).post {
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Handler(Looper.getMainLooper()).post {
                        Log.d(tag, "✅ Remote description set (${sdp.type})")
                        isRemoteDescriptionSet = true
                        drainIceCandidateQueue()
                        onComplete()
                    }
                }
                
                override fun onSetFailure(error: String?) {
                    Log.e(tag, "❌ Failed to set remote description: $error")
                    listener.onError("Failed to set remote description: $error")
                }
                
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, sdp)
        }
    }
    
    fun addIceCandidate(candidate: IceCandidate) {
        // Must run on main thread to avoid WebRTC crashes
        Handler(Looper.getMainLooper()).post {
            if (isRemoteDescriptionSet) {
                Log.d(tag, "Adding ICE candidate: ${candidate.sdpMid}")
                val success = peerConnection?.addIceCandidate(candidate) ?: false
                if (success) {
                    Log.d(tag, "✅ ICE candidate added")
                } else {
                    Log.w(tag, "⚠️ Failed to add ICE candidate")
                }
            } else {
                Log.d(tag, "⏳ Queueing ICE candidate (remote description not set)")
                iceCandidateQueue.add(candidate)
            }
        }
    }

    private fun drainIceCandidateQueue() {
        // Must run on main thread to avoid WebRTC crashes
        Handler(Looper.getMainLooper()).post {
            if (iceCandidateQueue.isNotEmpty()) {
                Log.d(tag, "Processing ${iceCandidateQueue.size} queued ICE candidates...")
                iceCandidateQueue.forEach { candidate ->
                    val success = peerConnection?.addIceCandidate(candidate) ?: false
                    if (success) {
                        Log.d(tag, "✅ Queued ICE candidate added")
                    } else {
                        Log.w(tag, "⚠️ Failed to add queued ICE candidate")
                    }
                }
                iceCandidateQueue.clear()
            }
        }
    }
    
    fun setMuted(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }
    
    fun close() {
        peerConnection?.close()
        peerConnectionFactory?.dispose()
    }
    
    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate != null) {
                // Execute on main thread to avoid ConcurrentModificationException with localIceCandidateQueue
                Handler(Looper.getMainLooper()).post {
                    if (isLocalDescriptionSet) {
                        Log.d(tag, "🧊 ICE candidate generated: ${candidate.sdpMid}")
                        listener.onIceCandidateGenerated(candidate)
                    } else {
                        Log.d(tag, "⏳ Queueing local ICE candidate (local description not set)")
                        localIceCandidateQueue.add(candidate)
                    }
                }
            }
        }
        
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            state?.let { 
                Log.d(tag, "🔌 ICE connection state: $it")
                when (it) {
                    PeerConnection.IceConnectionState.FAILED -> {
                        Log.e(tag, "❌ ICE connection FAILED - check network/firewall")
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        Log.w(tag, "⚠️ ICE connection DISCONNECTED")
                    }
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        Log.d(tag, "✅ ICE connection CONNECTED successfully")
                    }
                    else -> {}
                }
                listener.onConnectionStateChanged(it) 
            }
        }
        
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            Log.d(tag, "🔍 ICE gathering state: $state")
        }
        
        override fun onTrack(transceiver: RtpTransceiver?) {
            Log.d(tag, "🎵 Track received: ${transceiver?.mediaType}")
            listener.onAudioTrackReceived()
        }
        
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            Log.d(tag, "📡 Signaling state: $state")
        }
        
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            Log.d(tag, "🗑️ ICE candidates removed: ${candidates?.size}")
        }
        
        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(tag, "📶 ICE receiving: $receiving")
        }
        
        override fun onAddStream(stream: MediaStream?) {
            Log.d(tag, "➕ Stream added (deprecated): ${stream?.id}")
        }
        
        override fun onRemoveStream(stream: MediaStream?) {
            Log.d(tag, "➖ Stream removed (deprecated): ${stream?.id}")
        }
        
        override fun onDataChannel(dataChannel: DataChannel?) {
            Log.d(tag, "📊 Data channel: ${dataChannel?.label()}")
        }
        
        override fun onRenegotiationNeeded() {
            Log.d(tag, "🔄 Renegotiation needed")
        }
        
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            Log.d(tag, "➕ Track added: ${receiver?.track()?.kind()}")
        }
    }
}
