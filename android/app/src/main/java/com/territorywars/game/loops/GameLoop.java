package com.territorywars.game.loops;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import com.territorywars.game.engine.Game;
/**
* Fixed-timestep game loop with interpolation.
*
* <p>Runs on its own thread, calling {@link #onTick(float)} at
* {@link #TARGET_HZ} (60 FPS by default). Using {@code SystemClock.elapsedRealtime}
* keeps timing immune to sleep/standby drift. Renders can be throttled
* separately (e.g. 30/60/120 FPS from Settings) leaving physics fixed.</p>
*/
public class GameLoop implements Runnable {
public static final float TICK_RATE_HZ = 60f;
private static final float DT = 1f / TICK_RATE_HZ;
public interface TickListener {
void onTick(float dt);
}
private final TickListener listener;
private Thread thread;
private volatile boolean running;
private long targetFrameMillis = 16L; // ~60 FPS render pacing
public GameLoop(@NonNull TickListener listener) {
this.listener = listener;
}
public void setRenderFps(int fps) {
targetFrameMillis = Math.max(8, 1000L / Math.max(1, fps));
}
public void start() {
if (running) return;
running = true;
thread = new Thread(this, "GameLoop");
thread.start();
}
public void stop() {
running = false;
if (thread != null) {
thread.interrupt();
thread = null;
}
}
@Override
public void run() {
long frameStart = SystemClock.elapsedRealtime();
long accumulator = 0;
while (running) {
long now = SystemClock.elapsedRealtime();
long elapsed = now - frameStart;
frameStart = now;
if (elapsed > 250L) elapsed = 250L; // clamp spiral-of-death
accumulator += elapsed;
while (accumulator >= 16L) {
listener.onTick(DT);
accumulator -= 16L;
}
long frameTime = SystemClock.elapsedRealtime() - now;
long wait = targetFrameMillis - frameTime;
if (wait > 0) {
try {
Thread.sleep(wait);
} catch (InterruptedException e) {
return;
}
}
}
}
}
