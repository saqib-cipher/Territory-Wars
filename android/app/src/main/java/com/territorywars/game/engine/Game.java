package com.territorywars.game.engine;

import com.territorywars.game.entities.Player;
import com.territorywars.game.map.GameMap;
import com.territorywars.game.powerups.GamePowerup;
import com.territorywars.game.powerups.PowerupSpawner;
import com.territorywars.game.tiles.TileOwner;
import com.territorywars.models.GameMode;
import com.territorywars.models.PowerupType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Authoritative local game state (mirrors the server's authoritative room for
 * offline/practice mode, and runs prediction locally during online matches).
 *
 * <p>The server is the source of truth in online rooms; the client runs this
 * engine for rendering + prediction and reconciles with {@code scoreUpdate}
 * events.</p>
 */
public class Game {

    public final GameMode mode;
    public final GameMap map;
    public final Map<String, Player> players = new LinkedHashMap<>();
    public final List<GamePowerup> powerups = new ArrayList<>();
    public final PowerupSpawner powerupSpawner;

    public long matchStartMillis;
    public long matchDurationMillis;
    public long timeLeftMillis;
    public boolean finished;

    public Player localPlayer;
    private final Random random = new Random();

    public Game(GameMode mode, String mapId, long matchDurationMillis) {
        this.mode = mode;
        this.map = new GameMap();
        this.matchDurationMillis = matchDurationMillis;
        this.timeLeftMillis = matchDurationMillis;
        this.powerupSpawner = new PowerupSpawner(map);
    }

    public void startMatch(long now, int playerCount) {
        matchStartMillis = now;
        timeLeftMillis = matchDurationMillis;
        finished = false;
        spawnPlayers(now, playerCount);
    }

    /** Randomly spawns players with a small pre-owned island each. */
    public void spawnPlayers(long now, int count) {
        int margin = 6;
        for (int i = 0; i < count; i++) {
            float wx = (margin + random.nextFloat() * (map.getWidth() - 2 * margin))
                    * GameMap.TILE_SIZE;
            float wy = (margin + random.nextFloat() * (map.getHeight() - 2 * margin))
                    * GameMap.TILE_SIZE;
            Player p = new Player("p" + i, "Player " + (i + 1), i == 0 ? TileOwner.PLAYER : TileOwner.ENEMY);
            p.spawn(wx, wy);
            map.grantSpawnIsland(map.toGridX(wx), map.toGridY(wy), 2, p.ownerColor);
            players.put(p.id, p);
            if (i == 0) localPlayer = p;
        }
    }

    /** Spawns a bot-controlled enemy. */
    public void spawnBot(String id, String username, float wx, float wy) {
        Player bot = new Player(id, username, TileOwner.ENEMY);
        bot.spawn(wx, wy);
        map.grantSpawnIsland(map.toGridX(wx), map.toGridY(wy), 2, TileOwner.ENEMY);
        players.put(id, bot);
    }

    /** Advances the whole simulation by dt seconds. */
    public void tick(float dt, long now) {
        if (finished) return;

        timeLeftMillis = matchDurationMillis - (now - matchStartMillis);
        if (timeLeftMillis <= 0) {
            timeLeftMillis = 0;
            finished = true;
            return;
        }

        for (Player p : players.values()) {
            p.tick(map, dt);
        }

        powerupSpawner.update(now, powerups);
        applyPowerupPickups(now);
    }

    /** Distance check between player centres and power-up tiles. */
    private void applyPowerupPickups(long now) {
        List<GamePowerup> expired = new ArrayList<>();
        for (GamePowerup p : powerups) {
            if (p.isExpired(now)) {
                expired.add(p);
                continue;
            }
            for (Player player : players.values()) {
                if (Math.hypot(player.x - p.x, player.y - p.y) < Player.RADIUS * 2.2f) {
                    applyPowerup(player, p);
                    expired.add(p);
                    break;
                }
            }
        }
        powerups.removeAll(expired);
    }

    private void applyPowerup(Player player, GamePowerup powerup) {
        switch (powerup.type) {
            case HEAL:
                player.heal(50);
                break;
            default:
                player.grantEffect(powerup.type, powerup.type.getDurationMillis() / 1000f);
                break;
        }
    }

    public Player winner() {
        Player best = null;
        for (Player p : players.values()) {
            if (best == null || p.score > best.score) best = p;
        }
        return best;
    }

    public int myRank() {
        int rank = 1;
        for (Player p : players.values()) {
            if (p != localPlayer && p.score > localPlayer.score) rank++;
        }
        return rank;
    }

    /** Random power-up id generator for network sync. */
    public String newPowerupId() {
        return UUID.randomUUID().toString();
    }
}