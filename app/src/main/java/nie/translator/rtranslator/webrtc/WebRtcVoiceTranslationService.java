package nie.translator.rtranslator.webrtc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.UUID;

import nie.translator.rtranslator.R;
import nie.translator.rtranslator.voice_translation.VoiceTranslationActivity;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.BluetoothHeadsetUtils;
import nie.translator.rtranslator.tools.TTS;
import nie.translator.rtranslator.tools.Tools;
import nie.translator.rtranslator.tools.gui.messages.GuiMessage;
import nie.translator.rtranslator.voice_translation.VoiceTranslationService;
import nie.translator.rtranslator.voice_translation._conversation_mode._conversation.ConversationMessage;
import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApiText;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;
import nie.translator.rtranslator.voice_translation.neural_networks.voice.Recognizer;
import nie.translator.rtranslator.voice_translation.neural_networks.voice.RecognizerListener;
import nie.translator.rtranslator.voice_translation.neural_networks.voice.Recorder;
import nie.translator.rtranslator.bluetooth.Message;
import nie.translator.rtranslator.webrtc.model.SignalingMessage;

/**
 * WebRtcVoiceTranslationService
 *
 * Drop-in replacement for ConversationService.
 * Transport: WebRtcClient (audio) + SignalingClient (call setup) +
 * WebRtcClient DataChannel (translated-text messages).
 * Translation: identical ONNX Recognizer → Translator → TTS pipeline as before.
 *
 * This service is registered in the manifest and started by
 * ConversationFragment / PairingFragment just like ConversationService was,
 * except the Intent must carry:
 * - "callId" (String) – shared room identifier
 * - "serverUrl" (String, optional) – defaults to
 * CallConfig.SIGNALING_SERVER_URL
 * - "userId" (String, optional) – defaults to random UUID persisted in prefs
 */
