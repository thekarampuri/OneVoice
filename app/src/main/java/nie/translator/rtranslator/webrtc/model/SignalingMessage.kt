package nie.translator.rtranslator.webrtc.model

/**
 * Signaling message types for WebRTC in OneVoice.
 * Adapted from BhashaSetu (com.example.voicetranslate.data.model).
 */

/** Base sealed interface every signaling message implements. */
sealed interface SignalingMessage {
    val type: String
    val callId: String
}

/** JOIN — client joins a call room. */
data class JoinMessage(
    override val callId: String,
    val userId: String
) : SignalingMessage {
    override val type: String = "join"
}

/** PEER_JOINED — server notifies that a peer joined. */
data class PeerJoinedMessage(
    override val callId: String,
    val peerId: String?
) : SignalingMessage {
    override val type: String = "peer-joined"
}

/** OFFER — WebRTC SDP offer. */
data class OfferMessage(
    override val callId: String,
    val sdp: String
) : SignalingMessage {
    override val type: String = "offer"
}

/** ANSWER — WebRTC SDP answer. */
data class AnswerMessage(
    override val callId: String,
    val sdp: String
) : SignalingMessage {
    override val type: String = "answer"
}

/** ICE_CANDIDATE — ICE candidate for NAT traversal. */
data class IceCandidateMessage(
    override val callId: String,
    val candidate: IceCandidate
) : SignalingMessage {
    override val type: String = "ice-candidate"
}

/** ICE candidate payload. */
data class IceCandidate(
    val candidate: String,
    val sdpMid: String,
    val sdpMLineIndex: Int
)

/** LEAVE — client leaves the call room. */
data class LeaveMessage(
    override val callId: String,
    val userId: String
) : SignalingMessage {
    override val type: String = "leave"
}

/** PEER_LEFT — server notifies that a peer left. */
data class PeerLeftMessage(
    override val callId: String,
    val peerId: String
) : SignalingMessage {
    override val type: String = "peer-left"
}

/** ERROR — server error message. */
data class ErrorMessage(
    val code: String,
    val message: String
) : SignalingMessage {
    override val type: String = "error"
    override val callId: String = ""
}
