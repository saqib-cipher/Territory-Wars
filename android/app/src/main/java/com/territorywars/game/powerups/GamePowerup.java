package com.territorywars.game.powerups;

public class GamePowerup {
    public final String id;
    public final float x;
    public final float y;
    public final String type;

    public GamePowerup(String id, float x, float y, String type) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.type = type;
    }
}
