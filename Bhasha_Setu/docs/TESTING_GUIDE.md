# Quick Test Guide - WebRTC Connection

## Prerequisites
- Signaling server running on accessible IP
- Two Android devices with the app installed
- Both devices can reach the signaling server

## Step-by-Step Testing

### 1. Start Signaling Server

```bash
cd signaling-server
python main.py
```

You should see:
```
============================================================
WebRTC Signaling Server
============================================================
Mode: SDP/ICE relay only (no media)
Rooms: 1-to-1 (max 2 users)
Host: 0.0.0.0
Port: 8001
============================================================
```

### 2. Find Server IP Address

**On Windows:**
```bash
ipconfig
```
Look for "IPv4 Address" (e.g., `192.168.1.100`)

**On Linux/Mac:**
```bash
ifconfig
```
Look for "inet" address

### 3. Configure User 1 (Initiator)

1. Open app on Device 1
2. Note the "Your ID" (just for reference)
3. Enter:
   - **Server URL:** `192.168.1.100:8001` (use your actual IP)
   - **Call ID:** `test-call-001`
4. Click **"Start Call"**
5. Wait for "Calling..." status

### 4. Configure User 2 (Callee)

1. Open app on Device 2
2. Note the "Your ID" (will be different from User 1 - THIS IS NORMAL)
3. Enter:
   - **Server URL:** `192.168.1.100:8001` (SAME as User 1)
   - **Call ID:** `test-call-001` (SAME as User 1)
4. Click **"Start Call"**
5. Connection should establish within 5-10 seconds

## Expected Behavior

### User 1 Timeline:
1. Click "Start Call" → Status: "Calling..."
2. User 2 joins → Status: "Connecting..."
3. Connection established → Status: "Connected"
4. Can now talk with User 2

### User 2 Timeline:
1. Click "Start Call" → Status: "Calling..."
2. Receives offer from User 1 → Status: "Connecting..."
3. Connection established → Status: "Connected"
4. Can now talk with User 1

## Signaling Server Logs

You should see this sequence:

```
[HH:MM:SS] ✅ User abc12345 joined room test-call-001 (1/2) - INITIATOR
[HH:MM:SS] ✅ User def67890 joined room test-call-001 (2/2) - CALLEE
[HH:MM:SS] 📢 Notified abc12345 that def67890 joined
[HH:MM:SS] 📨 [abc12345] → offer
[HH:MM:SS] 📤 Relayed offer from abc12345 to def67890
[HH:MM:SS] 📨 [def67890] → answer
[HH:MM:SS] 📤 Relayed answer from def67890 to abc12345
[HH:MM:SS] 📨 [abc12345] → ice-candidate
[HH:MM:SS] 📤 Relayed ice-candidate from abc12345 to def67890
[HH:MM:SS] 📨 [def67890] → ice-candidate
[HH:MM:SS] 📤 Relayed ice-candidate from def67890 to abc12345
```

## Android Logcat Monitoring

### Filter for WebRTC logs:
```bash
adb logcat | grep -E "WebRtcClient|SignalingClient|CallRepository"
```

### Successful connection logs:
```
WebRtcClient: ✅ WebRTC initialized
WebRtcClient: ✅ PeerConnection created
SignalingClient: ✅ WebSocket connected to: ws://192.168.1.100:8001/ws/test-call-001/...
CallRepository: 👤 Peer joined: ...
CallRepository: 📞 We're the INITIATOR (joined first) - creating offer
WebRtcClient: ✅ Offer created successfully
WebRtcClient: ✅ Local description set (offer)
WebRtcClient: 🧊 ICE candidate generated
WebRtcClient: 🔌 ICE connection state: CHECKING
WebRtcClient: 🔌 ICE connection state: CONNECTED
WebRtcClient: ✅ ICE connection CONNECTED successfully
CallRepository: ✅ Call connected!
```

## Troubleshooting

### Issue: "Connection timeout after 30 seconds"
**Possible causes:**
- Server URL is incorrect
- Devices can't reach the signaling server
- Firewall blocking WebSocket connections

**Solutions:**
- Verify server IP with `ipconfig` or `ifconfig`
- Ping the server from each device
- Disable firewall temporarily for testing
- Ensure all devices are on the same network

### Issue: "Room full"
**Cause:** More than 2 users in the same room

**Solution:** Use a different Call ID

### Issue: Connection stuck at "Connecting..."
**Possible causes:**
- NAT/firewall blocking WebRTC traffic
- TURN server not accessible
- Network restrictions

**Solutions:**
- Try on mobile data instead of WiFi
- Check if UDP ports are blocked
- Verify TURN server is working (see TROUBLESHOOTING.md)

### Issue: Different "Your ID" on both apps
**This is NORMAL!** Each device has a unique User ID. The Call ID is what connects them.

## Quick Checklist

- [ ] Signaling server is running
- [ ] Server IP is correct and accessible
- [ ] Both devices use the SAME Server URL
- [ ] Both devices use the SAME Call ID
- [ ] Microphone permission granted on both devices
- [ ] Both devices are on the same network (or can reach the server)
- [ ] No more than 2 users in the same room

## Network Requirements

### Ports that need to be accessible:
- **8001** - Signaling server (WebSocket)
- **19302** - STUN server (UDP)
- **80, 443, 3478** - TURN server (TCP/UDP)

### Firewall rules (if needed):
```bash
# Allow signaling server
sudo ufw allow 8001/tcp

# Allow WebRTC traffic
sudo ufw allow 19302/udp
sudo ufw allow 3478/tcp
sudo ufw allow 3478/udp
```

## Testing on Same Device (Emulator + Physical)

You can test with:
- **Device 1:** Android Emulator
- **Device 2:** Physical Android device

**Important:** Use `10.0.2.2` instead of `localhost` for the emulator to reach the host machine.

**Example:**
- Emulator Server URL: `10.0.2.2:8001`
- Physical device Server URL: `192.168.1.100:8001`

## Success Criteria

✅ Both users can join the same room
✅ Connection establishes within 10 seconds
✅ Status shows "Connected" on both devices
✅ Audio is transmitted bidirectionally
✅ Mute button works
✅ End call button disconnects properly

## Next Steps After Successful Test

1. Test on different networks (WiFi, mobile data)
2. Test with devices on different networks (requires TURN)
3. Test call quality and latency
4. Test reconnection after network interruption
5. Test with multiple concurrent rooms
