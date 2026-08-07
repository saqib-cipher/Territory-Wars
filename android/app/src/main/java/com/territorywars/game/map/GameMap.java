package com.territorywars.game.map;

import com.territorywars.game.tiles.Tile;
import com.territorywars.game.tiles.TileOwner;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The capture grid. Tiles are owned by players; players start on a small owned
 * island and expand outward by walking.
 */
public class GameMap {

    public static final int DEFAULT_WIDTH = 64;
    public static final int DEFAULT_HEIGHT = 48;
    public static final float TILE_SIZE = 48f;   // world units per tile

    private final int width;
    private final int height;
    private final Tile[][] tiles;
    private final Random random = new Random();

    public GameMap() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public GameMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new Tile[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                tiles[x][y] = new Tile();
                tiles[x][y].reset(x, y);
            }
        }
        seedNeutralHills();
    }

    /** Gives the map a few pre-owned pockets to fight over. */
    private void seedNeutralHills() {
        // A couple of "capital" tiles owned by nobody but worth bonus points.
        // Concrete map balance is defined server-side; this keeps offline mode fair.
    }

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public float getWorldWidth() { return width * TILE_SIZE; }

    public float getWorldHeight() { return height * TILE_SIZE; }

    public Tile getTile(int gridX, int gridY) {
        if (gridX < 0 || gridY < 0 || gridX >= width || gridY >= height) return null;
        return tiles[gridX][gridY];
    }

    /** Converts world coordinates to grid coordinates (clamped). */
    public int toGridX(float worldX) {
        return clamp((int) Math.floor(worldX / TILE_SIZE), 0, width - 1);
    }

    public int toGridY(float worldY) {
        return clamp((int) Math.floor(worldY / TILE_SIZE), 0, height - 1);
    }

    /** All fully-owned tiles of the given owner. */
    public List<Tile> tilesOwnedBy(TileOwner owner) {
        List<Tile> out = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (tiles[x][y].isOwnedBy(owner)) out.add(tiles[x][y]);
            }
        }
        return out;
    }

    public int countOwnedBy(TileOwner owner) {
        int count = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (tiles[x][y].isOwnedBy(owner)) count++;
            }
        }
        return count;
    }

    /** Marks a player's spawn island as owned. */
    public void grantSpawnIsland(int spawnX, int spawnY, int radius, TileOwner owner) {
        for (int x = spawnX - radius; x <= spawnX + radius; x++) {
            for (int y = spawnY - radius; y <= spawnY + radius; y++) {
                Tile t = getTile(x, y);
                if (t != null) {
                    t.owner = owner;
                    t.capture = 1f;
                }
            }
        }
    }

    /** Random walkable position on the grid (used for powerups / bots). */
    public int randomTileX() { return random.nextInt(width); }

    public int randomTileY() { return random.nextInt(height); }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}