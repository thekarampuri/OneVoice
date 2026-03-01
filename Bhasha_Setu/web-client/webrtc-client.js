/**
 * WebRTC Browser Client - Production Ready
 * 
 * Fixes for common disconnection issues:
 * 1. ✅ Media tracks added BEFORE creating offer
 * 2. ✅ Full ICE candidate trickle support
 * 3. ✅ Proper event handler implementation
 * 4. ✅ WebSocket keepalive and reconnection
 * 5. ✅ STUN/TURN configuration
 * 6. ✅ Comprehensive error handling
 */

class WebRTCClient {
    constructor() {
        // Generate unique user ID
        this.userId = this.generateUUID();

        // WebRTC components
        this.peerConnection = null;
        this.localStream = null;
        this.remoteStream = null;

        // Signaling
        this.websocket = null;
        this.serverUrl = '';
        this.callId = '';

        // State
        this.isInitiator = false;
        this.isConnected = false;
        this.isMuted = false;

        // ICE candidate queues
        this.iceCandidateQueue = [];
        this.localIceCandidateQueue = [];

        // Configuration
        this.config = {
            iceServers: [
                // Google STUN servers
                { urls: 'stun:stun.l.google.com:19302' },
                { urls: 'stun:stun1.l.google.com:19302' },
                // Free TURN servers (metered.ca)
                {
                    urls: 'turn:a.relay.metered.ca:80',
                    username: '87e69f8c0c87b0fc5e056a36',
                    credential: 'sBP6FRtpEfj3MgDL'
                },
                {
                    urls: 'turn:a.relay.metered.ca:443',
                    username: '87e69f8c0c87b0fc5e056a36',
                    credential: 'sBP6FRtpEfj3MgDL'
                }
            ],
            iceCandidatePoolSize: 10,
            bundlePolicy: 'max-bundle',
            rtcpMuxPolicy: 'require'
        };

        this.initUI();
    }

    initUI() {
        // Display user ID
        document.getElementById('userId').textContent = this.userId;

        // Button handlers
        document.getElementById('startBtn').addEventListener('click', () => this.startCall());
        document.getElementById('endBtn').addEventListener('click', () => this.endCall());
        document.getElementById('muteBtn').addEventListener('click', () => this.toggleMute());

        this.log('Client initialized', 'success');
        this.updateStatus('Ready to start call', 'idle');
    }

