package com.territorywars.game.view;

import com.territorywars.game.entities.Player;
import com.territorywars.game.tiles.Tile;

public interface GameListener {
    void onTileCaptured(Player.TileCaptured capture, Tile tile);
    void onMatchEnded();
}
