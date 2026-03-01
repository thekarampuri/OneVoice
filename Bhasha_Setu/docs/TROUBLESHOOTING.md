# WebRTC Connection Troubleshooting Guide

## Issue: User 2 Connection Terminates After 3-4 Seconds

### Root Cause Analysis

The issue you're experiencing is **NOT** related to different User IDs. Each app instance is **supposed** to have a unique User ID - this is correct behavior.

The real problem is that the WebRTC connection is failing to establish between the two peers, likely due to:

1. **NAT/Firewall Issues** - The devices can't establish a direct peer-to-peer connection
2. **Missing TURN Server** - STUN servers alone aren't enough in restrictive network environments
3. **Network Configuration** - Both devices need to be able to reach each other or use a relay

### What I Fixed

#### 1. Added TURN Server Support (`WebRtcClient.kt`)

**Before:** Only STUN servers (Google's public STUN)
```kotlin
private val iceServers = listOf(
    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
    PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
)
```

**After:** STUN + TURN servers (using free metered.ca TURN servers)
```kotlin
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
```

**Why this helps:** TURN servers act as a relay when direct peer-to-peer connection fails. This dramatically improves connection success rate.

#### 2. Improved ICE Configuration

Added better ICE policies for more reliable connections:
```kotlin
val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
    continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
    iceTransportsType = PeerConnection.IceTransportsType.ALL
    bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
    rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
    tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
}
```

#### 3. Added Connection Timeout Handling (`CallRepository.kt`)

Added a 30-second timeout to automatically end the call if connection isn't established:
```kotlin
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
```

#### 4. Added Delay Before Offer Creation

Added a 500ms delay to ensure the peer is ready before creating the offer:
```kotlin
if (_callState.value == CallState.CALLING) {
    isInitiator = true
    Log.d(tag, "📞 We're the INITIATOR (joined first) - creating offer")
    _callState.value = CallState.CONNECTING
    // Small delay to ensure peer is ready
    scope.launch {
        delay(500)
        webRtcClient?.createOffer()
    }
}
```

#### 5. Enhanced Logging

Added detailed logging throughout the connection process to help diagnose issues:
- WebSocket connection details (URL, Call ID, User ID)
- ICE connection state changes with specific error messages
- Connection success/failure indicators

### Testing Instructions

1. **Rebuild the Android app:**
   ```bash
   cd android
   ./gradlew clean assembleDebug
   ```

2. **Install on both devices:**
   - Install the updated APK on both test devices

3. **Start the signaling server:**
   ```bash
   cd signaling-server
   python main.py
   ```

4. **Test the connection:**
   - **User 1 (Initiator):**
     1. Open the app
     2. Note your "Your ID" (this is just for reference, not needed for connection)
     3. Enter Server URL: `<your-server-ip>:8001`
     4. Enter Call ID: `test-room-123`
     5. Click "Start Call"
     6. Wait in the room
   
   - **User 2 (Callee):**
     1. Open the app
     2. Enter Server URL: `<same-server-ip>:8001`
     3. Enter Call ID: `test-room-123` (SAME as User 1)
     4. Click "Start Call"
     5. Connection should establish within 5-10 seconds

### What to Look For in Logs

Use `adb logcat` to monitor the connection process:

```bash
# Filter for WebRTC logs
adb logcat | grep -E "WebRtcClient|SignalingClient|CallRepository"
```

**Successful connection logs should show:**
```
✅ WebSocket connected to: ws://...
👤 Peer joined: ...
📞 We're the INITIATOR (joined first) - creating offer
📤 Local SDP created: offer
🧊 ICE candidate generated
🔌 ICE connection state: CHECKING
🔌 ICE connection state: CONNECTED
✅ ICE connection CONNECTED successfully
✅ Call connected!
```

**Failed connection logs might show:**
```
🔌 ICE connection state: FAILED
❌ ICE connection FAILED - check network/firewall
❌ Connection timeout - call failed to connect
```

### Common Issues and Solutions

#### Issue 1: "Room full" error
**Cause:** More than 2 users trying to join the same room
**Solution:** Use a unique Call ID for each pair of users

#### Issue 2: Connection timeout after 30 seconds
**Cause:** Network/firewall blocking WebRTC traffic
**Solution:** 
- Ensure both devices can reach the signaling server
- Check firewall settings
- Try on a different network (e.g., mobile data instead of WiFi)

#### Issue 3: ICE connection state stuck at "CHECKING"
**Cause:** TURN server not working or network blocking UDP/TCP
**Solution:**
- Verify TURN server credentials are correct
- Try on a less restrictive network
- Consider setting up your own TURN server (coturn)

#### Issue 4: Different "Your ID" on both apps
**This is NORMAL and EXPECTED!** Each device has its own unique User ID. The connection is established using the **Call ID** (room name), not the User IDs.

### Network Requirements

For WebRTC to work, you need:

1. **Signaling Server Access:**
   - Both devices must be able to reach the signaling server
   - Default port: 8001
   - Protocol: WebSocket (ws://)

2. **ICE Connectivity:**
   - STUN server access (UDP port 19302)
   - TURN server access (TCP/UDP ports 80, 443, 3478)
   - Firewall must allow WebRTC traffic

3. **Same Call ID:**
   - Both users must enter the EXACT same Call ID
   - Call IDs are case-sensitive

### Advanced Debugging

#### Check Signaling Server Logs

The signaling server logs show the connection flow:
```
✅ User abc12345 joined room test-room-123 (1/2) - INITIATOR
📢 Notified def67890 that abc12345 joined
📨 [abc12345] → offer
📤 Relayed offer from abc12345 to def67890
📨 [def67890] → answer
📤 Relayed answer from def67890 to abc12345
```

#### Test TURN Server Connectivity

Use this online tool to test TURN server:
https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/

Enter the TURN server details:
- URI: `turn:a.relay.metered.ca:80`
- Username: `87e69f8c0c87b0fc5e056a36`
- Password: `sBP6FRtpEfj3MgDL`

You should see "relay" type candidates if TURN is working.

### Next Steps

1. **Test the updated code** - The fixes should resolve most connection issues
2. **Monitor the logs** - Use logcat to see exactly where the connection fails
3. **Try different networks** - Test on WiFi, mobile data, and different locations
4. **Consider your own TURN server** - For production, use your own TURN server (coturn)

### Production Recommendations

For a production deployment:

1. **Set up your own TURN server** using coturn
2. **Use WSS (secure WebSocket)** instead of WS
3. **Implement connection quality monitoring**
4. **Add reconnection logic** for dropped connections
5. **Use environment variables** for server configuration
6. **Add analytics** to track connection success rates

### Understanding "Your ID"

The "Your ID" shown in the app is:
- A unique identifier for each device/app installation
- Generated once and stored persistently
- Used for signaling message routing
- **NOT** the Call ID (room name)

**Example:**
- User 1 "Your ID": `abc12345-6789-...`
- User 2 "Your ID": `def67890-1234-...`
- Both use Call ID: `test-room-123` to connect

The different User IDs are **correct and expected**. The Call ID is what connects them.
