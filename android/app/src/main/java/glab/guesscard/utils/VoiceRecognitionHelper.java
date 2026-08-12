package glab.guesscard.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Voice recognition helper that wraps Android's SpeechRecognizer.
 * Requires RECORD_AUDIO permission and a device with Google Speech Services.
 *
 * Usage:
 *   VoiceRecognitionHelper voice = new VoiceRecognitionHelper(context, result -> sendQuestion(result));
 *   voice.startListening();
 */
public class VoiceRecognitionHelper {

    private static final String TAG = "VoiceRecognition";

    public interface Listener {
        void onResult(String text);
        void onError(String message);
        void onListeningStarted();
        void onListeningStopped();
    }

    private SpeechRecognizer recognizer;
    private final Context context;
    private Listener listener;
    private boolean isListening = false;

    public VoiceRecognitionHelper(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public static boolean isAvailable(Context context) {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    public void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            if (listener != null) listener.onError("Speech recognition not available on this device.");
            return;
        }

        if (isListening) stopListening();

        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                isListening = true;
                if (listener != null) listener.onListeningStarted();
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                if (listener != null) listener.onListeningStopped();
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    // Capitalize first letter
                    if (!text.isEmpty()) {
                        text = text.substring(0, 1).toUpperCase() + text.substring(1);
                        if (!text.endsWith("?")) text = text + "?";
                    }
                    if (listener != null) listener.onResult(text);
                }
            }

            @Override
            public void onError(int error) {
                isListening = false;
                if (listener != null) listener.onListeningStopped();
                String msg;
                switch (error) {
                    case SpeechRecognizer.ERROR_AUDIO: msg = "Audio recording error"; break;
                    case SpeechRecognizer.ERROR_NO_MATCH: msg = "No speech detected"; break;
                    case SpeechRecognizer.ERROR_NETWORK: msg = "Network error"; break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: msg = "No speech input"; break;
                    default: msg = "Recognition error (" + error + ")";
                }
                if (listener != null) listener.onError(msg);
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partial) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask your question...");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        try {
            recognizer.startListening(intent);
        } catch (Exception e) {
            Log.e(TAG, "startListening failed: " + e.getMessage());
            if (listener != null) listener.onError("Could not start mic: " + e.getMessage());
        }
    }

    public void stopListening() {
        isListening = false;
        if (recognizer != null) {
            try {
                recognizer.stopListening();
                recognizer.cancel();
                recognizer.destroy();
            } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    public boolean isListening() {
        return isListening;
    }

    public void release() {
        stopListening();
    }
}
