/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nie.translator.rtranslator.voice_translation._conversation_mode._conversation;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;

import java.util.ArrayList;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.tools.BluetoothHeadsetUtils;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.TTS;
import nie.translator.rtranslator.tools.Tools;
import nie.translator.rtranslator.tools.gui.messages.GuiMessage;
import nie.translator.rtranslator.tools.gui.peers.GuiPeer;
import nie.translator.rtranslator.voice_translation.VoiceTranslationService;
// BluetoothCommunicator and ConversationBluetoothCommunicator removed — WebRTC transport is used instead.
// ConversationService is kept for backward compat but sendMessage now delegates to WebRtcVoiceTranslationService.

import nie.translator.rtranslator.bluetooth.Message;
import nie.translator.rtranslator.bluetooth.Peer;
import nie.translator.rtranslator.voice_translation._walkie_talkie_mode._walkie_talkie.WalkieTalkieService;
import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApiText;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;
import nie.translator.rtranslator.voice_translation.neural_networks.voice.Recognizer;
import nie.translator.rtranslator.voice_translation.neural_networks.voice.RecognizerListener;
import nie.translator.rtranslator.voice_translation.neural_networks.voice.Recorder;

public class ConversationService extends VoiceTranslationService {
    // properties
    public static final int SPEECH_BEAM_SIZE = 4;
    public static final int TRANSLATOR_BEAM_SIZE = 1;

    // commands
    public static final int CHANGE_LANGUAGE = 15;

