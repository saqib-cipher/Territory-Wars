package com.territorywars.game.physics;

import com.territorywars.game.map.GameMap;

/**
 * Axis-oriented collision helpers for the tile grid.
 */
public final class Collision {

    private Collision() {
    }

    /** True when the world point is inside the map bounds. */
    public static boolean insideMap(GameMap map, float x, float y, float pad) {
        return x >= pad && y >= pad
                && x <= map.getWorldWidth() - pad
                && y <= map.getWorldHeight() - pad;
    }

    /**
     * AABB sweep test: returns the largest safe displacement along the axis
     * before hitting the map boundary, given velocity over a frame.
     */
    public static float resolveAxis(float position, float delta,
                                    float min, float max) {
        float next = position + delta;
        return Math.max(min, Math.min(max, next));
    }

    /** Circle distance check (no sqrt needed). */
    public static boolean circlesOverlap(float ax, float ay, float ar,
                                         float bx, float by, float br) {
        float dx = ax - bx;
        float dy = ay - by;
        float r = ar + br;
        return dx * dx + dy * dy <= r * r;
    }
}