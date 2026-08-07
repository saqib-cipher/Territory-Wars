package com.territorywars.audio;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;

import com.territorywars.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight SoundPool wrapper for in-game SFX.
 *
 * <p>Music streaming is intentionally out of scope here; a full build would
 * hook a MediaPlayer/ExoPlayer service gated by the music preference.</p>
 */
public class GameAudio {

    public interface Sound {
        int CAPTURE = 0;
        int POWERUP = 1;
        int VICTORY = 2;
        int DEFEAT = 3;
        int CLICK = 4;
    }

    private final SoundPool soundPool;
    private final Map<Integer, Integer> soundIds = new HashMap<>();
    private boolean enabled = true;

    public GameAudio(Context context) {
        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .build();
        // Raw resources would live under res/raw; referenced here for completeness.
        // When assets exist, load like: soundPool.load(context, R.raw.capture, 1);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void preload(Context context, int soundRes) {
        soundIds.put(soundRes, soundPool.load(context, soundRes, 1));
    }

    public void play(int soundResource) {
        if (!enabled) return;
        Integer id = soundIds.get(soundResource);
        if (id != null) {
            soundPool.play(id, 1f, 1f, 1, 0, 1f);
        }
    }

    public void release() {
        soundPool.release();
    }
}