# WebRTC Browser Client - Quick Start Guide

## 🚀 Quick Start (2 minutes)

### Step 1: Start the Signaling Server
```bash
cd signaling-server
python main_improved.py
```

You should see:
```
============================================================
WebRTC Signaling Server - Production Ready
============================================================
Mode: SDP/ICE relay with keepalive
Rooms: 1-to-1 (max 2 users)
Host: 0.0.0.0
Port: 8001
============================================================
Web Client: http://localhost:8001/client
Health Check: http://localhost:8001/
Rooms List: http://localhost:8001/rooms
============================================================
```

### Step 2: Open Browser Client (User 1)
1. Open browser: `http://localhost:8001/client`
2. Server URL: `localhost:8001` (default)
3. Call ID: `test-room-123`
4. Click **"Start Call"**
5. Allow microphone access
6. Wait for status: "Connected to server, waiting for peer..."

### Step 3: Open Second Browser Tab (User 2)
1. Open NEW TAB: `http://localhost:8001/client`
2. Server URL: `localhost:8001` (same)
3. Call ID: `test-room-123` (SAME as User 1)
4. Click **"Start Call"**
5. Allow microphone access
6. Connection should establish in 3-5 seconds

### Step 4: Verify Connection
Both tabs should show:
- Status: **"Connected"** (green)
- Logs showing ICE connection established
- Mute button enabled
- You can hear each other speak

---

## 📋 What to Expect

### Successful Connection Timeline

**User 1 (Initiator):**
```
[00:00:00] Client initialized
[00:00:01] Starting call...
[00:00:02] Requesting microphone access...
[00:00:03] ✅ Microphone access granted
[00:00:03] Creating peer connection...
[00:00:03] ✅ Peer connection created
[00:00:03] Adding local tracks...
[00:00:03] ✅ All local tracks added
[00:00:04] Connecting to signaling server...
[00:00:04] ✅ Connected to signaling server
[00:00:05] Status: Connected to server, waiting for peer...
```

**User 2 joins:**
```
[00:00:10] 👤 Peer joined: def67890...
[00:00:10] We are the INITIATOR - creating offer
[00:00:11] Creating SDP offer...
[00:00:11] ✅ Local description set (offer)
[00:00:11] 📤 Sent: offer
[00:00:12] 🧊 ICE candidate generated: host
[00:00:12] 📤 Sent: ice-candidate
[00:00:13] 📨 Received: answer
[00:00:13] ✅ Remote description set (answer)
[00:00:14] 📨 Received: ice-candidate
[00:00:14] ✅ ICE candidate added
[00:00:15] 🔌 ICE connection state: checking
[00:00:16] 🔌 ICE connection state: connected
[00:00:16] ✅ ICE connection established!
[00:00:16] 🎵 Remote track received: audio
[00:00:16] Status: Connected
```

**User 2 (Callee):**
```
[00:00:10] Starting call...
[00:00:11] ✅ Microphone access granted
[00:00:11] ✅ Peer connection created
[00:00:11] ✅ All local tracks added
[00:00:12] ✅ Connected to signaling server
[00:00:12] 📨 Received: offer
[00:00:12] 📞 Received offer from peer
[00:00:12] We are the CALLEE - creating answer
[00:00:13] ✅ Remote description set (offer)
[00:00:13] Creating SDP answer...
[00:00:13] ✅ Local description set (answer)
[00:00:13] 📤 Sent: answer
[00:00:14] 🧊 ICE candidate generated: host
[00:00:14] 📤 Sent: ice-candidate
[00:00:15] 📨 Received: ice-candidate
[00:00:15] ✅ ICE candidate added
[00:00:16] 🔌 ICE connection state: checking
[00:00:17] 🔌 ICE connection state: connected
[00:00:17] ✅ ICE connection established!
[00:00:17] 🎵 Remote track received: audio
[00:00:17] Status: Connected
```

---

## 🎛️ Controls

### Mute Button
- Click to mute/unmute your microphone
- 🔊 Unmuted (default)
- 🔇 Muted

### End Call Button
- Closes the connection
- Stops microphone
- Disconnects from signaling server
- Resets to ready state

---

## 🐛 Troubleshooting

### Issue: "Microphone access denied"
**Solution:** 
- Click the lock icon in browser address bar
- Allow microphone access
- Refresh the page

### Issue: "Connection timeout"
**Solution:**
- Verify signaling server is running
- Check server URL is correct
- Ensure both users use the SAME Call ID

