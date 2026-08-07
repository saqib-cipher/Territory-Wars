package com.territorywars.game.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.territorywars.game.engine.Game;
import com.territorywars.game.map.GameMap;
import com.territorywars.game.tiles.Tile;

/**
 * Downscaled overview of the whole map. Always shows the full grid so players
 * can locate themselves and contested zones.
 */
public class MiniMapView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Game game;

    public MiniMapView(Context context) {
        this(context, null);
    }

    public MiniMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setGame(Game game) {
        this.game = game;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        GameMap map = game.map;
        float tileW = getWidth() / (float) map.getWidth();
        float tileH = getHeight() / (float) map.getHeight();

        for (int gx = 0; gx < map.getWidth(); gx++) {
            for (int gy = 0; gy < map.getHeight(); gy++) {
                Tile t = map.getTile(gx, gy);
                paint.setColor(Tile.colorOf(t.owner));
                canvas.drawRect(gx * tileW, gy * tileH,
                        (gx + 1) * tileW, (gy + 1) * tileH, paint);
            }
        }

        // local player blip
        if (game.localPlayer != null) {
            paint.setColor(0xFFFFFFFF);
            float px = game.localPlayer.x / map.getWorldWidth() * getWidth();
            float py = game.localPlayer.y / map.getWorldHeight() * getHeight();
            canvas.drawCircle(px, py, Math.max(2f, tileW * 2f), paint);
        }
    }
}