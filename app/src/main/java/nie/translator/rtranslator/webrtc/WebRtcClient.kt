package nie.translator.rtranslator.webrtc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import nie.translator.rtranslator.webrtc.model.IceCandidate
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * WebRTC audio + DataChannel client.
 * Adapted from BhashaSetu (com.example.voicetranslate.webrtc.WebRtcClient).
 * Package: nie.translator.rtranslator.webrtc
 *
 * Key additions over BhashaSetu original:
 *  - WebRTC DataChannel ("translation") for sending/receiving translated text
 *    messages between peers, replacing the old BluetoothCommunicator.sendMessage().
 *
 * Audio pipeline remains purely WebRTC (mic → encode → transmit → decode → speaker).
 * The ONNX STT + translation + TTS pipeline in WebRtcVoiceTranslationService
 * runs locally; only the resulting translated text string is transmitted
 * through the DataChannel to the remote peer.
 */
class WebRtcClient(
    private val context: Context,
    private val listener: WebRtcListener
) {
    private val tag = "WebRtcClient"

    companion object {
        const val CALL_ENDED_SIGNAL = "__CALL_ENDED__"
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    // DataChannel used across the connection lifetime
    private var dataChannel: DataChannel? = null

    // Messages queued while DataChannel is not yet OPEN
    private val pendingTranslationQueue = java.util.concurrent.ConcurrentLinkedQueue<String>()

    // Audio components
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    // ICE servers
    private val iceServers = listOf(
        PeerConnection.IceServer.builder(CallConfig.STUN_SERVER_1).createIceServer(),
        PeerConnection.IceServer.builder(CallConfig.STUN_SERVER_2).createIceServer(),
        PeerConnection.IceServer.builder(CallConfig.TURN_SERVER_URL_1)
            .setUsername(CallConfig.TURN_USERNAME)
            .setPassword(CallConfig.TURN_CREDENTIAL)
            .createIceServer(),
        PeerConnection.IceServer.builder(CallConfig.TURN_SERVER_URL_2)
            .setUsername(CallConfig.TURN_USERNAME)
            .setPassword(CallConfig.TURN_CREDENTIAL)
            .createIceServer(),
        PeerConnection.IceServer.builder(CallConfig.TURN_SERVER_URL_3)
            .setUsername(CallConfig.TURN_USERNAME)
            .setPassword(CallConfig.TURN_CREDENTIAL)
            .createIceServer(),
        PeerConnection.IceServer.builder(CallConfig.TURN_SERVER_URL_4)
            .setUsername(CallConfig.TURN_USERNAME)
            .setPassword(CallConfig.TURN_CREDENTIAL)
            .createIceServer()
    )

    // -----------------------------------------------------------------------
    // Queues / flags
    // -----------------------------------------------------------------------

    private val iceCandidateQueue      = mutableListOf<org.webrtc.IceCandidate>()
    private val localIceCandidateQueue = mutableListOf<org.webrtc.IceCandidate>()
    private var isRemoteDescriptionSet = false
    private var isLocalDescriptionSet  = false

    // -----------------------------------------------------------------------
    // Listener
    // -----------------------------------------------------------------------



    // -----------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------

    fun initialize() {
        Log.d(tag, "Initializing WebRTC audio…")

        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .setFieldTrials("")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()

        audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("101", audioSource)
        
        // --- CRITICAL FIX: Ensure no raw audio (and therefore no echo) is transmitted ---
        localAudioTrack?.setEnabled(false)

        Log.d(tag, "✅ WebRTC initialized (Audio track present but MUTED, DataChannel active)")
    }

    fun createPeerConnection(isInitiator: Boolean = false) {
        Log.d(tag, "Creating PeerConnection (initiator=$isInitiator)…")

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics          = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType     = PeerConnection.IceTransportsType.ALL
            bundlePolicy          = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy         = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy    = PeerConnection.TcpCandidatePolicy.ENABLED
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, peerConnectionObserver)

        if (peerConnection == null) {
            listener.onError("Failed to create PeerConnection")
            return
        }

        localAudioTrack?.let {
            peerConnection?.addTrack(it)
        }

        // Initiator creates the DataChannel; answerer receives it via peerConnectionObserver.
        if (isInitiator) {
            val dcInit = DataChannel.Init().apply {
                ordered = true
            }
            dataChannel = peerConnection?.createDataChannel("translation", dcInit)
            dataChannel?.registerObserver(dataChannelObserver)
            Log.d(tag, "✅ DataChannel 'translation' created (initiator)")
        }

        Log.d(tag, "✅ PeerConnection created")
    }

    // -----------------------------------------------------------------------
    // SDP  (offer / answer)
    // -----------------------------------------------------------------------

    fun createOffer() {
        Log.d(tag, "Creating SDP offer…")
        val sdpConstraints = MediaConstraints()
        Handler(Looper.getMainLooper()).post {
            peerConnection?.createOffer(makeSdpObserver("offer"), sdpConstraints)
        }
    }

    fun createAnswer() {
        Log.d(tag, "Creating SDP answer…")
        val sdpConstraints = MediaConstraints()
        Handler(Looper.getMainLooper()).post {
            peerConnection?.createAnswer(makeSdpObserver("answer"), sdpConstraints)
        }
    }

    private fun makeSdpObserver(kind: String) = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {
            if (sdp == null) {
                listener.onError("Failed to create $kind: SDP is null")
                return
            }
            Handler(Looper.getMainLooper()).post {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Handler(Looper.getMainLooper()).post {
                            Log.d(tag, "✅ Local description set ($kind)")
                            isLocalDescriptionSet = true
                            listener.onLocalSdpCreated(sdp)
                            drainLocalIceCandidateQueue()
                        }
                    }
                    override fun onSetFailure(error: String?) {
                        listener.onError("Failed to set local description: $error")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }
        }
        override fun onCreateFailure(error: String?) {
            listener.onError("Failed to create $kind: $error")
        }
        override fun onSetSuccess() {}
        override fun onSetFailure(p0: String?) {}
    }

    // -----------------------------------------------------------------------
    // Remote description / ICE
    // -----------------------------------------------------------------------

    fun setRemoteDescription(sdp: SessionDescription, onComplete: () -> Unit = {}) {
        Log.d(tag, "Setting remote description (${sdp.type})…")
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
                    listener.onError("Failed to set remote description: $error")
                }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, sdp)
        }
    }

    fun addIceCandidate(candidate: IceCandidate) {
        Handler(Looper.getMainLooper()).post {
            val rtcCandidate = org.webrtc.IceCandidate(
                candidate.sdpMid,
                candidate.sdpMLineIndex,
                candidate.candidate
            )
            if (isRemoteDescriptionSet) {
                Log.d(tag, "Adding ICE candidate: ${candidate.sdpMid}")
                peerConnection?.addIceCandidate(rtcCandidate)
            } else {
                Log.d(tag, "⏳ Queuing ICE candidate")
                iceCandidateQueue.add(rtcCandidate)
            }
        }
    }

    // -----------------------------------------------------------------------
    // DataChannel — send translated text
    // -----------------------------------------------------------------------

    /**
     * Send a translated-text message to the remote peer.
     * Format:  "<translated_text>|||<language_code>"
     * The remote peer's WebRtcVoiceTranslationService will parse this
     * and feed it to TTS, exactly as it used to receive Bluetooth messages.
     */
    /**
     * Notify remote peer that this user ended the call.
     * Sends a special sentinel through the DataChannel before closing.
     */
    fun sendEndCallSignal() {
        val dc = dataChannel
        if (dc != null && dc.state() == DataChannel.State.OPEN) {
            val payload = CALL_ENDED_SIGNAL
            val buffer = DataChannel.Buffer(
                java.nio.ByteBuffer.wrap(payload.toByteArray(Charsets.UTF_8)),
                false
            )
            dc.send(buffer)
            Log.d(tag, "📞 Sent call-ended signal to peer")
        }
    }

    fun sendTranslationMessage(text: String, languageCode: String) {
        val payload = text + CallConfig.MESSAGE_SEPARATOR + languageCode
        val dc = dataChannel
        if (dc != null && dc.state() == DataChannel.State.OPEN) {
            val buffer = DataChannel.Buffer(
                java.nio.ByteBuffer.wrap(payload.toByteArray(Charsets.UTF_8)),
                false  // not binary
            )
            val result = dc.send(buffer)
            if (result) {
                Log.d(tag, "📤 Translation sent: lang=$languageCode  text=${text.take(40)}")
            } else {
                Log.w(tag, "⚠️ Failed to send translation even though channel is OPEN")
            }
        } else {
            // DataChannel not open yet — queue for later delivery
            Log.w(tag, "⏳ DataChannel not OPEN (state=${dc?.state()}), queuing translation")
            synchronized(pendingTranslationQueue) {
                pendingTranslationQueue.add(payload)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    fun setMuted(isMuted: Boolean) {
        // Track is permanently muted to avoid echo, but this function exists for interface compliance
        Log.d(tag, "setMuted called ($isMuted) but track is permanently muted to avoid echo.")
    }

    fun close() {
        synchronized(pendingTranslationQueue) { pendingTranslationQueue.clear() }
        dataChannel?.unregisterObserver()
        dataChannel?.close()
        dataChannel = null
        peerConnection?.close()
        audioSource?.dispose()
        audioDeviceModule?.release()
        peerConnectionFactory?.dispose()
    }

    // -----------------------------------------------------------------------
    // Private
    // -----------------------------------------------------------------------



    private fun drainLocalIceCandidateQueue() {
        if (localIceCandidateQueue.isNotEmpty()) {
            Log.d(tag, "Draining ${localIceCandidateQueue.size} local ICE candidates")
            localIceCandidateQueue.forEach { c ->
                listener.onIceCandidateGenerated(
                    IceCandidate(c.sdp, c.sdpMid ?: "", c.sdpMLineIndex)
                )
            }
            localIceCandidateQueue.clear()
        }
    }

    private fun drainIceCandidateQueue() {
        Handler(Looper.getMainLooper()).post {
            if (iceCandidateQueue.isNotEmpty()) {
                Log.d(tag, "Draining ${iceCandidateQueue.size} queued ICE candidates")
                iceCandidateQueue.forEach { peerConnection?.addIceCandidate(it) }
                iceCandidateQueue.clear()
            }
        }
    }

    // -----------------------------------------------------------------------
    // DataChannel observer
    // -----------------------------------------------------------------------

    private val dataChannelObserver = object : DataChannel.Observer {
        override fun onStateChange() {
            val state = dataChannel?.state()
            Log.d(tag, "DataChannel state: $state")
            if (state == DataChannel.State.OPEN) {
                Log.d(tag, "✅ DataChannel OPEN — draining pending queue")
                // Drain queued messages now that the channel is open
                val dc = dataChannel ?: return
                val toSend: List<String>
                synchronized(pendingTranslationQueue) {
                    toSend = pendingTranslationQueue.toList()
                    pendingTranslationQueue.clear()
                }
                for (payload in toSend) {
                    val buffer = DataChannel.Buffer(
                        java.nio.ByteBuffer.wrap(payload.toByteArray(Charsets.UTF_8)),
                        false
                    )
                    val ok = dc.send(buffer)
                    Log.d(tag, "📤 Flushed queued msg (ok=$ok): ${payload.take(40)}")
                }
            }
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            val data = buffer.data
            val text = Charsets.UTF_8.decode(data).toString()
            Log.d(tag, "📥 DataChannel message received: ${text.take(80)}")
            if (text == CALL_ENDED_SIGNAL) {
                Log.d(tag, "📡 Peer ended the call")
                Handler(Looper.getMainLooper()).post { listener.onCallEndedByPeer() }
            } else {
                Handler(Looper.getMainLooper()).post { listener.onTranslationReceived(text) }
            }
        }

        override fun onBufferedAmountChange(previousAmount: Long) {}
    }

    // -----------------------------------------------------------------------
    // PeerConnection observer
    // -----------------------------------------------------------------------

    private val peerConnectionObserver = object : PeerConnection.Observer {

        override fun onIceCandidate(candidate: org.webrtc.IceCandidate?) {
            if (candidate != null) {
                Handler(Looper.getMainLooper()).post {
                    val wrapped = IceCandidate(candidate.sdp, candidate.sdpMid ?: "", candidate.sdpMLineIndex)
                    if (isLocalDescriptionSet) {
                        Log.d(tag, "🧊 ICE candidate generated: ${candidate.sdpMid}")
                        listener.onIceCandidateGenerated(wrapped)
                    } else {
                        Log.d(tag, "⏳ Queueing local ICE candidate")
                        localIceCandidateQueue.add(candidate)
                    }
                }
            }
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            state?.let {
                listener.onConnectionStateChanged(it)
            }
        }

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            Log.d(tag, "🔍 ICE gathering: $state")
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            Log.d(tag, "🎵 Track received: ${transceiver?.mediaType}")
            listener.onAudioTrackReceived()
        }

        /** Remote peer created the DataChannel — register our observer on it. */
        override fun onDataChannel(dc: DataChannel?) {
            Log.d(tag, "📊 DataChannel received from remote: ${dc?.label()}")
            if (dc != null && dc.label() == "translation") {
                dataChannel = dc
                dataChannel?.registerObserver(dataChannelObserver)
                // If it's already OPEN, the observer might not get onStateChange(OPEN). Trigger it manually.
                if (dc.state() == DataChannel.State.OPEN) {
                    dataChannelObserver.onStateChange()
                }
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out org.webrtc.IceCandidate>?) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
    }
}

interface WebRtcListener {
    fun onLocalSdpCreated(sdp: SessionDescription)
    fun onIceCandidateGenerated(candidate: IceCandidate)
    fun onConnectionStateChanged(state: PeerConnection.IceConnectionState)
    fun onAudioTrackReceived()
    /** Called when a translated-text message arrives through the DataChannel. */
    fun onTranslationReceived(translatedText: String)
    /** Called when the remote peer sends an end-call signal. */
    fun onCallEndedByPeer()
    fun onError(error: String)
}
