package com.territorywars.game.entities;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.territorywars.game.tiles.Tile;
import com.territorywars.game.tiles.TileOwner;

import java.util.ArrayDeque;

public class Player {
    public static final float RADIUS = 18f;

    public final String id;
    public final String name;
    public float x;
    public float y;
    public float dirX;
    public float dirY;
    public float speed = 200f;
    public TileOwner role = TileOwner.ME;
    public int score = 0;
    public int tilesCaptured = 0;

    private final ArrayDeque<TileCaptured> pendingCaptures = new ArrayDeque<>();

    public static class TileCaptured {
        public final int gx;
        public final int gy;
        public TileCaptured(int gx, int gy) {
            this.gx = gx;
            this.gy = gy;
        }
    }

    public Player(String id, String name, float startX, float startY) {
        this.id = id;
        this.name = name;
        this.x = startX;
        this.y = startY;
    }

    public void setDirection(float dx, float dy) {
        this.dirX = dx;
        this.dirY = dy;
    }

    public void update(float dt) {
        x += dirX * speed * dt;
        y += dirY * speed * dt;
    }

    public ArrayDeque<TileCaptured> drainPendingCaptures() {
        ArrayDeque<TileCaptured> copy = new ArrayDeque<>(pendingCaptures);
        pendingCaptures.clear();
        return copy;
    }

    public void addCapture(int gx, int gy) {
        pendingCaptures.add(new TileCaptured(gx, gy));
        tilesCaptured++;
        score += 10;
    }

    public void draw(Paint paint, Canvas canvas, float camX, float camY) {
        paint.setColor(Tile.colorOf(role));
        canvas.drawCircle(x - camX, y - camY, RADIUS, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(24f);
        canvas.drawText(name != null ? name : "Player", x - camX - 20, y - camY - RADIUS - 5, paint);
    }
}
