package com.territorywars.game.particles;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.List;

public class ParticleSystem {
    private static class Particle {
        float x, y, vx, vy, life, maxLife;
        int color;
    }

    private final List<Particle> particles = new ArrayList<>();

    public void burst(float x, float y, int color, int count, float speed) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.x = x;
            p.y = y;
            double angle = Math.random() * Math.PI * 2;
            p.vx = (float) (Math.cos(angle) * speed);
            p.vy = (float) (Math.sin(angle) * speed);
            p.maxLife = 0.4f + (float) Math.random() * 0.4f;
            p.life = p.maxLife;
            p.color = color;
            particles.add(p);
        }
    }

    public void tick(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.life -= dt;
            if (p.life <= 0) {
                particles.remove(i);
            }
        }
    }

    public void draw(Canvas canvas, Paint paint, float camX, float camY) {
        for (Particle p : particles) {
            paint.setColor(p.color);
            paint.setAlpha((int) (255 * (p.life / p.maxLife)));
            canvas.drawCircle(p.x - camX, p.y - camY, 4f, paint);
        }
        paint.setAlpha(255);
    }
}
