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
    public static final String SIGNALING_SERVER_URL = "https://lilliam-tauriform-jaida.ngrok-free.dev";

    // -----------------------------------------------------------------------
    // ICE Servers (STUN + TURN)
    // -----------------------------------------------------------------------

    /** Google public STUN servers. */
    public static final String STUN_SERVER_1 = "stun:stun.l.google.com:19302";
    public static final String STUN_SERVER_2 = "stun:stun1.l.google.com:19302";

    /**
     * Free TURN servers from metered.ca (reliable, no expiration).
     * These cover UDP (port 80), TCP (port 80), and TLS (port 443) to maximize
     * NAT traversal success when devices are on different networks.
     */
    public static final String TURN_SERVER_URL_1 = "turn:a.relay.metered.ca:80";
    public static final String TURN_SERVER_URL_2 = "turn:a.relay.metered.ca:80?transport=tcp";
    public static final String TURN_SERVER_URL_3 = "turn:a.relay.metered.ca:443";
    public static final String TURN_SERVER_URL_4 = "turns:a.relay.metered.ca:443?transport=tcp";
    public static final String TURN_USERNAME = "e3ee672d1f7e70a96df05db9";
    public static final String TURN_CREDENTIAL = "mJP0LlEEj4nGDa6y";

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
