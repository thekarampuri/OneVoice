# Architecture Decision Records (ADRs)

This document captures key architectural decisions made during the development of Bhasha Setu, along with the context and rationale behind each decision.

## ADR-001: Separate Backend from Android App

**Date**: 2026-01-11

**Status**: Accepted

**Context**:
The initial project structure had the backend nested inside the Android app directory (`android-app/VoiceTranslate/backend/`). This created confusion about component boundaries and made it difficult to:
- Deploy the backend independently
- Version control components separately
- Scale components independently
- Understand the system architecture at a glance

**Decision**:
Restructure the project to have separate top-level directories for each major component:
- `android/` - Android client only
- `signaling-server/` - WebSocket audio relay service
- `docs/` - Architecture documentation

Note: `media-server/` was planned for Phase 2 STT/TTS/Translation services but has been removed to focus on Phase 1 audio relay.

**Consequences**:
- ✅ Clear separation of concerns
- ✅ Independent deployment and scaling
- ✅ Easier to understand project structure
- ✅ Better alignment with microservices architecture
- ⚠️ Requires updating deployment scripts
- ⚠️ Need to update Android app to point to new backend location

---

## ADR-002: Audio Relay Only in Phase 1

**Date**: 2026-01-11

**Status**: Accepted

**Context**:
Previous attempts to implement STT/TTS alongside the audio relay led to complexity and instability. The core audio relay functionality needed to be solid before adding translation features.

**Decision**:
Implement the project in phases:
- **Phase 1**: Audio relay only (current)
- **Phase 2**: Add STT/TTS/Translation pipeline

The signaling server will focus solely on WebSocket connection management and audio relay without any speech processing.

**Consequences**:
- ✅ Simpler, more stable initial implementation
- ✅ Easier to debug audio quality issues
- ✅ Faster time to working prototype
- ✅ Clear milestone for Phase 1 completion
- ⚠️ Translation features delayed to Phase 2
- ⚠️ Need to ensure Phase 2 integration is smooth

---

## ADR-003: WebSocket for Real-Time Audio Streaming

**Date**: 2026-01-10

**Status**: Accepted

**Context**:
Need a protocol for real-time bidirectional audio streaming between mobile clients and server. Options considered:
- HTTP polling
- Server-Sent Events (SSE)
- WebRTC
- WebSocket

**Decision**:
Use WebSocket for audio streaming.

**Rationale**:
- **WebSocket**: Full-duplex, low latency, widely supported, simpler than WebRTC
- **WebRTC**: More complex setup, overkill for server-relay architecture
- **HTTP/SSE**: Not suitable for bidirectional real-time streaming

