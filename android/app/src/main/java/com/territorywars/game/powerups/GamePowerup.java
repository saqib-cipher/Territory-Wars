package com.territorywars.game.powerups;

import com.territorywars.models.PowerupType;

/**
 * A power-up tile spawned randomly on the map. Picking it up grants the
 * specified effect for a duration (or instantly, e.g. heal).
 */
public class GamePowerup {

    public final String id;
    public final PowerupType type;
    public final float x;
    public final float y;
    public long spawnTimeMillis;
    public final long lifetimeMillis = 20_000L; // despawn after 20s

    public GamePowerup(String id, PowerupType type, float x, float y, long now) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.spawnTimeMillis = now;
    }

    public boolean isExpired(long now) {
        return now - spawnTimeMillis > lifetimeMillis;
    }
}