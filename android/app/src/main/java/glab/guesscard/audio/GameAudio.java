package glab.guesscard.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * GameAudio handles loading and playing sound effects from assets/sounds/
 * for card flips, correct/wrong answers, victory, and timer countdowns.
 */
public class GameAudio {
    private static final String TAG = "GameAudio";

    public interface Sound {
        int CLICK = 1;
        int CARD_FLIP = 3;
        int QUESTION_YES = 5;
        int QUESTION_NO = 6;
        int CORRECT = 8;
        int WRONG = 9;
        int TIMER_TICK = 10;     // 10sec_timer.mp3
        int TIMER_WARNING = 11;  // 3sec_timer.mp3
        int VICTORY = 13;
    }

    private final SoundPool soundPool;
    private final Map<Integer, Integer> soundMap = new HashMap<>();
    private boolean soundEnabled = true;
    private boolean musicEnabled = true;
    private float soundVolume = 1.0f;
    private float musicVolume = 0.8f;

    public GameAudio(Context context) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(attrs)
                .build();

        loadAssetSounds(context);
    }

    private void loadAssetSounds(Context context) {
        loadAssetSound(context, Sound.CARD_FLIP, "sounds/card_flip.mp3");
        loadAssetSound(context, Sound.CORRECT, "sounds/correct.mp3");
        loadAssetSound(context, Sound.WRONG, "sounds/wronganswer.mp3");
        loadAssetSound(context, Sound.VICTORY, "sounds/winning.mp3");
        loadAssetSound(context, Sound.TIMER_TICK, "sounds/10sec_timer.mp3");
        loadAssetSound(context, Sound.TIMER_WARNING, "sounds/3sec_timer.mp3");
    }

    private void loadAssetSound(Context context, int soundType, String assetPath) {
        try {
            AssetFileDescriptor afd = context.getAssets().openFd(assetPath);
            int soundId = soundPool.load(afd, 1);
            soundMap.put(soundType, soundId);
            Log.d(TAG, "Loaded asset sound: " + assetPath + " -> ID " + soundId);
        } catch (Exception e) {
            Log.w(TAG, "Could not load asset sound: " + assetPath, e);
        }
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setMusicEnabled(boolean enabled) {
        this.musicEnabled = enabled;
    }

    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    public void playSound(int soundType) {
        if (!soundEnabled) return;
        Integer soundId = soundMap.get(soundType);
        if (soundId != null && soundId > 0) {
            soundPool.play(soundId, soundVolume, soundVolume, 1, 0, 1.0f);
        } else {
            Log.d(TAG, "Triggered sound fallback: " + soundType);
        }
    }

    public void loadSound(Context context, int soundType, int resId) {
        try {
            int id = soundPool.load(context, resId, 1);
            soundMap.put(soundType, id);
        } catch (Exception e) {
            Log.w(TAG, "Could not load raw sound resId: " + resId, e);
        }
    }

    public void release() {
        try {
            soundPool.release();
        } catch (Exception ignored) {
        }
    }
}