package com.territorywars.game.particles;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayDeque;

/**
 * Pooled, GPU-friendly particle system used for capture sparks and pickups.
 * Particles are simple circles; the pool avoids allocations in the render
 * thread.
 */
public class ParticleSystem {

    public static final int MAX_PARTICLES = 256;

    private final ArrayDeque<Particle> pool = new ArrayDeque<>();
    private final Particle[] active = new Particle[MAX_PARTICLES];
    private int activeCount = 0;

    public ParticleSystem() {
        for (int i = 0; i < MAX_PARTICLES; i++) pool.push(new Particle());
    }

    /** Emits a burst at world coordinates with the given colour. */
    public void burst(float wx, float wy, int color, int count, float speed) {
        for (int i = 0; i < count && activeCount < MAX_PARTICLES; i++) {
            Particle p = pool.poll();
            if (p == null) continue;
            p.init(wx, wy, color, speed);
            active[activeCount++] = p;
        }
    }

    public void tick(float dt) {
        int write = 0;
        for (int i = 0; i < activeCount; i++) {
            Particle p = active[i];
            p.life -= dt;
            if (p.life <= 0f) {
                pool.push(p);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            active[write++] = p;
        }
        activeCount = write;
    }

    public void draw(Canvas canvas, Paint paint, float camX, float camY) {
        for (int i = 0; i < activeCount; i++) {
            Particle p = active[i];
            float alpha = Math.max(0f, Math.min(1f, p.life / p.maxLife));
            paint.setColor(com.territorywars.game.tiles.Tile.blend(
                    p.color, 0x00FFFFFF, 1f - alpha));
            canvas.drawCircle(p.x - camX, p.y - camY, p.size * (0.4f + alpha), paint);
        }
    }

    private static class Particle {
        float x, y, vx, vy;
        float size;
        float life, maxLife;
        int color;

        void init(float x, float y, int color, float speed) {
            this.x = x;
            this.y = y;
            double angle = Math.random() * Math.PI * 2;
            float vel = (float) (speed * (0.5 + Math.random()));
            this.vx = (float) (Math.cos(angle) * vel);
            this.vy = (float) (Math.sin(angle) * vel);
            this.size = 2f + (float) Math.random() * 5f;
            this.maxLife = this.life = 0.4f + (float) Math.random() * 0.5f;
            this.color = color;
        }
    }
}