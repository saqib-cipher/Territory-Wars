package com.territorywars.game.tiles;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * A single tile of the capture grid.
 *
 * <p>{@code capture} represents the captured fraction (0..1). Walking on a
 * neutral tile raises it to 1 (captured). Walking on an enemy tile lowers its
 * capture fraction until it flips to yours. Tile instances are pooled to avoid
 * GC pressure during 60 FPS gameplay.</p>
 */
public class Tile {

    public int gridX;
    public int gridY;
    public TileOwner owner = TileOwner.NEUTRAL;
    public float capture = 0f;          // 0 = none, 1 = fully captured by owner
    public boolean lockedByServer;      // authoritative update pending

    /** Static colours, configured once from resources. */
    public static int colorNeutral = Color.rgb(55, 71, 79);
    public static int colorPlayer = Color.rgb(68, 138, 255);
    public static int colorEnemy = Color.rgb(255, 82, 82);
    public static int colorTeamA = Color.rgb(0, 200, 83);
    public static int colorTeamB = Color.rgb(255, 109, 0);

    public static void configureColors(int neutral, int player, int enemy, int teamA, int teamB) {
        Tile.colorNeutral = neutral;
        Tile.colorPlayer = player;
        Tile.colorEnemy = enemy;
        Tile.colorTeamA = teamA;
        Tile.colorTeamB = teamB;
    }

    /** Reset pooled instance to defaults. */
    public void reset(int gridX, int gridY) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.owner = TileOwner.NEUTRAL;
        this.capture = 0f;
        this.lockedByServer = false;
    }

    /** True when the tile is fully owned by the given owner. */
    public boolean isOwnedBy(TileOwner o) {
        return owner == o && capture >= 1f;
    }

    /**
     * Applies a capture step from the given aggressor. Neutral tiles gain;
     * enemy tiles first deplete their local capture before flipping.
     *
     * @return true when the tile changed owner
     */
    public boolean applyCaptureStep(TileOwner aggressor, float strength) {
        if (owner == aggressor) {
            if (capture < 1f) {
                capture = Math.min(1f, capture + strength);
                return false;
            }
            return false;
        }
        if (owner == TileOwner.NEUTRAL) {
            capture = Math.min(1f, capture + strength);
            if (capture >= 1f) {
                owner = aggressor;
                capture = 1f;
                return true;
            }
            return false;
        }
        // stealing enemy territory: their capture shrinks, then flips to us
        capture -= strength * 2f;
        if (capture <= 0f) {
            owner = aggressor;
            capture = Math.min(1f, strength);
            return true;
        }
        capture += strength;
        if (capture > 1f) capture = 1f;
        return false;
    }

    /** Colour blended from the owner colour by capture progress. */
    public int resolveColor() {
        int base = colorOf(owner);
        if (capture < 1f) {
            base = blend(colorNeutral, base, capture);
        }
        return base;
    }

    public static int colorOf(TileOwner o) {
        switch (o) {
            case TEAM_A: return colorTeamA;
            case TEAM_B: return colorTeamB;
            case ENEMY: return colorEnemy;
            case PLAYER: return colorPlayer;
            default: return colorNeutral;
        }
    }

    /** Draws the tile as a filled square at world position (px, py). */
    public void draw(Paint paint, Canvas canvas, float px, float py, float size) {
        paint.setColor(resolveColor());
        canvas.drawRect(px, py, px + size, py + size, paint);
    }

    /** Linear colour blend, alpha-aware. */
    public static int blend(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (from >> 24) & 0xFF, r = (from >> 16) & 0xFF,
                g = (from >> 8) & 0xFF, b = from & 0xFF;
        int a2 = (to >> 24) & 0xFF, r2 = (to >> 16) & 0xFF,
                g2 = (to >> 8) & 0xFF, b2 = to & 0xFF;
        return Color.argb(
                (int) (a + (a2 - a) * t),
                (int) (r + (r2 - r) * t),
                (int) (g + (g2 - g) * t),
                (int) (b + (b2 - b) * t));
    }
}