package com.territorywars.models;

/**
 * Power-ups that spawn on the map during a match.
 */
public enum PowerupType {
    SPEED(0, 5_000),
    INVINCIBILITY(1, 4_000),
    DOUBLER(2, 8_000),
    FREEZE(3, 3_000),
    HEAL(4, 0),
    SHIELD(5, 6_000);

    private final int code;
    private final long durationMillis;

    PowerupType(int code, long durationMillis) {
        this.code = code;
        this.durationMillis = durationMillis;
    }

    public int getCode() {
        return code;
    }

    /** How long the effect lasts; 0 for instant effects (heal). */
    public long getDurationMillis() {
        return durationMillis;
    }

    public static PowerupType fromCode(int code) {
        for (PowerupType t : values()) {
            if (t.code == code) return t;
        }
        return SPEED;
    }
}