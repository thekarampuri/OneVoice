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

package nie.translator.rtranslator;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.animation.AnticipateInterpolator;
import androidx.annotation.NonNull;
import android.animation.ObjectAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import java.util.ArrayList;
import nie.translator.rtranslator.access.AccessActivity;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.ErrorCodes;
import nie.translator.rtranslator.tools.ImageActivity;
import nie.translator.rtranslator.voice_translation.VoiceTranslationActivity;
import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApi;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;

import androidx.core.splashscreen.SplashScreen;


public class LoadingActivity extends GeneralActivity {
    private final boolean START_IMAGE = false;
    private Handler mainHandler;
    private boolean isVisible = false;
    private Global global;
    private boolean startingActivity = false;
    private boolean showingError = false;
    private long startTime;
    private final long SPLASH_DURATION = 3000; // 3 seconds

    public LoadingActivity() {
        // Required empty public constructor
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String previousActivity = getIntent().getStringExtra("activity");
        SplashScreen splashScreen = null;
        if(previousActivity == null || !previousActivity.equals("download")) {  //if this activity is called by the DownloadFragment we don't use the splash screen
            // Handle the splash screen transition (it must remain before the super.onCreate() call).
            splashScreen = SplashScreen.installSplashScreen(this);
        }
        super.onCreate(savedInstanceState);
        if(splashScreen == null){
            setTheme(R.style.Theme_Speech);
        }
        setContentView(R.layout.activity_loading);
        mainHandler = new Handler(Looper.getMainLooper());
        startTime = System.currentTimeMillis();

        // Keep the splash screen visible for this Activity.
        if(splashScreen != null) {
            splashScreen.setKeepOnScreenCondition(new SplashScreen.KeepOnScreenCondition() {
                @Override
                public boolean shouldKeepOnScreen() {
                    long currentTime = System.currentTimeMillis();
                    return currentTime - startTime < SPLASH_DURATION && !showingError;
                }
            });

            // Set the exit animation for the splash screen
            splashScreen.setOnExitAnimationListener(splashScreenProvider -> {
                final View splashScreenView = splashScreenProvider.getView();
                ObjectAnimator fadeOut = ObjectAnimator.ofFloat(
                        splashScreenView,
                        View.ALPHA,
                        1f,
                        0f
                );
                fadeOut.setInterpolator(new AnticipateInterpolator());
                fadeOut.setDuration(500L);

                // Call splashScreenProvider.remove() at the end of the animation.
                fadeOut.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        splashScreenProvider.remove();
                    }
                });

                fadeOut.start();
            });
        }
    }

    public void onResume() {
        super.onResume();
        isVisible = true;
        global = (Global) getApplication();
        if (global.isFirstStart()) {
            Intent intent = new Intent(this, AccessActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        } else if (global.getTranslator() != null && global.getSpeechRecognizer() != null) {
            startVoiceTranslationActivity();
        } else {
            initializeApp(false);
            //onFailure(new int[]{ErrorCodes.GOOGLE_TTS_ERROR}, 0);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isVisible = false;
    }

    private void initializeApp(boolean ignoreTTSError) {
        global.getLanguages(false, ignoreTTSError, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> result) {
                global.initializeTranslator(new Translator.InitListener() {
                    @Override
                    public void onInitializationFinished() {
                        global.initializeSpeechRecognizer(new NeuralNetworkApi.InitListener() {
                            @Override
                            public void onInitializationFinished() {
                                if (isVisible) {
                                    startVoiceTranslationActivity();
                                }
                            }

                            @Override
                            public void onError(int[] reasons, long value) {
                                global.deleteSpeechRecognizer();  //we do this to ensure the restart of the loading of models when the app is restarted
                                LoadingActivity.this.onFailure(reasons, value);
                            }
                        });
                    }

                    @Override
                    public void onError(int[] reasons, long value) {
                        global.deleteTranslator();   //we do this to ensure the restart of the loading of models when the app is restarted
                        LoadingActivity.this.onFailure(reasons, value);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                LoadingActivity.this.onFailure(reasons, value);
            }
        });
    }

    private void startVoiceTranslationActivity() {
        if(!START_IMAGE) {
            startingActivity = true;
            Intent intent = new Intent(LoadingActivity.this, VoiceTranslationActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }else{
            startImageActivity();
        }
    }

    private void startImageActivity() {
        startingActivity = true;
        Intent intent = new Intent(LoadingActivity.this, ImageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();

    }

    private void notifyGoogleTTSErrorDialog() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                showGoogleTTSErrorDialog(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        initializeApp(true);
                    }
                });
            }
        });
    }

    public void notifyInternetLack() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isVisible) {
                    // creation of the dialog.
                    AlertDialog.Builder builder = new AlertDialog.Builder(LoadingActivity.this);
                    //builder.setCancelable(true);
                    builder.setMessage(R.string.error_internet_lack_loading);
                    builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
                    builder.setPositiveButton(R.string.retry, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            initializeApp(false);
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                }
            }
        });
    }

    public void notifyModelsLoadingError() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isVisible) {
                    // creation of the dialog.
                    AlertDialog.Builder builder = new AlertDialog.Builder(LoadingActivity.this);
                    //builder.setCancelable(true);
                    builder.setMessage(R.string.error_models_loading);
                    builder.setPositiveButton(R.string.fix, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if(global != null){
                                restartDownload();
                            }
                        }
                    });
                    builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                }
            }
        });
    }

    private void notifyMissingGoogleTTSDialog() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isVisible) {
                    showMissingGoogleTTSDialog(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            initializeApp(true);
                        }
                    });
                }
            }
        });
    }


    private void restartDownload(){
        //we reset all the download shared preferences
        SharedPreferences sharedPreferences = getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor;
        editor = sharedPreferences.edit();
        editor.putLong("currentDownloadId", -1);
        editor.apply();
        editor = sharedPreferences.edit();
        editor.putString("lastDownloadSuccess", "");
        editor.apply();
        editor = sharedPreferences.edit();
        editor.putString("lastTransferSuccess", "");
        editor.apply();
        editor = sharedPreferences.edit();
        editor.putString("lastTransferFailure", "");
        editor.apply();
        //we restart the download (only the corrupted files will be re-downloaded)
        global.setFirstStart(true);
        Intent intent = new Intent(LoadingActivity.this, AccessActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void onFailure(int[] reasons, long value) {
        for (int aReason : reasons) {
            switch (aReason) {
                case ErrorCodes.ERROR_LOADING_MODEL:
                    showingError = true;
                    notifyModelsLoadingError();
                    break;
                case ErrorCodes.SAFETY_NET_EXCEPTION:
                case ErrorCodes.MISSED_CONNECTION:
                    showingError = true;
                    notifyInternetLack();
                    break;
                case ErrorCodes.MISSING_GOOGLE_TTS:
                    showingError = true;
                    notifyMissingGoogleTTSDialog();
                    break;
                case ErrorCodes.GOOGLE_TTS_ERROR:
                    showingError = true;
                    notifyGoogleTTSErrorDialog();
                    break;
                default:
                    onError(aReason, value);
                    break;
            }
        }
    }
}