public class WebRtcVoiceTranslationService extends VoiceTranslationService
        implements WebRtcListener, SignalingListener {

    private static final String TAG = "WebRtcVozSvc";
    public static final int SPEECH_BEAM_SIZE = 4;
    public static final int TRANSLATOR_BEAM_SIZE = 1;

    // -------------------------------------------------------------------------
    // Extras for starting this service
    // -------------------------------------------------------------------------
    public static final String EXTRA_CALL_ID = "callId";
    public static final String EXTRA_SERVER_URL = "serverUrl";
    public static final String EXTRA_USER_ID = "userId";

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private String callId;
    private String userId;
    private String serverUrl;

    // WebRTC
    private WebRtcClient webRtcClient;
    private SignalingClient signalingClient;
    private boolean isInitiator = false; // set when we send the offer
    private boolean isPeerConnected = false; // tracks whether a peer is in the room
    private Runnable stopSelfRunnable; // delayed stopSelf runnable

    // Translation pipeline (same as ConversationService)
    private Translator translator;
    private Recognizer mVoiceRecognizer;
    private RecognizerListener mVoiceRecognizerCallback;
    private Global global;
    private String textRecognized = "";
    private Handler mainHandler;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        global = (Global) getApplication();
        translator = global.getTranslator();
        mainHandler = new Handler(Looper.getMainLooper());

        // ----- Voice recorder callback (same as ConversationService) -----
        mVoiceCallback = new Recorder.Callback() {
            @Override
            public void onVoiceStart() {
                if (mVoiceRecognizer != null) {
                    super.onVoiceStart();
                    WebRtcVoiceTranslationService.super.notifyVoiceStart();
                }
            }

            @Override
            public void onVoice(@NonNull float[] data, int size) {
                if (mVoiceRecognizer != null) {
                    super.onVoice(data, size);
                    global.getLanguage(true, new Global.GetLocaleListener() {
                        @Override
                        public void onSuccess(CustomLocale result) {
                            int sr = getVoiceRecorderSampleRate();
                            if (sr != 0) {
                                mVoiceRecognizer.recognize(data, SPEECH_BEAM_SIZE, result.getCode());
                            }
                        }

                        @Override
                        public void onFailure(int[] reasons, long value) {
                            WebRtcVoiceTranslationService.super.notifyError(reasons, value);
                        }
                    });
                }
            }

            @Override
            public void onVoiceEnd() {
                if (mVoiceRecognizer != null) {
                    super.onVoiceEnd();
                    textRecognized = "";
                    WebRtcVoiceTranslationService.super.notifyVoiceEnd();
                }
            }

            @Override
            public void onVolumeLevel(float volumeLevel) {
                super.onVolumeLevel(volumeLevel);
                WebRtcVoiceTranslationService.super.notifyVolumeLevel(volumeLevel);
            }
        };

        // ----- Client handler (commands from UI) -----
        clientHandler = new Handler(message -> {
            int command = message.getData().getInt("command", -1);
            final String text = message.getData().getString("text");
            if (command != -1) {
                if (!WebRtcVoiceTranslationService.super.executeCommand(command, message.getData())) {
                    if (command == RECEIVE_TEXT) {
                        global.getLanguage(true, new Global.GetLocaleListener() {
                            @Override
                            public void onSuccess(CustomLocale language) {
                                if (text != null) {
                                    GuiMessage guiMessage = new GuiMessage(
                                            new Message(global, text),
                                            global.getTranslator().incrementCurrentResultID(),
                                            true, true);
                                    sendTranslationViaPeer(text, language);
                                    notifyMessage(guiMessage);
                                    addOrUpdateMessage(guiMessage);
                                }
                            }

                            @Override
                            public void onFailure(int[] reasons, long value) {
                                WebRtcVoiceTranslationService.super.notifyError(reasons, value);
                            }
                        });
                    }
                }
            }
            return false;
        });

        // ----- STT recognizer listener (same as ConversationService) -----
        mVoiceRecognizer = global.getSpeechRecognizer();
        mVoiceRecognizerCallback = new VoiceTranslationServiceRecognizerListener() {
            @Override
            public void onSpeechRecognizedResult(
                    String text, String languageCode, double confidenceScore, boolean isFinal) {
                if (text != null && languageCode != null && !text.isEmpty() && !isMetaText(text)) {
                    CustomLocale language = CustomLocale.getInstance(languageCode);
                    GuiMessage guiMessage = new GuiMessage(
                            new Message(global, text),
                            global.getTranslator().incrementCurrentResultID(),
                            true, isFinal);
                    if (isFinal) {
                        textRecognized = "";
                        sendTranslationViaPeer(text, language);
                        notifyMessage(guiMessage);
                        addOrUpdateMessage(guiMessage);
                    } else {
                        notifyMessage(guiMessage);
                        textRecognized = text;
                    }
                }
            }
        };
        if (mVoiceRecognizer != null) {
            mVoiceRecognizer.addCallback(mVoiceRecognizerCallback);
        } else {
            Log.e(TAG, "Speech Recognizer is null! Initialization failed.");
        }

        initializeVoiceRecorder();
        
        // Re-initialize TTS with our own listener to ensure audio routing is set upon readiness
        tts = new TTS(this, new TTS.InitListener() {
            @Override
            public void onInit() {
                if (tts != null) {
                    tts.setOnUtteranceProgressListener(ttsListener);
                    configureAudioRouting();
                }
            }
            @Override
            public void onError(int reason) {
                tts = null;
                notifyError(new int[]{reason}, -1);
                isAudioMute = true;
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // ---- FIX 1: Promote to foreground immediately so Android doesn't kill us ----
        Notification notification = null;
        if (intent != null) {
            notification = intent.getParcelableExtra("notification");
        }
        if (notification == null) {
            notification = buildDefaultNotification();
        }
        startForeground(1, notification);
        Log.d("WebRtcVozSvc", "SERVICE STAYING ALIVE - foreground started");
        // ---------------------------------------------------------------------------

        if (intent != null) {
            String newCallId = intent.getStringExtra(EXTRA_CALL_ID);
            if (newCallId != null)
                callId = newCallId;

            String newServerUrl = intent.getStringExtra(EXTRA_SERVER_URL);
            if (newServerUrl != null)
                serverUrl = newServerUrl;

            String newUserId = intent.getStringExtra(EXTRA_USER_ID);
            if (newUserId != null)
                userId = newUserId;

            if (serverUrl == null || serverUrl.isEmpty()) {
                serverUrl = CallConfig.SIGNALING_SERVER_URL;
            }
            if (userId == null || userId.isEmpty()) {
                userId = UUID.randomUUID().toString();
            }

            if (callId != null && !callId.isEmpty() && webRtcClient == null) {
                startWebRtc();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Builds a minimal notification so startForeground() never receives a null arg.
     */
    private Notification buildDefaultNotification() {
        final String CHANNEL_ID = "webrtc_call_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "WebRTC Call", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null)
                nm.createNotificationChannel(channel);
        }
        Intent openIntent = new Intent(this, VoiceTranslationActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OneVoice Call")
                .setContentText("WebRTC call in progress")
                .setSmallIcon(R.drawable.mic_icon)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        if (mVoiceRecognizer != null) {
            mVoiceRecognizer.removeCallback(mVoiceRecognizerCallback);
        }
        stopWebRtc();
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // VoiceTranslationService abstract method
    // -------------------------------------------------------------------------

    @Override
    public void initializeVoiceRecorder() {
        if (Tools.hasPermissions(this, REQUIRED_PERMISSIONS)) {
            super.mVoiceRecorder = new Recorder(
                    (Global) getApplication(), true, mVoiceCallback,
                    null);
        }
    }

    // -------------------------------------------------------------------------
    // WebRTC setup / teardown
    // -------------------------------------------------------------------------

    private void startWebRtc() {
        Log.d(TAG, "Starting WebRTC callId=" + callId);

        webRtcClient = new WebRtcClient(this, this);
        webRtcClient.initialize();

        signalingClient = new SignalingClient(serverUrl, this);
        signalingClient.connect(callId, userId);
    }

    private void stopWebRtc() {
        if (signalingClient != null) {
            signalingClient.close();
            signalingClient = null;
        }
        if (webRtcClient != null) {
            webRtcClient.sendEndCallSignal();
            webRtcClient.close();
            webRtcClient = null;
        }
    }

    // -------------------------------------------------------------------------
    // SignalingClient.SignalingListener
    // -------------------------------------------------------------------------

    @Override
    public void onConnected() {
        Log.d(TAG, "✅ Signaling connected");
        configureAudioRouting();
        sendLocalPeerInfo();
    }

    private void configureAudioRouting() {
        Log.d(TAG, "🔊 Configuring audio routing (speakerphone, voice communication mode)...");
        android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            setTtsAudioAttributes(audioAttributes);
        }
    }

    /**
     * A new peer joined the room → we are the initiator.
     * Create PeerConnection (initiator) then send offer.
     */
    @Override
    public void onPeerJoined(String peerId) {
        Log.d(TAG, "👤 Peer joined — creating offer");
        isPeerConnected = true;
        cancelStopSelf();
        isInitiator = true;
        webRtcClient.createPeerConnection(true);
        webRtcClient.createOffer();
        sendLocalPeerInfo();
    }

    private void sendLocalPeerInfo() {
        if (signalingClient != null && callId != null) {
            String name = global.getName();
            // In a real app we'd load the avatar URI or Base64 here.
            // For now we just send the name.
            signalingClient.sendPeerInfo(callId, name, null);
        }
    }

    /**
     * We joined a room that already has a peer → we are the answerer.
     * Create PeerConnection (non-initiator) and wait for the offer.
     */
    @Override
    public void onExistingPeer(String peerId) {
        Log.d(TAG, "👤 Existing peer present — creating PeerConnection (answerer)");
        isPeerConnected = true;
        cancelStopSelf();
        isInitiator = false;
        webRtcClient.createPeerConnection(false);
        sendLocalPeerInfo();
    }

    @Override
    public void onPeerInfoReceived(String name, String avatar) {
        Log.d(TAG, "👤 Peer info received in service: " + name);
        this.peerName = name;
        this.peerAvatar = avatar;

        Bundle bundle = new Bundle();
        bundle.putInt("callback", ON_PEER_INFO_RECEIVED);
        bundle.putString("name", name);
        bundle.putString("avatar", avatar);
        notifyToClient(bundle);
    }

    @Override
    public void onOfferReceived(String sdp) {
        Log.d(TAG, "📞 Offer received");
        SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.OFFER, sdp);
        webRtcClient.setRemoteDescription(remoteSdp, () -> {
            webRtcClient.createAnswer();
            return kotlin.Unit.INSTANCE;
        });
    }

    @Override
    public void onAnswerReceived(String sdp) {
        Log.d(TAG, "✅ Answer received");
        SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
        webRtcClient.setRemoteDescription(remoteSdp, () -> kotlin.Unit.INSTANCE);
    }

    @Override
    public void onIceCandidateReceived(nie.translator.rtranslator.webrtc.model.IceCandidate candidate) {
        webRtcClient.addIceCandidate(candidate);
    }

    @Override
    public void onPeerLeft(String peerId) {
        // FIX 2: Don't stop immediately — could be a ghost event or brief dropout.
        // Wait 5 seconds; if the peer reconnects in that window we cancel the shutdown.
        Log.d(TAG, "👋 Peer left — scheduling stop in 5 s");
        isPeerConnected = false;
        stopSelfRunnable = () -> {
            if (!isPeerConnected) {
                Log.d(TAG, "Peer did not reconnect — stopping service");
                stopSelf();
            }
        };
        mainHandler.postDelayed(stopSelfRunnable, 5000);
    }

    private void cancelStopSelf() {
        if (stopSelfRunnable != null) {
            mainHandler.removeCallbacks(stopSelfRunnable);
            stopSelfRunnable = null;
            Log.d(TAG, "Stop-self runnable cancelled — peer reconnected");
        }
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "Signaling disconnected");
    }

    @Override
    public void onError(String error) {
        Log.e(TAG, "Signaling error: " + error);
    }

    // -------------------------------------------------------------------------
    // WebRtcClient.WebRtcListener
    // -------------------------------------------------------------------------

    @Override
    public void onLocalSdpCreated(SessionDescription sdp) {
        if (sdp.type == SessionDescription.Type.OFFER) {
            signalingClient.sendOffer(callId, sdp.description);
        } else {
            signalingClient.sendAnswer(callId, sdp.description);
        }
    }

    @Override
    public void onIceCandidateGenerated(nie.translator.rtranslator.webrtc.model.IceCandidate candidate) {
        signalingClient.sendIceCandidate(callId, candidate);
    }

    @Override
    public void onConnectionStateChanged(PeerConnection.IceConnectionState state) {
        Log.d(TAG, "ICE state: " + state);
    }

    @Override
    public void onAudioTrackReceived() {
        Log.d(TAG, "🎵 Remote audio track received");
    }

    /**
     * Incoming translated-text from the remote peer via DataChannel (fallback).
     * Parse text + language code and delegate to processIncomingTranslation.
     */
    @Override
    public void onTranslationReceived(String rawMessage) {
        Log.d(TAG, "📥 Translation received via DataChannel: " + rawMessage.substring(0, Math.min(60, rawMessage.length())));
        String text;
        String languageCode;

        if (rawMessage.contains(CallConfig.MESSAGE_SEPARATOR)) {
            int idx = rawMessage.lastIndexOf(CallConfig.MESSAGE_SEPARATOR);
            text = rawMessage.substring(0, idx);
            languageCode = rawMessage.substring(idx + CallConfig.MESSAGE_SEPARATOR.length());
        } else {
            text = rawMessage;
            languageCode = "en";
        }
        processIncomingTranslation(text, languageCode);
    }

    /**
     * Core processing for incoming translated text — shared by both DataChannel
     * and WebSocket relay paths.
     */
    private void processIncomingTranslation(String text, String languageCode) {
        final CustomLocale sourceLanguage = CustomLocale.getInstance(languageCode);

        global.getLanguage(true, new Global.GetLocaleListener() {
            @Override
            public void onSuccess(CustomLocale myLanguage) {
                ConversationMessage conversationMessage = new ConversationMessage(
                        new NeuralNetworkApiText(text, sourceLanguage));

                translator.translateMessage(conversationMessage, myLanguage, TRANSLATOR_BEAM_SIZE,
                        new Translator.TranslateMessageListener() {
                            @Override
                            public void onTranslatedMessage(
                                    ConversationMessage cm, long messageID, boolean isFinal) {
                                global.getTTSLanguages(true, new Global.GetLocalesListListener() {
                                    @Override
                                    public void onSuccess(ArrayList<CustomLocale> ttsLanguages) {
                                        if (isFinal) {
                                            if (CustomLocale.containsLanguage(ttsLanguages,
                                                    cm.getPayload().getLanguage())) {
                                                speak(cm.getPayload().getText(), cm.getPayload().getLanguage());
                                            }
                                        }
                                        Message msg = new Message(global, text);
                                        msg.setText(cm.getPayload().getText());
                                        GuiMessage guiMessage = new GuiMessage(msg, messageID, false, isFinal);
                                        notifyMessage(guiMessage);
                                        addOrUpdateMessage(guiMessage);
                                    }

                                    @Override
                                    public void onFailure(int[] reasons, long value) {
                                    }
                                });
                            }

                            @Override
                            public void onFailure(int[] reasons, long value) {
                                WebRtcVoiceTranslationService.super.notifyError(reasons, value);
                            }
                        });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                WebRtcVoiceTranslationService.super.notifyError(reasons, value);
            }
        });
    }

    /**
     * Incoming translated text from the remote peer via WebSocket relay.
     * This is the SignalingListener override (WS relay path).
     * Delegates to the same processing logic as the DataChannel path.
     */
    @Override
    public void onTranslationReceived(String text, String lang) {
        Log.d(TAG, "📥 WS relay translation received: lang=" + lang
                + "  text=" + text.substring(0, Math.min(60, text.length())));
        processIncomingTranslation(text, lang);
    }


    @Override
    public void onCallEndedByPeer() {
        Log.d(TAG, "📡 onCallEndedByPeer: Remote peer ended the call — terminating immediately.");
        // We notify UI that peer disconnected, and stop service gracefully.
        // It mimics pressing End Call on our side.
        stopSelf();

        mainHandler.post(() -> {
            android.widget.Toast.makeText(this, "The remote user ended the call.", android.widget.Toast.LENGTH_LONG)
                    .show();
            // This forces VoiceTranslationActivity to fall back similarly to disconnected
            Intent intent = new Intent(this, VoiceTranslationActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        });
    }

    // -------------------------------------------------------------------------
    // Full-duplex: keep mic active while TTS plays translated audio.
    // No raw audio is transmitted (audio track is muted in WebRtcClient),
    // so echo only affects local STT — acceptable trade-off for full-duplex.
    // -------------------------------------------------------------------------

    @Override
    protected boolean shouldDeactivateMicDuringTTS() {
        return false; // return false for full-duplex (microphone stays active during TTS)
    }

    @Override
    protected boolean isBluetoothHeadsetConnected() {
        if (mVoiceRecorder != null) {
            return mVoiceRecorder.isOnHeadsetSco();
        } else {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Send helper — called after local STT + translate produces final text
    // -------------------------------------------------------------------------

    /**
     * Send the locally recognized text to the remote peer.
     * PRIMARY path: WebSocket relay via signaling server (works even when ICE fails).
     * FALLBACK: DataChannel (if WebRTC peer connection succeeded).
     *
     * Format sent via WS: type=translation, text=<original>, lang=<bcp47>
     */
    private void sendTranslationViaPeer(String originalText, CustomLocale sourceLanguage) {
        if (originalText == null || originalText.isEmpty()) return;
        String lang = sourceLanguage != null ? sourceLanguage.getCode() : "und";
        Log.d(TAG, "📤 Sending text to peer (WS relay): lang=" + lang
                + "  text=" + originalText.substring(0, Math.min(40, originalText.length())));
        if (signalingClient != null) {
            // Primary: always try WS relay (reliable, works through server)
            signalingClient.sendTranslation(callId, originalText, lang);
        }
        if (webRtcClient != null) {
            // Fallback: also try DataChannel (if ICE succeeded)
            webRtcClient.sendTranslationMessage(originalText, lang);
        }
    }

    @Override
    protected boolean executeCommand(int command, Bundle data) {
        if (command == GET_ATTRIBUTES) {
            // Intercept GET_ATTRIBUTES to add peer info
            Bundle bundle = new Bundle();
            bundle.putInt("callback", ON_ATTRIBUTES);
            bundle.putParcelableArrayList("messages", getMessages());
            bundle.putBoolean("isMicMute", isMicMute);
            bundle.putBoolean("isAudioMute", isAudioMute);
            bundle.putBoolean("isTTSError", tts == null);
            bundle.putBoolean("isEditTextOpen", isEditTextOpen);
            bundle.putBoolean("isBluetoothHeadsetConnected", isBluetoothHeadsetConnected());
            bundle.putBoolean("isMicAutomatic", isMicAutomatic);
            bundle.putBoolean("isMicActivated", isMicActivated);
            bundle.putString("peerName", peerName);
            bundle.putString("peerAvatar", peerAvatar);

            if (mVoiceRecorder != null && mVoiceRecorder.isRecording()) {
                if (manualRecognizingFirstLanguage) {
                    bundle.putInt("listeningMic", FIRST_LANGUAGE);
                } else if (manualRecognizingSecondLanguage) {
                    bundle.putInt("listeningMic", SECOND_LANGUAGE);
                } else {
                    bundle.putInt("listeningMic", AUTO_LANGUAGE);
                }
            } else {
                bundle.putInt("listeningMic", -1);
            }
            notifyToClient(bundle);
            return true;
        }
        return super.executeCommand(command, data);
    }

    // Helper to get messages from parent via reflection or change messages access
    // modifier
    private ArrayList<GuiMessage> getMessages() {
        try {
            java.lang.reflect.Field field = VoiceTranslationService.class.getDeclaredField("messages");
            field.setAccessible(true);
            return (ArrayList<GuiMessage>) field.get(this);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // -------------------------------------------------------------------------
    // Communicator (mirrors ConversationService.ConversationServiceCommunicator)
    // -------------------------------------------------------------------------

    public static class WebRtcServiceCommunicator extends VoiceTranslationServiceCommunicator {
        public WebRtcServiceCommunicator(int id) {
            super(id);
            super.serviceHandler = new Handler(msg -> {
                msg.getData().setClassLoader(nie.translator.rtranslator.bluetooth.Peer.class.getClassLoader());
                int cb = msg.getData().getInt("callback", -1);
                executeCallback(cb, msg.getData());
                return true;
            });
        }
    }
}
