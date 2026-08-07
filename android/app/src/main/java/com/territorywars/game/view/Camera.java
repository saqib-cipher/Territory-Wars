package com.territorywars.game.view;

import com.territorywars.game.map.GameMap;

public class Camera {
    public float x;
    public float y;
    public float viewportW;
    public float viewportH;

    public void setViewport(float w, float h) {
        this.viewportW = w;
        this.viewportH = h;
    }

    public void centerOnPlayer(GameMap map, float px, float py) {
        this.x = px - viewportW / 2f;
        this.y = py - viewportH / 2f;
    }

    public void follow(float px, float py) {
        this.x = px - viewportW / 2f;
        this.y = py - viewportH / 2f;
    }

    public void update(float dt) {}

    public void clampToWorld(GameMap map) {
        if (map == null) return;
        float worldW = map.getWorldWidth();
        float worldH = map.getWorldHeight();
        x = Math.max(0, Math.min(x, worldW - viewportW));
        y = Math.max(0, Math.min(y, worldH - viewportH));
    }
}
