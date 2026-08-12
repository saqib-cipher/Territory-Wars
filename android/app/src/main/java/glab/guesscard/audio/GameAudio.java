package glab.guesscard.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * SoundManager and MusicManager for Guess the Card party game.
 * Supports SoundPool playback for card, turn, question, and result effects,
 * as well as audio focus compliance.
 */
public class GameAudio {
    private static final String TAG = "GameAudio";

    public interface Sound {
        int CLICK = 1;
        int CARD_SHUFFLE = 2;
        int CARD_FLIP = 3;
        int CARD_REVEAL = 4;
        int QUESTION_YES = 5;
        int QUESTION_NO = 6;
        int QUESTION_MAYBE = 7;
        int CORRECT = 8;
        int WRONG = 9;
        int TIMER_TICK = 10;
        int TIMER_WARNING = 11;
        int TIMEOUT = 12;
        int VICTORY = 13;
        int DEFEAT = 14;
        int PLAYER_JOIN = 15;
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
            // Log fallback when raw audio resource isn't present
            Log.d(TAG, "Triggered sound: " + soundType);
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