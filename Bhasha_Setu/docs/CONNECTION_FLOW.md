# WebRTC Connection Flow - Bhasha Setu

## Overview
This document explains the complete connection flow between two users in the Bhasha Setu app.

## Architecture Diagram

```
┌─────────────────────┐                    ┌─────────────────────┐
│   User 1 (Device)   │                    │   User 2 (Device)   │
│  ID: abc12345...    │                    │  ID: def67890...    │
└──────────┬──────────┘                    └──────────┬──────────┘
           │                                          │
           │ 1. Connect WS                            │
           │    /ws/room-123/abc12345                 │
           ▼                                          │
┌─────────────────────────────────────────────────────┐
│         Signaling Server (Port 8001)                │
│         Room: room-123                              │
│         Users: [abc12345, def67890]                 │
└─────────────────────────────────────────────────────┘
           │                                          │
           │                            2. Connect WS │
           │                /ws/room-123/def67890 ◄───┘
           │                                          │
           │ 3. peer-joined event                     │
           │    (User 2 joined)                       │
           ├─────────────────────────────────────────►│
           │                                          │
           │ 4. Create Offer (SDP)                    │
           ├─────────────────────────────────────────►│
           │                                          │
           │                       5. Create Answer   │
           │◄─────────────────────────────────────────┤
           │                                          │
           │ 6. Exchange ICE Candidates               │
           │◄────────────────────────────────────────►│
           │                                          │
           │                                          │
           ▼                                          ▼
┌─────────────────────┐                    ┌─────────────────────┐
│   STUN Server       │                    │   TURN Server       │
│ (Google Public)     │                    │  (metered.ca)       │
│ Port: 19302         │                    │  Ports: 80, 443     │
└─────────────────────┘                    └─────────────────────┘
           │                                          │
           │ 7. NAT Traversal / Relay                 │
           │◄────────────────────────────────────────►│
           │                                          │
           ▼                                          ▼
┌─────────────────────────────────────────────────────┐
│         Direct P2P Audio Connection                 │
│         (or via TURN relay if needed)               │
└─────────────────────────────────────────────────────┘
```

## Detailed Step-by-Step Flow

### Phase 1: Signaling Connection

#### Step 1: User 1 Joins Room
```
User 1 → Signaling Server
  WebSocket: ws://server:8001/ws/room-123/abc12345
  
Server Response:
  - Accept connection
  - Create room "room-123"
  - Add User 1 as INITIATOR
  - Wait for second user
```

#### Step 2: User 2 Joins Room
```
User 2 → Signaling Server
  WebSocket: ws://server:8001/ws/room-123/def67890
  
Server Response:
  - Accept connection
  - Add User 2 to room "room-123"
  - Mark User 2 as CALLEE
  - Send "peer-joined" to User 1
```

#### Step 3: Peer Joined Notification
```
Signaling Server → User 1
  {
    "type": "peer-joined",
    "callId": "room-123",
    "peerId": "def67890"
  }

User 1 Action:
  - Recognize as INITIATOR
  - Wait 500ms (ensure peer is ready)
  - Create WebRTC offer
```

### Phase 2: WebRTC Negotiation

#### Step 4: Offer Exchange
```
User 1 → Signaling Server → User 2
  {
    "type": "offer",
    "callId": "room-123",
    "sdp": "v=0\r\no=- ... (SDP offer)"
  }

User 2 Action:
  - Set remote description (offer)
  - Create WebRTC answer
```

#### Step 5: Answer Exchange
```
User 2 → Signaling Server → User 1
  {
    "type": "answer",
    "callId": "room-123",
    "sdp": "v=0\r\no=- ... (SDP answer)"
  }

User 1 Action:
  - Set remote description (answer)
  - Start ICE candidate gathering
```

#### Step 6: ICE Candidate Exchange
```
User 1 ↔ Signaling Server ↔ User 2
  {
    "type": "ice-candidate",
    "callId": "room-123",
    "candidate": {
      "candidate": "candidate:...",
      "sdpMid": "0",
      "sdpMLineIndex": 0
    }
  }

Both Users:
  - Exchange multiple ICE candidates
  - Test connectivity paths
  - Find best connection route
```

