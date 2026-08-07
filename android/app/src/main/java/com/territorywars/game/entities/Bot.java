package com.territorywars.game.entities;

import com.territorywars.game.map.GameMap;

import java.util.Random;

/**
 * Simple autonomous enemy used in Offline Practice mode.
 *
 * <p>Bots wander, drift toward neutral/unowned tiles and capture them. Their
 * movement is intentionally imperfect so offline matches stay winnable.</p>
 */
public class Bot extends Player {

    private final Random random = new Random();
    private float retargetTimer = 0f;
    private float steerX;
    private float steerY;

    public Bot(String id, String username) {
        super(id, username, com.territorywars.game.tiles.TileOwner.ENEMY);
    }

    /**
     * Overridden: decides movement each few frames, then delegates to the
     * parent's physics tick.
     */
    @Override
    public boolean tick(GameMap map, float dt) {
        retargetTimer -= dt;
        if (retargetTimer <= 0f) {
            retargetTimer = 0.6f + random.nextFloat() * 0.8f;
            pickTarget(map);
        }
        setDirection(steerX, steerY);
        return super.tick(map, dt);
    }

    private void pickTarget(GameMap map) {
        // steer toward a random unowned area
        int gx = map.randomTileX();
        int gy = map.randomTileY();
        float wx = (gx + 0.5f) * GameMap.TILE_SIZE;
        float wy = (gy + 0.5f) * GameMap.TILE_SIZE;
        float dx = wx - x;
        float dy = wy - y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            steerX = 0f;
            steerY = 0f;
            return;
        }
        steerX = dx / len;
        steerY = dy / len;
    }
}