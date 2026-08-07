package com.territorywars.game.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.territorywars.game.engine.Game;
import com.territorywars.game.entities.Player;
import com.territorywars.game.loops.GameLoop;
import com.territorywars.game.map.GameMap;
import com.territorywars.game.particles.ParticleSystem;
import com.territorywars.game.powerups.GamePowerup;
import com.territorywars.game.tiles.Tile;

import java.util.ArrayDeque;

/**
 * Main game surface: owns the fixed-timestep engine tick and the Canvas render
 * pipeline. Input is drag-to-steer (virtual joystick continuous delta).
 */
public class GameView extends View implements GameLoop.TickListener {

    /** Callbacks forwarded to the HUD / network layer. */
    public interface GameListener {
        void onTileCaptured(Player.TileCaptured capture, Tile tile);

        void onMatchEnded();
    }

    private Game game;
    private GameLoop loop;
    private GameListener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ParticleSystem particles = new ParticleSystem();
    private final Camera camera = new Camera();

    private boolean dragging;
    private float lastTouchX;
    private float lastTouchY;
    private float dragSteerX;
    private float dragSteerY;

    public GameView(Context context) {
        this(context, null);
    }

    public GameView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setGame(Game game) {
        this.game = game;
        if (game.localPlayer != null) {
            camera.centerOnPlayer(game.map, game.localPlayer.x, game.localPlayer.y);
        }
    }

    public void setGameListener(GameListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (loop != null) return;
        loop = new GameLoop(this);
        loop.start();
    }

    public void stop() {
        if (loop != null) {
            loop.stop();
            loop = null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        camera.setViewport(w, h);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        drawTiles(canvas);
        drawPowerups(canvas);
        for (Player p : game.players.values()) {
            p.draw(paint, canvas, camera.x, camera.y);
        }
        particles.draw(canvas, paint, camera.x, camera.y);
        drawGridLines(canvas);
    }

    /** Viewport-culled tile rendering for cheap 60 FPS draws. */
    private void drawTiles(Canvas canvas) {
        GameMap map = game.map;
        float tileSize = GameMap.TILE_SIZE;
        int x0 = Math.max(0, (int) Math.floor(camera.x / tileSize));
        int y0 = Math.max(0, (int) Math.floor(camera.y / tileSize));
        int x1 = Math.min(map.getWidth() - 1, (int) Math.ceil((camera.x + getWidth()) / tileSize));
        int y1 = Math.min(map.getHeight() - 1, (int) Math.ceil((camera.y + getHeight()) / tileSize));

        for (int gx = x0; gx <= x1; gx++) {
            for (int gy = y0; gy <= y1; gy++) {
                Tile t = map.getTile(gx, gy);
                if (t != null) {
                    t.draw(paint, canvas,
                            gx * tileSize - camera.x,
                            gy * tileSize - camera.y,
                            tileSize);
                }
            }
        }
    }

    private void drawPowerups(Canvas canvas) {
        paint.setColor(Color.rgb(255, 214, 0)); // power-up yellow
        for (GamePowerup p : game.powerups) {
            canvas.drawCircle(p.x - camera.x, p.y - camera.y,
                    Player.RADIUS * 0.7f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(p.x - camera.x, p.y - camera.y,
                    Player.RADIUS * 0.9f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawGridLines(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(0x22000000);
        float tileSize = GameMap.TILE_SIZE;
        float startX = camera.x - ((camera.x % tileSize) + tileSize) % tileSize;
        for (float x = startX; x <= camera.x + getWidth(); x += tileSize) {
            canvas.drawLine(x - camera.x, 0, x - camera.x, getHeight(), paint);
        }
        float startY = camera.y - ((camera.y % tileSize) + tileSize) % tileSize;
        for (float y = startY; y <= camera.y + getHeight(); y += tileSize) {
            canvas.drawLine(0, y - camera.y, getWidth(), y - camera.y, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void onTick(float dt) {
        if (game == null || game.localPlayer == null) return;

        Player local = game.localPlayer;

        // Continuous-steer input: move in the accumulated drag direction.
        local.setDirection(-dragSteerX, -dragSteerY);

        game.tick(dt, System.currentTimeMillis());

        // Drain locally captured tiles into the network/HUD sink.
        ArrayDeque<Player.TileCaptured> caps = local.drainPendingCaptures();
        while (!caps.isEmpty()) {
            Player.TileCaptured c = caps.poll();
            Tile tile = game.map.getTile(c.gx, c.gy);
            if (tile != null) {
                if (listener != null) listener.onTileCaptured(c, tile);
                particles.burst(tileCenterX(tile), tileCenterY(tile),
                        Tile.colorOf(tile.owner), 10, 140f);
            }
        }

        if (game.finished && listener != null) listener.onMatchEnded();

        camera.follow(local.x, local.y);
        camera.update(dt);
        camera.clampToWorld(game.map);

        particles.tick(dt);
        invalidate();
    }

    private float tileCenterX(Tile t) {
        return (t.gridX + 0.5f) * GameMap.TILE_SIZE;
    }

    private float tileCenterY(Tile t) {
        return (t.gridY + 0.5f) * GameMap.TILE_SIZE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    // Dragging is relative: accumulate then apply inertia-free.
                    dragSteerX = clampSteer(dragSteerX + dx);
                    dragSteerY = clampSteer(dragSteerY + dy);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                dragSteerX = 0f;
                dragSteerY = 0f;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private float clampSteer(float v) {
        return Math.max(-1f, Math.min(1f, v / 28f));
    }
}