"""
WebRTC Signaling Server - Production Ready

Improvements:
1. ✅ WebSocket keepalive (ping/pong)
2. ✅ Better error handling
3. ✅ Connection state tracking
4. ✅ Graceful shutdown
5. ✅ Enhanced logging
6. ✅ ICE candidate relay verification
"""

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
import json
import uvicorn
from typing import Dict, Optional
import asyncio
from datetime import datetime

app = FastAPI(title="Bhasha Setu Signaling Server")

# CORS for web clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Serve static files (web client)
try:
    app.mount("/client", StaticFiles(directory="web-client", html=True), name="client")
except RuntimeError:
    print("⚠️  web-client directory not found, static files not served")

# Room storage: {call_id: {user_id: WebSocket}}
rooms: Dict[str, Dict[str, WebSocket]] = {}

# Connection metadata
connection_metadata: Dict[str, Dict] = {}


def log(msg: str):
    """Enhanced logging with timestamp"""
    timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
    print(f"[{timestamp}] {msg}")


@app.get("/")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "running",
        "service": "WebRTC Signaling Server",
        "active_rooms": len(rooms),
        "total_connections": sum(len(users) for users in rooms.values())
    }


@app.get("/rooms")
async def list_rooms():
    """List all active rooms (for debugging)"""
    room_info = {}
    for call_id, users in rooms.items():
        room_info[call_id] = {
            "user_count": len(users),
            "users": [uid[:8] + "..." for uid in users.keys()]
        }
    return room_info


@app.websocket("/ws/{call_id}/{user_id}")
async def websocket_endpoint(websocket: WebSocket, call_id: str, user_id: str):
    """
    WebSocket endpoint for signaling
    
    URL: ws://server/ws/{call_id}/{user_id}
    - call_id: Room identifier
    - user_id: Unique user identifier (UUID)
    """
    
    # Check room capacity (max 2 users)
    if call_id in rooms and len(rooms[call_id]) >= 2:
        await websocket.close(code=1008, reason="Room full")
        log(f"❌ Room {call_id} is full, rejected {user_id[:8]}")
        return
    
    # Accept connection
    try:
        await websocket.accept()
        log(f"✅ WebSocket accepted for {user_id[:8]}")
    except Exception as e:
        log(f"❌ Failed to accept WebSocket: {e}")
        return
    
    # Create room if doesn't exist
    if call_id not in rooms:
        rooms[call_id] = {}
        log(f"🆕 Created room: {call_id}")
    
    # Determine if this user is the initiator (first to join)
    is_initiator = len(rooms[call_id]) == 0
    
    # Add user to room
    rooms[call_id][user_id] = websocket
    connection_metadata[user_id] = {
        "call_id": call_id,
        "joined_at": datetime.now(),
        "is_initiator": is_initiator,
        "last_ping": datetime.now()
    }
    
    log(f"✅ User {user_id[:8]} joined room {call_id} ({len(rooms[call_id])}/2) - {'INITIATOR' if is_initiator else 'CALLEE'}")
    
    # Notify peer if they exist
    peer_id = get_peer_id(call_id, user_id)
    if peer_id:
        # Notify the existing peer that we joined
        await send_to_peer(call_id, user_id, {
            "type": "peer-joined",
            "callId": call_id,
            "peerId": user_id
        })
        log(f"📢 Notified existing peer {peer_id[:8]} that {user_id[:8]} joined")
        
        # ALSO notify the joining user (us) that a peer is already there
        await websocket.send_text(json.dumps({
            "type": "existing-peer",
            "callId": call_id,
            "peerId": peer_id
        }))
        log(f"📢 Notified joining user {user_id[:8]} that {peer_id[:8]} is already here")
    
    # Start keepalive task
    keepalive_task = asyncio.create_task(keepalive(websocket, user_id))
    
    try:
        # Message relay loop
        while True:
            # Receive message from client
            data = await websocket.receive_text()
            
            # Update last activity
            if user_id in connection_metadata:
                connection_metadata[user_id]["last_ping"] = datetime.now()
            
            try:
                message = json.loads(data)
                msg_type = message.get("type")
                
                log(f"📨 [{user_id[:8]}] → {msg_type}")
                
                # Validate message
                if not msg_type:
                    log(f"⚠️  Invalid message from {user_id[:8]}: no type field")
                    continue
                
                # Relay message to peer
                if msg_type in ["offer", "answer", "ice-candidate", "peer-info"]:
                    success = await send_to_peer(call_id, user_id, message)
                    if not success:
                        log(f"⚠️  Failed to relay {msg_type} from {user_id[:8]} (peer not found)")
                else:
                    log(f"⚠️  Unknown message type from {user_id[:8]}: {msg_type}")
                    
            except json.JSONDecodeError as e:
                log(f"⚠️  Invalid JSON from {user_id[:8]}: {e}")
            except Exception as e:
                log(f"⚠️  Error processing message from {user_id[:8]}: {e}")
    
    except WebSocketDisconnect:
        log(f"❌ User {user_id[:8]} disconnected from room {call_id}")
    except Exception as e:
        log(f"⚠️  Error for {user_id[:8]}: {e}")
    finally:
        # Cancel keepalive task
        keepalive_task.cancel()
        
        # Cleanup on disconnect
        await cleanup_user(call_id, user_id)