### Phase 3: Connection Establishment

#### Step 7: ICE Connection States

```
State Progression:
  NEW → CHECKING → CONNECTED

User 1 & User 2:
  1. NEW: Initial state
  2. CHECKING: Testing ICE candidates
     - Try direct connection (STUN)
     - Try relay connection (TURN)
     - Test multiple paths
  3. CONNECTED: Best path found
     - Audio starts flowing
     - Connection timeout cancelled
```

## Connection Scenarios

### Scenario A: Direct Connection (Best Case)
```
User 1 ←──────────────────────────────────────────────→ User 2
         Direct P2P connection via STUN
         Low latency, best quality
```

**When this works:**
- Both users on same local network
- No restrictive firewalls
- NAT allows direct connections

### Scenario B: TURN Relay (Restrictive Networks)
```
User 1 ←────────→ TURN Server ←────────→ User 2
                  (metered.ca)
         Relayed connection
         Higher latency, still works
```

**When this is needed:**
- Users on different networks
- Symmetric NAT
- Corporate firewalls
- Mobile networks with restrictions

### Scenario C: Connection Failure
```
User 1  ✗ ✗ ✗ ✗ ✗ ✗ ✗ ✗ ✗ ✗ ✗ ✗  User 2
         All connection attempts fail
         Timeout after 30 seconds
```

**Why this happens:**
- Signaling server unreachable
- All ICE candidates fail
- Firewall blocks all WebRTC traffic
- TURN server unavailable

## Timeline Example

### Successful Connection (Total: ~5-10 seconds)

```
T+0.0s  User 1: Click "Start Call"
T+0.1s  User 1: WebSocket connected
T+0.2s  User 1: Status = "Calling..."

T+2.0s  User 2: Click "Start Call"
T+2.1s  User 2: WebSocket connected
T+2.2s  User 2: Status = "Calling..."

T+2.3s  User 1: Receives "peer-joined"
T+2.3s  User 1: Status = "Connecting..."
T+2.8s  User 1: Creates offer (after 500ms delay)
T+2.9s  User 1: Sends offer to User 2

T+3.0s  User 2: Receives offer
T+3.0s  User 2: Status = "Connecting..."
T+3.1s  User 2: Creates answer
T+3.2s  User 2: Sends answer to User 1

T+3.3s  Both: ICE candidate exchange begins
T+3.3s  Both: ICE state = CHECKING

T+4.5s  Both: Testing connection paths
T+5.0s  Both: Direct connection established
T+5.0s  Both: ICE state = CONNECTED
T+5.0s  Both: Status = "Connected"
T+5.0s  Both: Audio starts flowing ✅
```

### Failed Connection (Total: 30 seconds)

```
T+0.0s  User 1: Click "Start Call"
T+0.1s  User 1: WebSocket connected
T+0.2s  User 1: Status = "Calling..."

T+2.0s  User 2: Click "Start Call"
T+2.1s  User 2: WebSocket connected
T+2.2s  User 2: Status = "Calling..."

T+2.3s  User 1: Receives "peer-joined"
T+2.3s  User 1: Status = "Connecting..."
T+2.8s  User 1: Creates offer
T+2.9s  User 1: Sends offer to User 2

T+3.0s  User 2: Receives offer
T+3.0s  User 2: Status = "Connecting..."
T+3.1s  User 2: Creates answer
T+3.2s  User 2: Sends answer to User 1

T+3.3s  Both: ICE candidate exchange begins
T+3.3s  Both: ICE state = CHECKING

T+5.0s  Both: All direct connections fail
T+8.0s  Both: Trying TURN relay...
T+15.0s Both: TURN relay also fails
T+20.0s Both: ICE state = FAILED

T+30.0s Both: Connection timeout ⏰
T+30.0s Both: Call ended ❌
```

