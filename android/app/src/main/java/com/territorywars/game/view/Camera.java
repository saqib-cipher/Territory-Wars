package com.territorywars.game.view;

import com.territorywars.game.map.GameMap;

/**
 * Bounded camera that smoothly follows the local player.
 */
public class Camera {

    private static final float SMOOTHING = 0.12f;

    public float x;
    public float y;

    private float targetX;
    private float targetY;
    private int viewportWidth;
    private int viewportHeight;

    /** Starts the camera centred on a world point. */
    public void init(float wx, float wy, int viewportWidth, int viewportHeight) {
        setViewport(viewportWidth, viewportHeight);
        this.x = targetX = wx - viewportWidth / 2f;
        this.y = targetY = wy - viewportHeight / 2f;
    }

    public void setViewport(int w, int h) {
        this.viewportWidth = w;
        this.viewportHeight = h;
    }

    public void follow(float wx, float wy) {
        this.targetX = wx;
        this.targetY = wy;
    }

    /** Smoothly advances the camera toward its target. */
    public void update(float dt) {
        // distance-based damping keeps movement stable across frame rates
        x += (targetX - x) * Math.min(1f, SMOOTHING * dt * 60f);
        y += (targetY - y) * Math.min(1f, SMOOTHING * dt * 60f);
    }

    /** Clamps the camera so the map edges are never visible unless it fits. */
    public void clampToWorld(GameMap map) {
        float worldW = map.getWorldWidth();
        float worldH = map.getWorldHeight();
        x = Math.max(0f, Math.min(x, Math.max(0f, worldW - viewportWidth)));
        y = Math.max(0f, Math.min(y, Math.max(0f, worldH - viewportHeight)));
    }

    public float getX() { return x; }

    public float getY() { return y; }
}