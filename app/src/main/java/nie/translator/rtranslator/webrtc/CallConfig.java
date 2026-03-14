package nie.translator.rtranslator.webrtc;

/**
 * CallConfig — central configuration for WebRTC signaling and ICE servers.
 *
 * Change SIGNALING_SERVER_URL to match the IP / hostname of the
 * laptop that is running Bhasha_Setu/signaling-server/main.py
 * (e.g. "192.168.1.10:8000").
 *
 * All ICE server credentials below are free public / metered servers that
 * work out-of-the-box. Replace with your own TURN servers for production.
 */
public final class CallConfig {

    private CallConfig() {
    } // utility class – no instances

    // -----------------------------------------------------------------------
    // Signaling Server
    // -----------------------------------------------------------------------

    /**
     * Host:port of the FastAPI WebSocket signaling server.
     * Do NOT include a protocol prefix – SignalingClient adds ws:// itself.
     * Example: "192.168.31.29:8001"
     */
    public static final String SIGNALING_SERVER_URL = "https://7289-160-30-85-53.ngrok-free.app";

    // -----------------------------------------------------------------------
    // ICE Servers (STUN + TURN)
    // -----------------------------------------------------------------------

    /** Google public STUN servers. */
    public static final String STUN_SERVER_1 = "stun:stun.l.google.com:19302";
    public static final String STUN_SERVER_2 = "stun:stun1.l.google.com:19302";

    /** Free public OpenRelay TURN servers (no expiration). */
    public static final String TURN_SERVER_URL_1 = "turn:openrelay.metered.ca:80";
    public static final String TURN_SERVER_URL_2 = "turn:openrelay.metered.ca:443";
    public static final String TURN_USERNAME = "openrelayproject";
    public static final String TURN_CREDENTIAL = "openrelayproject";

    // -----------------------------------------------------------------------
    // DataChannel
    // -----------------------------------------------------------------------

    /**
     * Label of the reliable ordered DataChannel used for translated-text messages.
     */
    public static final String DATA_CHANNEL_LABEL = "translation";

    // -----------------------------------------------------------------------
    // Message format
    // -----------------------------------------------------------------------

    /** Separator placed between the translated text and its language code. */
    public static final String MESSAGE_SEPARATOR = "|||";
}
