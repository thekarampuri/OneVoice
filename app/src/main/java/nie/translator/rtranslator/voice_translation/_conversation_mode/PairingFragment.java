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

package nie.translator.rtranslator.voice_translation._conversation_mode;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import java.util.UUID;
import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.settings.SettingsActivity;
import nie.translator.rtranslator.tools.Tools;
import nie.translator.rtranslator.voice_translation.VoiceTranslationActivity;
import nie.translator.rtranslator.webrtc.CallConfig;
import nie.translator.rtranslator.webrtc.WebRtcVoiceTranslationService;


public class PairingFragment extends PairingToolbarFragment {

    // UI
    private ConstraintLayout constraintLayout;
    private EditText callIdInput;
    private Button startCallButton;
    private TextView callIdHint;
    private AppCompatImageButton exitButton;
    private AppCompatImageButton settingsButton;
    private Handler mainHandler;

    public PairingFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pairing, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        constraintLayout  = view.findViewById(R.id.container);
        exitButton        = view.findViewById(R.id.exitButton);
        settingsButton    = view.findViewById(R.id.settingsButton);
        callIdInput       = view.findViewById(R.id.callIdInput);
        startCallButton   = view.findViewById(R.id.startCallButton);
        callIdHint        = view.findViewById(R.id.callIdHint);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Apply window insets
        WindowInsets windowInsets = activity.getFragmentContainer().getRootWindowInsets();
        if (windowInsets != null) {
            constraintLayout.dispatchApplyWindowInsets(
                windowInsets.replaceSystemWindowInsets(
                    windowInsets.getSystemWindowInsetLeft(),
                    windowInsets.getSystemWindowInsetTop(),
                    windowInsets.getSystemWindowInsetRight(), 0));
        }

        settingsButton.setOnClickListener(v -> {
            startActivity(new Intent(activity, SettingsActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });

        exitButton.setOnClickListener(v -> activity.onBackPressed());

        // --- WebRTC: join call by Call ID ---
        startCallButton.setOnClickListener(v -> {
            String callId = callIdInput != null ? callIdInput.getText().toString().trim() : "";
            if (TextUtils.isEmpty(callId)) {
                // Generate a random room name if none provided
                callId = UUID.randomUUID().toString().substring(0, 8);
                if (callIdInput != null) callIdInput.setText(callId);
                Toast.makeText(activity,
                    "No Call ID entered. Using: " + callId, Toast.LENGTH_SHORT).show();
            }
            joinCall(callId);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    /**
     * Start WebRtcVoiceTranslationService and navigate to the conversation fragment.
     */
    private void joinCall(String callId) {
        Log.d("PairingFragment", "Joining call: " + callId);
        String userId = ((Global) activity.getApplication()).getMyPeer().getUniqueName();

        Intent serviceIntent = new Intent(activity, WebRtcVoiceTranslationService.class);
        serviceIntent.putExtra(WebRtcVoiceTranslationService.EXTRA_CALL_ID, callId);
        serviceIntent.putExtra(WebRtcVoiceTranslationService.EXTRA_SERVER_URL, CallConfig.SIGNALING_SERVER_URL);
        serviceIntent.putExtra(WebRtcVoiceTranslationService.EXTRA_USER_ID, userId);
        activity.startService(serviceIntent);

        // Navigate to conversation fragment
        activity.setFragment(VoiceTranslationActivity.CONVERSATION_FRAGMENT);
    }

    @Override
    protected void startSearch() {
        // No Bluetooth search — WebRTC uses a Call ID
    }

    public void clearFoundPeers() {
        // No-op in WebRTC mode
    }

    public void setListViewClickable(boolean isClickable, boolean showToast) {
        // No-op in WebRTC mode
    }
}