package com.territorywars.game.map;

import com.territorywars.game.tiles.Tile;

public class GameMap {
    public static final float TILE_SIZE = 40f;

    private final int width;
    private final int height;
    private final Tile[][] tiles;

    public GameMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new Tile[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                tiles[x][y] = new Tile(x, y);
            }
        }
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public float getWorldWidth() { return width * TILE_SIZE; }
    public float getWorldHeight() { return height * TILE_SIZE; }

    public Tile getTile(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return null;
        return tiles[x][y];
    }
}