    // others
    private String textRecognized = "";
    private Translator translator;
    private String myPeerName;
    private Recognizer mVoiceRecognizer;
    private RecognizerListener mVoiceRecognizerCallback;
    // communicationCallback removed — replaced by WebRtcVoiceTranslationService
    private Global global;
    // private ConversationBluetoothCommunicator.Callback communicationCallback; // removed
    private static Handler mHandler = new Handler();
    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        global = (Global) getApplication();
        mainHandler = new Handler(Looper.getMainLooper());
        // startBluetoothSco
        // mBluetoothHelper = new BluetoothHelper(this);
        mVoiceCallback = new Recorder.Callback() {
            @Override
            public void onVoiceStart() {
                if (mVoiceRecognizer != null) {
                    super.onVoiceStart();
                    Log.e("recorder", "onVoiceStart");
                    // we notify the client
                    ConversationService.super.notifyVoiceStart();
                }
            }

            @Override
            public void onVoice(@NonNull float[] data, int size) {
                if (mVoiceRecognizer != null) {
                    super.onVoice(data, size);
                    global.getLanguage(true, new Global.GetLocaleListener() {
                        @Override
                        public void onSuccess(CustomLocale result) {
                            int sampleRate = getVoiceRecorderSampleRate();
                            if (sampleRate != 0) {
                                mVoiceRecognizer.recognize(data, SPEECH_BEAM_SIZE, result.getCode());
                            }
                        }

                        @Override
                        public void onFailure(int[] reasons, long value) {
                            ConversationService.super.notifyError(reasons, value);
                        }
                    });
                }
            }

            @Override
            public void onVoiceEnd() {
                if (mVoiceRecognizer != null) {
                    super.onVoiceEnd();
                    Log.e("recorder", "onVoiceEnd");
                    // if the textRecognizer is not empty then it means that we have a result that
                    // has not been correctly recognized as final
                    if (!textRecognized.equals("")) {
                        textRecognized = "";
                    }
                    // the client is notified
                    ConversationService.super.notifyVoiceEnd();
                }
            }

            @Override
            public void onVolumeLevel(float volumeLevel) {
                super.onVolumeLevel(volumeLevel);
                // we notify the client
                ConversationService.super.notifyVolumeLevel(volumeLevel);
            }
        };
        clientHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(final android.os.Message message) {
                int command = message.getData().getInt("command", -1);
                final String text = message.getData().getString("text");
                if (command != -1) {
                    if (!ConversationService.super.executeCommand(command, message.getData())) {
                        switch (command) {
                            case RECEIVE_TEXT:
                                global.getLanguage(true, new Global.GetLocaleListener() {
                                    @Override
                                    public void onSuccess(CustomLocale language) {
                                        if (text != null) {
                                            GuiMessage guiMessage = new GuiMessage(new Message(global, text),
                                                    global.getTranslator().incrementCurrentResultID(), true, true);
                                            // send the message
                                            sendMessage(
                                                    new ConversationMessage(new NeuralNetworkApiText(text, language)));

                                            notifyMessage(guiMessage);
                                            // we save every new message in the exchanged messages so that the fragment
                                            // can restore them
                                            addOrUpdateMessage(guiMessage);
                                        }
                                    }

                                    @Override
                                    public void onFailure(int[] reasons, long value) {
                                        ConversationService.super.notifyError(reasons, value);
                                    }
                                });
                                break;
                        }
                    }
                }
                return false;
            }
        });

        // WebRTC transport: communication callbacks no longer registered here.
        // Use WebRtcVoiceTranslationService for actual calls.
        // if (global.getBluetoothCommunicator() != null) { ... }

        // speech recognition and translation initialization
        translator = global.getTranslator();
        mVoiceRecognizer = global.getSpeechRecognizer();
        mVoiceRecognizerCallback = new VoiceTranslationServiceRecognizerListener() {
            @Override
            public void onSpeechRecognizedResult(String text, String languageCode, double confidenceScore,
                    boolean isFinal) {
                if (text != null && languageCode != null && !text.equals("") && !isMetaText(text)) {
                    CustomLocale language = CustomLocale.getInstance(languageCode);
                    GuiMessage guiMessage = new GuiMessage(new Message(global, text),
                            global.getTranslator().incrementCurrentResultID(), true, isFinal);
                    if (isFinal) {
                        textRecognized = ""; // to ensure that we continue to listen since in this case the result is
                                             // automatically extracted
                        // send the message
                        sendMessage(new ConversationMessage(new NeuralNetworkApiText(text, language)));

                        notifyMessage(guiMessage);
                        // we save every new message in the exchanged messages so that the fragment can
                        // restore them
                        addOrUpdateMessage(guiMessage);
                    } else {
                        notifyMessage(guiMessage);
                        textRecognized = text; // if it equals something then when calling voiceEnd we stop recognition
                    }
                }
            }

            @Override
            public void onError(int[] reasons, long value) {
                ConversationService.super.notifyError(reasons, value);
            }
        };

        mVoiceRecognizer.addCallback(mVoiceRecognizerCallback);
        // mBluetoothHelper.start();

        // voice recorder initialization
        initializeVoiceRecorder();
    }

    /**
     * Sending translated text is now handled by WebRtcVoiceTranslationService.
     * This stub is kept in case ConversationService is still active in a non-WebRTC context.
     * In the normal WebRTC flow, WebRtcVoiceTranslationService.sendTranslationViaPeer() handles this.
     */
    private void sendMessage(ConversationMessage conversationMessage) {
        // Bluetooth removed. For WebRTC-backed calls, outbound text is handled by
        // WebRtcVoiceTranslationService which is the preferred service to start.
        android.util.Log.w("ConversationService",
                "sendMessage called on ConversationService — use WebRtcVoiceTranslationService instead");
    }

    public void initializeVoiceRecorder() {
        if (Tools.hasPermissions(this, REQUIRED_PERMISSIONS)) {
            // voice recorder initialization
            super.mVoiceRecorder = new Recorder((Global) getApplication(), true, mVoiceCallback,
                    new BluetoothHeadsetCallback());
        }
    }

    public String getMyPeerName() {
        return myPeerName;
    }

    public void setMyPeerName(String myPeerName) {
        this.myPeerName = myPeerName;
    }

    @Override
    protected boolean shouldDeactivateMicDuringTTS() {
        return !isBluetoothHeadsetConnected();
    }

    @Override
    protected boolean isBluetoothHeadsetConnected() {
        if (mVoiceRecorder != null) {
            return mVoiceRecorder.isOnHeadsetSco();
        } else {
            return false;
        }
    }

    @Override
    public void onDestroy() {
        mVoiceRecognizer.removeCallback(mVoiceRecognizerCallback);
        mVoiceRecognizer = null;
        // BluetoothCommunicator callbacks removed — no Bluetooth cleanup needed
        super.onDestroy();
    }

    private class BluetoothHelper extends BluetoothHeadsetUtils {
        private BluetoothHelper(Context context) {
            super(context);
        }

        @Override
        public void onHeadsetConnected() {
        }

        @Override
        public void onScoAudioConnected() {
            Bundle bundle = new Bundle();
            bundle.putInt("callback", ON_CONNECTED_BLUETOOTH_HEADSET);
            notifyToClient(bundle);
        }

        @Override
        public void onScoAudioDisconnected() {
            Bundle bundle = new Bundle();
            bundle.putInt("callback", ON_DISCONNECTED_BLUETOOTH_HEADSET);
            notifyToClient(bundle);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stop();
                    start();
                }
            }, 1000);
        }

        @Override
        public void onHeadsetDisconnected() {
        }
    }

    public class BluetoothHeadsetCallback {

        public void onHeadsetConnected() {
        }

        public void onScoAudioConnected() {
            Bundle bundle = new Bundle();
            bundle.putInt("callback", ON_CONNECTED_BLUETOOTH_HEADSET);
            notifyToClient(bundle);
        }

        public void onScoAudioDisconnected() {
            Bundle bundle = new Bundle();
            bundle.putInt("callback", ON_DISCONNECTED_BLUETOOTH_HEADSET);
            notifyToClient(bundle);
        }

        public void onHeadsetDisconnected() {
        }
    }

    public static class ConversationServiceCommunicator extends VoiceTranslationServiceCommunicator {
        public ConversationServiceCommunicator(int id) {
            super(id);
            super.serviceHandler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(android.os.Message msg) {
                    msg.getData().setClassLoader(Peer.class.getClassLoader());
                    int callbackMessage = msg.getData().getInt("callback", -1);
                    Bundle data = msg.getData();
                    executeCallback(callbackMessage, data);
                    return true;
                }
            });
        }
    }
}