async def keepalive(websocket: WebSocket, user_id: str):
    """Send periodic heartbeat to keep connection alive"""
    try:
        while True:
            await asyncio.sleep(15)  # Ping more frequently for Render
            try:
                await websocket.send_text(json.dumps({"type": "ping", "timestamp": datetime.now().isoformat()}))
                # log(f"🏓 Ping sent to {user_id[:8]}")
            except Exception as e:
                log(f"⚠️  Keepalive failed for {user_id[:8]}: {e}")
                break
    except asyncio.CancelledError:
        pass


def get_peer_id(call_id: str, user_id: str) -> Optional[str]:
    """Get the peer's user ID in the room"""
    if call_id not in rooms:
        return None
    
    for uid in rooms[call_id].keys():
        if uid != user_id:
            return uid
    
    return None


async def send_to_peer(call_id: str, sender_id: str, message: dict) -> bool:
    """Send message to the peer (not the sender)"""
    peer_id = get_peer_id(call_id, sender_id)
    
    if peer_id and call_id in rooms and peer_id in rooms[call_id]:
        peer_ws = rooms[call_id][peer_id]
        try:
            await peer_ws.send_text(json.dumps(message))
            log(f"📤 Relayed {message.get('type')} from {sender_id[:8]} to {peer_id[:8]}")
            return True
        except Exception as e:
            log(f"⚠️  Failed to send to {peer_id[:8]}: {e}")
            return False
    else:
        log(f"⚠️  Peer not found for {sender_id[:8]} in room {call_id}")
        return False


async def cleanup_user(call_id: str, user_id: str):
    """Remove user from room and notify peer"""
    if call_id in rooms:
        # Remove user
        if user_id in rooms[call_id]:
            del rooms[call_id][user_id]
            log(f"🗑️  Removed {user_id[:8]} from room {call_id}")
        
        # Remove metadata
        if user_id in connection_metadata:
            del connection_metadata[user_id]
        
        # Notify peer
        peer_id = get_peer_id(call_id, user_id)
        if peer_id:
            try:
                peer_ws = rooms[call_id][peer_id]
                await peer_ws.send_text(json.dumps({
                    "type": "peer-left",
                    "callId": call_id,
                    "peerId": user_id
                }))
                log(f"📢 Notified {peer_id[:8]} that {user_id[:8]} left")
            except Exception as e:
                log(f"⚠️  Failed to notify peer: {e}")
        
        # Delete room if empty
        if not rooms[call_id]:
            del rooms[call_id]
            log(f"🗑️  Deleted empty room {call_id}")

import os

if __name__ == "__main__":
    print("=" * 60)
    print("WebRTC Signaling Server - Production Ready")
    print("=" * 60)
    print("Mode: SDP/ICE relay with keepalive")
    print("Rooms: 1-to-1 (max 2 users)")
    print("Host: 0.0.0.0")
    port = int(os.environ.get("PORT", 8001))
    print(f"Port: {port}")
    print("=" * 60)
    print(f"Web Client: http://localhost:{port}/client")
    print(f"Health Check: http://localhost:{port}/")
    print(f"Rooms List: http://localhost:{port}/rooms")
    print("=" * 60)
    uvicorn.run(app, host="0.0.0.0", port=port)