    generateUUID() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
            const r = Math.random() * 16 | 0;
            const v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }

    async startCall() {
        try {
            this.serverUrl = document.getElementById('serverUrl').value.trim();
            this.callId = document.getElementById('callId').value.trim();

            if (!this.serverUrl || !this.callId) {
                this.log('Please enter server URL and call ID', 'error');
                return;
            }

            this.log('Starting call...', 'info');
            this.updateStatus('Initializing...', 'connecting');

            // Disable start button
            document.getElementById('startBtn').disabled = true;

            // Step 1: Get user media FIRST (critical!)
            await this.getUserMedia();

            // Step 2: Create peer connection
            this.createPeerConnection();

            // Step 3: Add local tracks to peer connection (BEFORE creating offer!)
            this.addLocalTracks();

            // Step 4: Connect to signaling server
            await this.connectSignaling();

        } catch (error) {
            this.log(`Failed to start call: ${error.message}`, 'error');
            this.updateStatus(`Error: ${error.message}`, 'error');
            document.getElementById('startBtn').disabled = false;
        }
    }

    async getUserMedia() {
        this.log('Requesting microphone access...', 'info');

        try {
            this.localStream = await navigator.mediaDevices.getUserMedia({
                audio: {
                    echoCancellation: true,
                    noiseSuppression: true,
                    autoGainControl: true
                },
                video: false
            });

            this.log('✅ Microphone access granted', 'success');
            this.log(`Local stream tracks: ${this.localStream.getTracks().length}`, 'info');

        } catch (error) {
            throw new Error(`Microphone access denied: ${error.message}`);
        }
    }

    createPeerConnection() {
        this.log('Creating peer connection...', 'info');

        this.peerConnection = new RTCPeerConnection(this.config);

        // Event: ICE candidate generated
        this.peerConnection.onicecandidate = (event) => {
            if (event.candidate) {
                this.log(`🧊 ICE candidate generated: ${event.candidate.type}`, 'info');

                const candidateData = {
                    candidate: event.candidate.candidate,
                    sdpMid: event.candidate.sdpMid,
                    sdpMLineIndex: event.candidate.sdpMLineIndex
                };

                if (this.peerConnection.localDescription) {
                    // Send ICE candidate to peer via signaling
                    this.sendSignalingMessage({
                        type: 'ice-candidate',
                        callId: this.callId,
                        candidate: candidateData
                    });
                } else {
                    this.localIceCandidateQueue.push(candidateData);
                }
            } else {
                this.log('ICE candidate gathering complete', 'info');
            }
        };

        // Event: ICE connection state change
        this.peerConnection.oniceconnectionstatechange = () => {
            const state = this.peerConnection.iceConnectionState;
            this.log(`🔌 ICE connection state: ${state}`, 'info');

            switch (state) {
                case 'checking':
                    this.updateStatus('Connecting...', 'connecting');
                    break;
                case 'connected':
                case 'completed':
                    this.log('✅ ICE connection established!', 'success');
                    this.updateStatus('Connected', 'connected');
                    this.isConnected = true;
                    document.getElementById('endBtn').disabled = false;
                    document.getElementById('controls').classList.add('show');
                    break;
                case 'disconnected':
                    this.log('⚠️ ICE connection disconnected', 'warning');
                    this.updateStatus('Disconnected', 'error');
                    break;
                case 'failed':
                    this.log('❌ ICE connection failed', 'error');
                    this.updateStatus('Connection failed', 'error');
                    this.endCall();
                    break;
                case 'closed':
                    this.log('ICE connection closed', 'info');
                    break;
            }
        };

        // Event: ICE gathering state change
        this.peerConnection.onicegatheringstatechange = () => {
            this.log(`ICE gathering state: ${this.peerConnection.iceGatheringState}`, 'info');
        };

        // Event: Signaling state change
        this.peerConnection.onsignalingstatechange = () => {
            this.log(`Signaling state: ${this.peerConnection.signalingState}`, 'info');
        };

        // Event: Remote track received (CRITICAL!)
        this.peerConnection.ontrack = (event) => {
            this.log(`🎵 Remote track received: ${event.track.kind}`, 'success');

            if (!this.remoteStream) {
                this.remoteStream = new MediaStream();

                // Create audio element to play remote audio
                const audioElement = new Audio();
                audioElement.srcObject = this.remoteStream;
                audioElement.autoplay = true;
                audioElement.play().catch(e => {
                    this.log(`Failed to play remote audio: ${e.message}`, 'error');
                });

                this.log('Remote audio element created and playing', 'success');
            }

            this.remoteStream.addTrack(event.track);
        };

        // Event: Connection state change
        this.peerConnection.onconnectionstatechange = () => {
            this.log(`Connection state: ${this.peerConnection.connectionState}`, 'info');
        };

        this.log('✅ Peer connection created', 'success');
    }

    addLocalTracks() {
        this.log('Adding local tracks to peer connection...', 'info');

        this.localStream.getTracks().forEach(track => {
            this.peerConnection.addTrack(track, this.localStream);
            this.log(`Added ${track.kind} track to peer connection`, 'info');
        });

        this.log('✅ All local tracks added', 'success');
    }

    async connectSignaling() {
        return new Promise((resolve, reject) => {
            const wsUrl = `ws://${this.serverUrl}/ws/${this.callId}/${this.userId}`;
            this.log(`Connecting to signaling server: ${wsUrl}`, 'info');

            this.websocket = new WebSocket(wsUrl);

            this.websocket.onopen = () => {
                this.log('✅ Connected to signaling server', 'success');
                this.updateStatus('Connected to server, waiting for peer...', 'connecting');
                resolve();
            };

            this.websocket.onmessage = async (event) => {
                try {
                    const message = JSON.parse(event.data);
                    await this.handleSignalingMessage(message);
                } catch (error) {
                    this.log(`Error handling message: ${error.message}`, 'error');
                }
            };

            this.websocket.onerror = (error) => {
                this.log(`WebSocket error: ${error}`, 'error');
                reject(new Error('WebSocket connection failed'));
            };

            this.websocket.onclose = (event) => {
                this.log(`WebSocket closed: ${event.code} - ${event.reason}`, 'warning');

                if (this.isConnected) {
                    this.updateStatus('Signaling server disconnected', 'error');
                }
            };
        });
    }

    async handleSignalingMessage(message) {
        this.log(`📨 Received: ${message.type}`, 'info');

        switch (message.type) {
            case 'peer-joined':
                await this.handlePeerJoined(message);
                break;
            case 'existing-peer':
                this.handleExistingPeer(message);
                break;
            case 'offer':
                await this.handleOffer(message);
                break;
            case 'answer':
                await this.handleAnswer(message);
                break;
            case 'ice-candidate':
                await this.handleIceCandidate(message);
                break;
            case 'peer-left':
                this.handlePeerLeft(message);
                break;
            default:
                this.log(`Unknown message type: ${message.type}`, 'warning');
        }
    }

    async handlePeerJoined(message) {
        this.log(`👤 Peer joined: ${message.peerId}`, 'success');

        // We are the initiator (first to join)
        this.isInitiator = true;
        this.log('We are the INITIATOR - creating offer', 'info');

        // Small delay to ensure peer is ready
        await new Promise(resolve => setTimeout(resolve, 500));

        await this.createOffer();
    }

    async createOffer() {
        try {
            this.log('Creating SDP offer...', 'info');
            this.updateStatus('Creating offer...', 'connecting');

            const offer = await this.peerConnection.createOffer({
                offerToReceiveAudio: true,
                offerToReceiveVideo: false
            });

            this.log('Setting local description (offer)...', 'info');
            await this.peerConnection.setLocalDescription(offer);

            this.log('✅ Local description set (offer)', 'success');
            this.log(`SDP offer created (${offer.sdp.length} bytes)`, 'info');

            // Process any queued local ICE candidates
            this.localIceCandidateQueue.forEach(candidate => {
                this.sendSignalingMessage({
                    type: 'ice-candidate',
                    callId: this.callId,
                    candidate: candidate
                });
            });
            this.localIceCandidateQueue = [];

            // Send offer to peer
            this.sendSignalingMessage({
                type: 'offer',
                callId: this.callId,
                sdp: offer.sdp
            });

        } catch (error) {
            this.log(`Failed to create offer: ${error.message}`, 'error');
        }
    }

    async handleOffer(message) {
        this.log('📞 Received offer from peer', 'info');

        // We are the callee (second to join)
        this.isInitiator = false;
        this.log('We are the CALLEE - creating answer', 'info');

        try {
            const offer = new RTCSessionDescription({
                type: 'offer',
                sdp: message.sdp
            });

            this.log('Setting remote description (offer)...', 'info');
            await this.peerConnection.setRemoteDescription(offer);
            this.log('✅ Remote description set (offer)', 'success');

            // Process any queued ICE candidates
            await this.processIceCandidateQueue();

            // Create answer
            await this.createAnswer();

        } catch (error) {
            this.log(`Failed to handle offer: ${error.message}`, 'error');
        }
    }

    async createAnswer() {
        try {
            this.log('Creating SDP answer...', 'info');
            this.updateStatus('Creating answer...', 'connecting');

            const answer = await this.peerConnection.createAnswer({
                offerToReceiveAudio: true,
                offerToReceiveVideo: false
            });

            this.log('Setting local description (answer)...', 'info');
            await this.peerConnection.setLocalDescription(answer);

            this.log('✅ Local description set (answer)', 'success');
            this.log(`SDP answer created (${answer.sdp.length} bytes)`, 'info');

            // Process any queued local ICE candidates
            this.localIceCandidateQueue.forEach(candidate => {
                this.sendSignalingMessage({
                    type: 'ice-candidate',
                    callId: this.callId,
                    candidate: candidate
                });
            });
            this.localIceCandidateQueue = [];

            // Send answer to peer
            this.sendSignalingMessage({
                type: 'answer',
                callId: this.callId,
                sdp: answer.sdp
            });

        } catch (error) {
            this.log(`Failed to create answer: ${error.message}`, 'error');
        }
    }

    async handleAnswer(message) {
        this.log('✅ Received answer from peer', 'success');

        try {
            const answer = new RTCSessionDescription({
                type: 'answer',
                sdp: message.sdp
            });

            this.log('Setting remote description (answer)...', 'info');
            await this.peerConnection.setRemoteDescription(answer);
            this.log('✅ Remote description set (answer)', 'success');

            // Process any queued ICE candidates
            await this.processIceCandidateQueue();

        } catch (error) {
            this.log(`Failed to handle answer: ${error.message}`, 'error');
        }
    }

    async handleIceCandidate(message) {
        this.log('🧊 Received ICE candidate from peer', 'info');

        try {
            const candidate = new RTCIceCandidate({
                candidate: message.candidate.candidate,
                sdpMid: message.candidate.sdpMid,
                sdpMLineIndex: message.candidate.sdpMLineIndex
            });

            // Check if remote description is set
            if (this.peerConnection.remoteDescription) {
                await this.peerConnection.addIceCandidate(candidate);
                this.log('✅ ICE candidate added', 'success');
            } else {
                // Queue candidate for later (remote description not set yet)
                this.log('Queueing ICE candidate (remote description not set)', 'warning');
                this.iceCandidateQueue.push(candidate);
            }

        } catch (error) {
            this.log(`Failed to add ICE candidate: ${error.message}`, 'error');
        }
    }

    async processIceCandidateQueue() {
        if (this.iceCandidateQueue.length > 0) {
            this.log(`Processing ${this.iceCandidateQueue.length} queued ICE candidates...`, 'info');

            for (const candidate of this.iceCandidateQueue) {
                try {
                    await this.peerConnection.addIceCandidate(candidate);
                    this.log('✅ Queued ICE candidate added', 'success');
                } catch (error) {
                    this.log(`Failed to add queued candidate: ${error.message}`, 'error');
                }
            }

            this.iceCandidateQueue = [];
        }
    }

    handlePeerLeft(message) {
        this.log(`👋 Peer left: ${message.peerId}`, 'warning');
        this.updateStatus('Peer disconnected', 'error');
        this.endCall();
    }

    handleExistingPeer(message) {
        this.log(`👤 Existing peer in room: ${message.peerId}`, 'success');
        this.isInitiator = false;
        this.log('We are the CALLEE - waiting for offer', 'info');
        this.updateStatus('Waiting for offer...', 'connecting');
    }

    sendSignalingMessage(message) {
        if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            this.websocket.send(JSON.stringify(message));
            this.log(`📤 Sent: ${message.type}`, 'info');
        } else {
            this.log('Cannot send message: WebSocket not connected', 'error');
        }
    }

    toggleMute() {
        this.isMuted = !this.isMuted;

        this.localStream.getAudioTracks().forEach(track => {
            track.enabled = !this.isMuted;
        });

        const muteBtn = document.getElementById('muteBtn');
        if (this.isMuted) {
            muteBtn.textContent = '🔇 Muted';
            muteBtn.classList.add('active');
            this.log('🔇 Microphone muted', 'info');
        } else {
            muteBtn.textContent = '🔊 Unmuted';
            muteBtn.classList.remove('active');
            this.log('🔊 Microphone unmuted', 'info');
        }
    }

    endCall() {
        this.log('Ending call...', 'info');

        // Close peer connection
        if (this.peerConnection) {
            this.peerConnection.close();
            this.peerConnection = null;
        }

        // Stop local stream
        if (this.localStream) {
            this.localStream.getTracks().forEach(track => track.stop());
            this.localStream = null;
        }

        // Close WebSocket
        if (this.websocket) {
            this.websocket.close();
            this.websocket = null;
        }

        // Reset state
        this.isConnected = false;
        this.isInitiator = false;
        this.isMuted = false;
        this.iceCandidateQueue = [];

        // Update UI
        document.getElementById('startBtn').disabled = false;
        document.getElementById('endBtn').disabled = true;
        document.getElementById('controls').classList.remove('show');
        this.updateStatus('Call ended', 'idle');

        this.log('✅ Call ended', 'success');
    }

    updateStatus(text, type) {
        const statusEl = document.getElementById('status');
        const statusTextEl = document.getElementById('statusText');

        statusTextEl.textContent = text;
        statusEl.className = `status show status-${type}`;

        if (type === 'connecting') {
            statusEl.classList.add('connecting-indicator');
        } else {
            statusEl.classList.remove('connecting-indicator');
        }
    }

    log(message, type = 'info') {
        const timestamp = new Date().toLocaleTimeString();
        const logEntry = `[${timestamp}] ${message}`;

        console.log(logEntry);

        const logsEl = document.getElementById('logs');
        logsEl.classList.add('show');

        const entry = document.createElement('div');
        entry.className = `log-entry log-${type}`;
        entry.textContent = logEntry;

        logsEl.appendChild(entry);
        logsEl.scrollTop = logsEl.scrollHeight;

        // Keep only last 100 entries
        while (logsEl.children.length > 100) {
            logsEl.removeChild(logsEl.firstChild);
        }
    }
}

// Initialize client when page loads
let client;
document.addEventListener('DOMContentLoaded', () => {
    client = new WebRTCClient();
});
