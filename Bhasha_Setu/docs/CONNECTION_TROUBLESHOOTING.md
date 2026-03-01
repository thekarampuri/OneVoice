# Connection Troubleshooting Guide

## Issue: No Logs on Server & Devices Not Connecting

You reported that when clicking "Start Call", no logs appear on the command prompt and devices don't connect.

### Root Cause
The Android app is pre-configured with a default Server URL: `192.168.1.10:8001`.
- If your computer's IP address is **NOT** `192.168.1.10`, the app is trying to connect to the wrong address.
- Since the request never reaches your computer, the command prompt shows no logs.

### Solution

#### 1. Find your Computer's IP Address
1. Open a terminal (PowerShell or Command Prompt).
2. Run the command: `ipconfig`
3. Look for **IPv4 Address** under your active adapter (e.g., Wireless LAN adapter Wi-Fi).
   - Example: `192.168.1.45`

#### 2. Update the Android App
1. Open the app on your phones.
2. In the **Server URL** field, replace `192.168.1.10:8001` with your actual IP.
   - Example: `192.168.1.45:8001`
3. Ensure both devices use the **EXACT SAME** Server URL and **Call ID**.

#### 3. Run the Correct Server
I have consolidated the server files. You should now run:
```bash
cd signaling-server
python main.py
```
(This `main.py` is the improved version I renamed for you).

### Firewall Note
If you still see no logs after entering the correct IP:
- Ensure your **Windows Firewall** allows incoming connections on port `8001`.
- Ensure your phones and computer are on the **same Wi-Fi network**.
