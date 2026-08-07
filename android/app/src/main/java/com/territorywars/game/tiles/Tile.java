package com.territorywars.game.tiles;

import android.graphics.Canvas;
import android.graphics.Paint;

public class Tile {
    public final int gridX;
    public final int gridY;
    public TileOwner owner = TileOwner.NEUTRAL;

    public Tile(int gridX, int gridY) {
        this.gridX = gridX;
        this.gridY = gridY;
    }

    public static int colorOf(TileOwner owner) {
        if (owner == null) return 0xFF1E1E2C;
        if (owner == TileOwner.ME) return 0xFF4CAF50;
        if (owner == TileOwner.ENEMY) return 0xFFF44336;
        return 0xFF1E1E2C;
    }

    public void draw(Paint paint, Canvas canvas, float screenX, float screenY, float size) {
        paint.setColor(colorOf(owner));
        canvas.drawRect(screenX, screenY, screenX + size, screenY + size, paint);
    }
}
