package com.territorywars.game.engine;

import com.territorywars.game.entities.Player;
import com.territorywars.game.map.GameMap;
import com.territorywars.game.powerups.GamePowerup;
import com.territorywars.models.GameMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Game {
    public final GameMap map;
    public final Player localPlayer;
    public final Map<String, Player> players = new HashMap<>();
    public final List<GamePowerup> powerups = new ArrayList<>();
    public GameMode mode = GameMode.CLASSIC;
    public long matchDurationMillis = 120_000L;
    public boolean finished = false;

    public Game(int width, int height, String localPlayerId) {
        this.map = new GameMap(width, height);
        this.localPlayer = new Player(localPlayerId, "Player", width * GameMap.TILE_SIZE / 2f, height * GameMap.TILE_SIZE / 2f);
        this.players.put(localPlayerId, localPlayer);
    }

    public Game(GameMode mode, String mapName, long durationMs) {
        this(50, 50, "local_player");
        this.mode = mode;
        this.matchDurationMillis = durationMs;
    }

    public void startMatch(long seed, int mode) {}
    public Player winner() { return localPlayer; }
    public int myRank() { return 1; }

    public void tick(float dt, long nowMs) {
        if (finished) return;
        for (Player p : players.values()) {
            p.update(dt);
        }
    }
}
