package com.example.voicetranslate.data.model

/**
 * Call state enum
 * 
 * Represents the current state of a call from the user's perspective.
 * 
 * State transitions:
 * IDLE -> CALLING (user initiates call)
 * IDLE -> RINGING (incoming call)
 * CALLING -> CONNECTING (peer answered)
 * RINGING -> CONNECTING (user answered)
 * CONNECTING -> CONNECTED (WebRTC connection established)
 * Any state -> ENDED (call terminated)
 */
enum class CallState {
    /**
     * No active call
     */
    IDLE,
    
    /**
     * Outgoing call - waiting for peer to answer
     */
    CALLING,
    
    /**
     * Incoming call - waiting for user to answer
     */
    RINGING,
    
    /**
     * Call accepted - establishing WebRTC connection
     */
    CONNECTING,
    
    /**
     * Call active - audio streaming
     */
    CONNECTED,
    
    /**
     * Call terminated
     */
    ENDED
}
