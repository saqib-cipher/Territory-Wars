package com.territorywars.game.entities;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.territorywars.game.map.GameMap;
import com.territorywars.game.tiles.Tile;
import com.territorywars.game.tiles.TileOwner;
import com.territorywars.models.PowerupType;

/**
 * Character/entity of a player (or bot).
 *
 * <p>Movement is a velocity/position model; per-tile capture happens when the
 * body enters a new grid cell. Effect timers (speed, shield, ...) live here
 * and are advanced by {@code GameLoop}.</p>
 */
public class Player {

    public static final float RADIUS = GameMap.TILE_SIZE * 0.32f;
    public static final float BASE_SPEED = 240f;         // world units / second
    private static final float CAPTURE_STRENGTH = 1.4f;  // capture fraction per second

    public String id;
    public String username;
    public TileOwner ownerColor;

    public float x;
    public float y;
    public float vx;
    public float vy;

    public int score;
    public int tilesCaptured;
    public int coins;
    public int health = 100;

    private int lastGridX = -1;
    private int lastGridY = -1;

    private final PowerupEffects effects = new PowerupEffects();
    private float pendingCapture = 0f;
    private final java.util.ArrayDeque<TileCaptured> queuedCaptures = new java.util.ArrayDeque<>();

    public Player(String id, String username, TileOwner ownerColor) {
        this.id = id;
        this.username = username;
        this.ownerColor = ownerColor;
    }

    public void spawn(float worldX, float worldY) {
        this.x = worldX;
        this.y = worldY;
        this.vx = 0;
        this.vy = 0;
        this.health = 100;
        this.pendingCapture = 0f;
        this.queuedCaptures.clear();
        this.lastGridX = -1;
        this.lastGridY = -1;
    }

    /** Sets a direction vector; normalises it to a unit step. */
    public void setDirection(float dx, float dy) {
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 0.0001f) {
            this.vx = dx / len;
            this.vy = dy / len;
        } else {
            this.vx = 0f;
            this.vy = 0f;
        }
    }

    public float speed() {
        float eff = BASE_SPEED;
        if (effects.isActive(PowerupType.SPEED)) eff *= 1.8f;
        if (effects.isActive(PowerupType.FREEZE)) eff *= 0.20f;
        return eff;
    }

    public boolean isInvincible() {
        return effects.isActive(PowerupType.INVINCIBILITY)
                || effects.isActive(PowerupType.SHIELD);
    }

    public void grantEffect(PowerupType type, float durationSeconds) {
        effects.grant(type, durationSeconds);
    }

    public void heal(int amount) {
        health = Math.min(100, health + amount);
    }

    /**
     * Advances movement and performs tile capture when entering a new cell.
     *
     * @return whether new captures were queued for the network sync
     */
    public boolean tick(GameMap map, float dt) {
        effects.tick(dt);

        float speed = speed();
        float nextX = x + vx * speed * dt;
        float nextY = y + vy * speed * dt;
        x = clamp(nextX, RADIUS, map.getWorldWidth() - RADIUS);
        y = clamp(nextY, RADIUS, map.getWorldHeight() - RADIUS);

        int gx = map.toGridX(x);
        int gy = map.toGridY(y);
        if (gx == lastGridX && gy == lastGridY) return false;

        lastGridX = gx;
        lastGridY = gy;
        Tile tile = map.getTile(gx, gy);
        if (tile == null || tile.lockedByServer) return false;

        boolean flipped = tile.applyCaptureStep(ownerColor, CAPTURE_STRENGTH * dt);
        if (flipped) {
            tilesCaptured++;
            score += isInvincible() ? 20 : 10;
            tile.lockedByServer = true;
            queuedCaptures.add(new TileCaptured(gx, gy, ownerColor));
            return true;
        }
        pendingCapture += CAPTURE_STRENGTH * dt;
        while (pendingCapture >= 1f) {
            pendingCapture -= 1f;
            score += 1;
        }
        return false;
    }

    /** Removes and returns the captures pending server confirmation. */
    public java.util.ArrayDeque<TileCaptured> drainPendingCaptures() {
        java.util.ArrayDeque<TileCaptured> out = new java.util.ArrayDeque<>(queuedCaptures);
        queuedCaptures.clear();
        return out;
    }

    public void draw(Paint paint, Canvas canvas, float camX, float camY) {
        paint.setColor(isInvincible() ? Color.CYAN : Tile.colorOf(ownerColor));
        canvas.drawCircle(x - camX, y - camY, RADIUS, paint);

        paint.setColor(Color.WHITE);
        canvas.drawCircle(x - camX + vx * RADIUS * 0.75f,
                y - camY + vy * RADIUS * 0.75f, RADIUS * 0.3f, paint);
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    /** A captured tile slice awaiting the server ack. */
    public static class TileCaptured {
        public final int gx;
        public final int gy;
        public final TileOwner owner;

        public TileCaptured(int gx, int gy, TileOwner owner) {
            this.gx = gx;
            this.gy = gy;
            this.owner = owner;
        }
    }

    /** Tracks timed power-up effects. */
    public static class PowerupEffects {
        private final java.util.EnumMap<PowerupType, Float> timers =
                new java.util.EnumMap<>(PowerupType.class);

        public void grant(PowerupType type, float durationSeconds) {
            timers.put(type, durationSeconds);
        }

        public boolean isActive(PowerupType type) {
            Float t = timers.get(type);
            return t != null && t > 0f;
        }

        public void tick(float dt) {
            java.util.Iterator<java.util.Map.Entry<PowerupType, Float>> it =
                    timers.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<PowerupType, Float> e = it.next();
                float v = e.getValue() - dt;
                if (v <= 0f) it.remove();
                else e.setValue(v);
            }
        }
    }
}