# WebRTC Signaling Server

Simple, robust WebSocket server for WebRTC signaling between Android devices.

## Features
- 1-to-1 WebRTC signaling
- WebSocket keepalive (prevents timeouts)
- Automatic room management
- Robust error handling

## Setup

1. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

2. Run the server:
   ```bash
   python main.py
   ```
   Server listens on `0.0.0.0:8001`.

## Android Configuration

In the Android app:
- **Server URL**: `Your-IP:8001` (e.g. `192.168.1.100:8001`)
- **Call ID**: Any string (must be same on both devices)
