package com.territorywars.models;

import androidx.annotation.NonNull;

/**
 * Game modes supported by the matchmaker.
 */
public enum GameMode {
    CLASSIC(0),
    RANKED(1),
    TEAM_BATTLE(2),
    PRIVATE_ROOM(3),
    OFFLINE(4);

    private final int code;

    GameMode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    @NonNull
    public static GameMode fromCode(int code) {
        for (GameMode mode : values()) {
            if (mode.code == code) return mode;
        }
        return CLASSIC;
    }
}