## Key Components

### User ID vs Call ID

```
┌─────────────────────────────────────────────────────┐
│ User ID (Device-Specific)                           │
│ - Generated once per app installation               │
│ - Stored persistently                               │
│ - Used for signaling message routing                │
│ - Example: "abc12345-6789-0123-4567-890abcdef123"   │
│                                                      │
│ User 1 ID: abc12345...                              │
│ User 2 ID: def67890...  ← DIFFERENT (This is OK!)  │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ Call ID (Room Name)                                 │
│ - Entered by both users                             │
│ - Must be IDENTICAL to connect                      │
│ - Case-sensitive                                    │
│ - Example: "room-123"                               │
│                                                      │
│ User 1 Call ID: room-123                            │
│ User 2 Call ID: room-123  ← SAME (Required!)       │
└─────────────────────────────────────────────────────┘
```

## Network Ports

```
┌──────────────────────────────────────────────────────┐
│ Signaling Server                                     │
│ Port: 8001 (WebSocket)                               │
│ Protocol: WS (or WSS in production)                  │
│ Must be accessible from both devices                 │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│ STUN Server (Google)                                 │
│ Port: 19302 (UDP)                                    │
│ Purpose: NAT discovery                               │
│ Free public service                                  │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│ TURN Server (metered.ca)                             │
│ Ports: 80, 443, 3478 (TCP/UDP)                       │
│ Purpose: Relay when direct connection fails          │
│ Free tier available                                  │
└──────────────────────────────────────────────────────┘
```

## State Machine

### Call States

```
┌──────┐
│ IDLE │ Initial state
└───┬──┘
    │ startCall()
    ▼
┌─────────┐
│ CALLING │ Waiting for peer
└────┬────┘
     │ peer-joined
     ▼
┌────────────┐
│ CONNECTING │ WebRTC negotiation
└─────┬──────┘
      │ ICE connected
      ▼
┌───────────┐
│ CONNECTED │ Active call
└─────┬─────┘
      │ endCall() or peer-left
      ▼
┌────────┐
│ ENDED  │ Call finished
└────────┘
```

### ICE Connection States

```
┌─────┐
│ NEW │ Initial
└──┬──┘
   │
   ▼
┌──────────┐
│ CHECKING │ Testing candidates
└────┬─────┘
     │
     ├────────────────┐
     │                │
     ▼                ▼
┌───────────┐    ┌────────┐
│ CONNECTED │    │ FAILED │
└───────────┘    └────────┘
     │                │
     ▼                ▼
┌──────────────┐  ┌────────┐
│ DISCONNECTED │  │ ENDED  │
└──────────────┘  └────────┘
```

## Troubleshooting Flow

```
Connection Failed?
       │
       ▼
┌─────────────────────────────────┐
│ Check Signaling Server Logs     │
│ Are both users connected?        │
└────────┬────────────────────────┘
         │
    ┌────┴────┐
    │   YES   │
    └────┬────┘
         │
         ▼
┌─────────────────────────────────┐
│ Check Android Logcat             │
│ What is ICE connection state?    │
└────────┬────────────────────────┘
         │
    ┌────┴────────────────┐
    │                     │
    ▼                     ▼
┌─────────┐         ┌──────────┐
│ CHECKING│         │  FAILED  │
└────┬────┘         └─────┬────┘
     │                    │
     ▼                    ▼
┌─────────────────┐  ┌──────────────────┐
│ Wait longer     │  │ Network/Firewall │
│ (up to 30s)     │  │ blocking traffic │
└─────────────────┘  └──────────────────┘
```

## Summary

The connection flow involves:
1. **Signaling** - WebSocket connection to coordinate peers
2. **Negotiation** - SDP offer/answer exchange
3. **ICE** - Finding the best connection path
4. **Connection** - Establishing audio stream

Key points:
- Different User IDs are **normal**
- Same Call ID is **required**
- TURN servers improve success rate
- 30-second timeout prevents hanging
- Detailed logs help debugging
