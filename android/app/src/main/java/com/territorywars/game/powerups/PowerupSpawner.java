package com.territorywars.game.powerups;

import com.territorywars.game.map.GameMap;
import com.territorywars.models.PowerupType;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Periodically spawns random power-ups on unoccupied map tiles.
 */
public class PowerupSpawner {

    private static final int MAX_ACTIVE = 5;
    private static final int SPAWN_INTERVAL_MS = 6_000;

    private final GameMap map;
    private final Random random = new Random();
    private long nextSpawnAt;

    public PowerupSpawner(GameMap map) {
        this.map = map;
        this.nextSpawnAt = System.currentTimeMillis();
    }

    public void update(long now, List<GamePowerup> powerups) {
        if (powerups.size() >= MAX_ACTIVE || now < nextSpawnAt) return;
        nextSpawnAt = now + SPAWN_INTERVAL_MS;

        PowerupType type = PowerupType.values()[random.nextInt(PowerupType.values().length)];
        int gx = map.randomTileX();
        int gy = map.randomTileY();
        float x = (gx + 0.5f) * GameMap.TILE_SIZE;
        float y = (gy + 0.5f) * GameMap.TILE_SIZE;
        powerups.add(new GamePowerup(UUID.randomUUID().toString(), type, x, y, now));
    }
}