### Issue: "Room full"
**Solution:**
- Only 2 users can join the same room
- Use a different Call ID
- Or wait for one user to leave

### Issue: Connection stuck at "Connecting..."
**Solution:**
- Check browser console for errors (F12)
- Verify STUN/TURN servers are accessible
- Try a different network (WiFi vs mobile data)

### Issue: No audio heard
**Solution:**
- Check volume is not muted
- Verify microphone is working (test in other apps)
- Check browser console for "Remote track received" message
- Ensure both users have granted microphone permission

---

## 📊 Server Endpoints

### Web Client
`http://localhost:8001/client`
- Main web interface

### Health Check
`http://localhost:8001/`
- Returns server status and active room count

### Rooms List
`http://localhost:8001/rooms`
- Shows all active rooms and users (for debugging)

---

## 🔍 Debugging

### Browser Console (F12)
Open developer tools to see detailed logs:
- Connection state changes
- ICE candidate generation
- SDP offer/answer exchange
- Error messages

### Server Terminal
Monitor signaling server logs:
- User join/leave events
- Message relay (offer, answer, ICE)
- Connection errors

---

## 🌐 Testing Across Devices

### Same Network (LAN)
**User 1:** `http://192.168.1.100:8001/client`
**User 2:** `http://192.168.1.100:8001/client`

Replace `192.168.1.100` with your server's IP address.

### Different Networks (Internet)
Use ngrok or similar tunneling service:

```bash
# Terminal 1: Start server
python main_improved.py

# Terminal 2: Start ngrok
ngrok http 8001
```

Use the ngrok URL (e.g., `abc123.ngrok.io`) in the browser client.

---

## 📝 Call ID Best Practices

### Good Call IDs:
- ✅ `meeting-room-1`
- ✅ `alice-bob-call`
- ✅ `project-discussion-2024`
- ✅ `test-room-123`

### Bad Call IDs:
- ❌ Empty string
- ❌ Spaces only
- ❌ Special characters that might break URLs

### Security Note:
Call IDs are not encrypted or authenticated. Anyone who knows the Call ID can join the room. For production, implement proper authentication.

---

## 🎯 Success Criteria

✅ Both users can join the same room
✅ Connection establishes within 5-10 seconds
✅ Status shows "Connected" (green)
✅ Both users can hear each other
✅ Mute button works
✅ End call button disconnects properly
✅ No errors in browser console
✅ Server logs show successful message relay

---

## 🔄 Common Workflows

### Testing Locally (Same Computer)
1. Open two browser tabs
2. Use `localhost:8001` as server URL
3. Use same Call ID in both tabs
4. Start call in both tabs
5. Connection should establish

### Testing on Different Computers (Same Network)
1. Find server IP: `ipconfig` (Windows) or `ifconfig` (Linux/Mac)
2. User 1: Open `http://<server-ip>:8001/client`
3. User 2: Open `http://<server-ip>:8001/client`
4. Both use same Call ID
5. Start call in both browsers

### Testing on Different Networks
1. Use ngrok or deploy to cloud server
2. Share the public URL with User 2
3. Both use same Call ID
4. Start call in both browsers

---

## 📚 Files Overview

```
web-client/
├── index.html          # Web interface
└── webrtc-client.js    # WebRTC client logic

signaling-server/
├── main.py             # Original server
└── main_improved.py    # Improved server (USE THIS)

docs/
└── WEBRTC_DISCONNECTION_FIX.md  # Root cause analysis
```

---

## 🚨 Important Notes

1. **Microphone Permission:** Required for both users
2. **Same Call ID:** Both users MUST use identical Call ID
3. **Room Limit:** Maximum 2 users per room
4. **Browser Support:** Chrome, Firefox, Edge (latest versions)
5. **HTTPS:** For production, use HTTPS (required for getUserMedia)

---

## ✅ Quick Checklist

Before starting:
- [ ] Signaling server is running
- [ ] Browser supports WebRTC (Chrome/Firefox/Edge)
- [ ] Microphone is connected and working

For each user:
- [ ] Open web client URL
- [ ] Enter server URL
- [ ] Enter Call ID (same for both users)
- [ ] Click "Start Call"
- [ ] Allow microphone access
- [ ] Wait for "Connected" status

---

**Ready to test!** 🎉

If you encounter any issues, check:
1. Browser console (F12) for errors
2. Server terminal for connection logs
3. `docs/WEBRTC_DISCONNECTION_FIX.md` for detailed troubleshooting