**Consequences**:
- ✅ Low latency bidirectional communication
- ✅ Simple implementation
- ✅ Good mobile support (OkHttp WebSocket)
- ⚠️ No built-in NAT traversal (unlike WebRTC)
- ⚠️ Requires server to relay audio (can't do peer-to-peer)

---

## ADR-004: Room-Based Connection Management

**Date**: 2026-01-10

**Status**: Accepted

**Context**:
Need a way to manage multiple concurrent calls and route audio between participants.

**Decision**:
Use a room-based architecture where:
- Each call has a unique `call_id`
- Clients join rooms by connecting with the same `call_id`
- Audio from one client is relayed to all other clients in the same room
- Rooms are created on-demand and deleted when empty

**Consequences**:
- ✅ Simple to implement and understand
- ✅ Supports multi-party calls (future)
- ✅ Automatic cleanup of inactive rooms
- ✅ Easy to add room-level features (recording, moderation)
- ⚠️ In-memory storage limits scalability
- ⚠️ Need Redis or similar for multi-server deployment

---

## ADR-005: FastAPI for Signaling Server

**Date**: 2026-01-10

**Status**: Accepted

**Context**:
Need to choose a framework for the signaling server. Options:
- Node.js (Express, Socket.io)
- Python (FastAPI, Flask)
- Go
- Rust

**Decision**:
Use FastAPI (Python) for the signaling server.

**Rationale**:
- **FastAPI**: Modern, async support, WebSocket support, easy to integrate with ML models (Phase 2)
- **Node.js**: Good for real-time, but Python better for ML integration
- **Go/Rust**: More performant but steeper learning curve

**Consequences**:
- ✅ Easy integration with Whisper, MarianMT (Phase 2)
- ✅ Modern async/await syntax
- ✅ Built-in WebSocket support
- ✅ Automatic API documentation
- ⚠️ Slightly lower performance than Go/Rust
- ⚠️ GIL limitations for CPU-bound tasks (mitigated by async I/O)

---

## ADR-006: PCM 16-bit Audio Format

**Date**: 2026-01-10

**Status**: Accepted

**Context**:
Need to choose an audio format for streaming. Options:
- Opus (compressed)
- AAC (compressed)
- PCM (uncompressed)

**Decision**:
Use PCM 16-bit, 16 kHz, mono for audio streaming.

**Rationale**:
- **PCM**: Uncompressed, simple, compatible with Whisper (Phase 2)
- **Opus/AAC**: Better bandwidth efficiency but adds encoding/decoding latency
- **16 kHz**: Standard for speech recognition, good quality vs bandwidth trade-off
- **Mono**: Sufficient for voice, reduces bandwidth by 50%

**Consequences**:
- ✅ No encoding/decoding latency
- ✅ Direct compatibility with Whisper
- ✅ Simple implementation
- ⚠️ Higher bandwidth usage (~256 kbps)
- ⚠️ May need compression for poor network conditions

---

## ADR-007: Language Code as User ID

**Date**: 2026-01-10

**Status**: Accepted

**Context**:
Need a way to identify users in a call room. The WebSocket endpoint includes `source_lang` and `target_lang` parameters.

**Decision**:
Use `source_lang` as the `user_id` for connection management.

**Rationale**:
- Simple and unique within a call (assuming one speaker per language)
- Useful for debugging and logging
- Will be used for language-specific processing in Phase 2

**Consequences**:
- ✅ Simple user identification
- ✅ Language information embedded in user ID
- ⚠️ Limits to one speaker per language per call
- ⚠️ Need to change if supporting multiple speakers of same language

---

## ADR-008: Media Server as Separate Component

**Date**: 2026-01-11

**Status**: Accepted

**Context**:
Translation features (STT/TTS) are planned for Phase 2. Need to decide whether to integrate them into the signaling server or create a separate service.

**Decision**:
Create a separate `media-server` component for all speech processing (ASR, Translation, TTS) in Phase 2.

**Update (2026-01-12)**: The `media-server/` directory has been removed to focus on Phase 1 (audio relay only). It will be recreated when implementing Phase 2.

**Rationale**:
- **Separation of Concerns**: Signaling server handles connections, media server handles processing
- **Independent Scaling**: Can scale processing separately from connection management
- **Technology Flexibility**: Can use different tech stack for ML models
- **Resource Isolation**: Heavy ML processing doesn't affect connection stability

**Consequences**:
- ✅ Clear separation of concerns
- ✅ Independent scaling and deployment
- ✅ Easier to swap out ML models
- ✅ Better resource management
- ⚠️ More complex architecture
- ⚠️ Need inter-service communication protocol

---

## ADR-009: Placeholder Directories for Phase 2

**Date**: 2026-01-11

**Status**: Deprecated

**Context**:
Phase 2 features are not yet implemented, but we want to establish the architecture early.

**Decision**:
Create placeholder directories and documentation for Phase 2 components when needed.

**Update (2026-01-12)**: Status changed to **Deprecated**. The placeholder `media-server/` directory has been removed to avoid confusion during Phase 1. Phase 2 components will be created when implementation begins.

**Consequences**:
- ✅ Clear roadmap for Phase 2
- ✅ Architecture decisions made early
- ✅ Easy to see what's coming next
- ✅ Prevents ad-hoc additions later
- ⚠️ Directories are empty (may confuse contributors)
- ⚠️ Need to keep documentation updated

---

## ADR-010: Centralized Documentation in docs/

**Date**: 2026-01-11

**Status**: Accepted

**Context**:
Architecture documentation was scattered across component READMEs and not easily discoverable.

**Decision**:
Create a centralized `docs/` directory with:
- `call-flow.md` - WebSocket and audio relay flow
- `translation-flow.md` - Phase 2 translation pipeline
- `decisions.md` - This file (ADRs)

**Consequences**:
- ✅ Single source of truth for architecture
- ✅ Easy to find documentation
- ✅ Better for onboarding new developers
- ✅ Supports mermaid diagrams and rich formatting
- ⚠️ Need to keep docs in sync with code
- ⚠️ Risk of documentation drift

---

## ADR-011: Ngrok for Development Testing

**Date**: 2026-01-10

**Status**: Accepted

**Context**:
Need to test Android app with backend server during development. Options:
- Local network (requires same WiFi)
- Cloud deployment (slow iteration)
- Ngrok (tunneling)

**Decision**:
Use ngrok for development testing to expose local server to mobile devices.

**Consequences**:
- ✅ Easy testing from any network
- ✅ Fast iteration cycle
- ✅ No need for cloud deployment during development
- ⚠️ Requires ngrok installation
- ⚠️ Free tier has limitations
- ⚠️ Not suitable for production

---

## Future ADRs

Topics to be decided:

- **ADR-012**: Choice of TTS engine (Coqui vs Cloud services)
- **ADR-013**: Translation model selection (MarianMT vs alternatives)
- **ADR-014**: Deployment strategy (Docker, Kubernetes, etc.)
- **ADR-015**: Monitoring and observability approach
- **ADR-016**: Authentication and authorization mechanism
- **ADR-017**: Data privacy and storage policies

---

## Template for New ADRs

```markdown
## ADR-XXX: [Title]

**Date**: YYYY-MM-DD

**Status**: [Proposed | Accepted | Deprecated | Superseded]

**Context**:
[Describe the problem and why a decision is needed]

**Decision**:
[State the decision clearly]

**Rationale**:
[Explain why this decision was made, alternatives considered]

**Consequences**:
- ✅ [Positive consequences]
- ⚠️ [Negative consequences or trade-offs]
```
