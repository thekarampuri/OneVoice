package com.example.voicetranslate.data.model

/**
 * Call data model
 * 
 * Represents a 1-to-1 call session.
 * Pure data class with no business logic.
 * 
 * @property callId Unique identifier for the call room (shared between caller and callee)
 * @property callerId User ID of the caller (who initiated the call)
 * @property calleeId User ID of the callee (who received the call)
 * @property state Current state of the call
 * @property startedAt Timestamp when call was initiated (milliseconds)
 * @property connectedAt Timestamp when call was connected (milliseconds), null if not connected yet
 * @property endedAt Timestamp when call ended (milliseconds), null if still active
 */
data class Call(
    val callId: String,
    val callerId: String,
    val calleeId: String?,
    val state: CallState = CallState.IDLE,
    val startedAt: Long = System.currentTimeMillis(),
    val connectedAt: Long? = null,
    val endedAt: Long? = null
) {
    /**
     * Check if this user is the caller
     */
    fun isCallerUser(userId: String): Boolean = callerId == userId
    
    /**
     * Check if this user is the callee
     */
    fun isCalleeUser(userId: String): Boolean = calleeId == userId
    
    /**
     * Get the peer's user ID (the other person in the call)
     */
    fun getPeerId(myUserId: String): String? {
        return when {
            callerId == myUserId -> calleeId
            calleeId == myUserId -> callerId
            else -> null
        }
    }
    
    /**
     * Get call duration in milliseconds (if connected)
     */
    fun getDuration(): Long? {
        return connectedAt?.let { connected ->
            val end = endedAt ?: System.currentTimeMillis()
            end - connected
        }
    }
    
    companion object {
        /**
         * Create a new outgoing call
         */
        fun createOutgoing(callId: String, callerId: String): Call {
            return Call(
                callId = callId,
                callerId = callerId,
                calleeId = null,
                state = CallState.CALLING
            )
        }
        
        /**
         * Create a new incoming call
         */
        fun createIncoming(callId: String, callerId: String, calleeId: String): Call {
            return Call(
                callId = callId,
                callerId = callerId,
                calleeId = calleeId,
                state = CallState.RINGING
            )
        }
    }
}
