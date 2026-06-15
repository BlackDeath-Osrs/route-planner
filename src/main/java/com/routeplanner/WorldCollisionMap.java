package com.routeplanner;

import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads collision-map.zip using the exact same format as Runemoro/shortest-path FlagMap.
 *
 * Index formula (from FlagMap.java decompilation):
 *   index = (plane * 64 * 64 + (y - minY) * 64 + (x - minX)) * 2 + direction
 *
 * Directions: 0 = can travel NORTH from tile, 1 = can travel EAST from tile
 * South is stored as North of the tile below. West is stored as East of the tile to the left.
 * A set bit means movement IS ALLOWED (not blocked).
 */
public class WorldCollisionMap {

    public static final int NORTH = 0;
    public static final int EAST  = 1;

    private static final int REGION_SIZE = 64;

    private static class RegionData {
        final int minX;
        final int minY;
        final BitSet flags;
        final byte planeCount;

        RegionData(int minX, int minY, byte[] data) {
            this.minX = minX;
            this.minY = minY;
            this.flags = BitSet.valueOf(data);
            // planeCount = ceil(flags.size() / 8192)
            int size = flags.size();
            this.planeCount = (byte) ((size + 8192 - 1) / 8192);
        }

        boolean get(int x, int y, int plane, int direction) {
            if (x < minX || x >= minX + REGION_SIZE) return false;
            if (y < minY || y >= minY + REGION_SIZE) return false;
            if (plane < 0 || planeCount <= 0) return false;
            if (direction < 0 || direction >= 2) return false;
            int idx = (plane * REGION_SIZE * REGION_SIZE
                + (y - minY) * REGION_SIZE
                + (x - minX)) * 2 + direction;
            return flags.get(idx);
        }
    }

    // key = regionX | (regionY << 16)
    private final Map<Integer, RegionData> regions = new HashMap<>();

    public WorldCollisionMap(InputStream zipStream) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String[] parts = entry.getName().split("_");
                int regionX = Integer.parseInt(parts[0]);
                int regionY = Integer.parseInt(parts[1]);
                int key = regionX | (regionY << 16);
                int minX = regionX * REGION_SIZE;
                int minY = regionY * REGION_SIZE;
                byte[] data = zis.readAllBytes();
                regions.put(key, new RegionData(minX, minY, data));
                zis.closeEntry();
            }
        }
    }

    /**
     * Returns true if movement NORTH from (x,y,plane) is allowed.
     */
    public boolean canMoveNorth(int x, int y, int plane) {
        return getFlag(x, y, plane, NORTH);
    }

    /**
     * Returns true if movement SOUTH from (x,y,plane) is allowed.
     * South from (x,y) = North from (x, y-1).
     */
    public boolean canMoveSouth(int x, int y, int plane) {
        return getFlag(x, y - 1, plane, NORTH);
    }

    /**
     * Returns true if movement EAST from (x,y,plane) is allowed.
     */
    public boolean canMoveEast(int x, int y, int plane) {
        return getFlag(x, y, plane, EAST);
    }

    /**
     * Returns true if movement WEST from (x,y,plane) is allowed.
     * West from (x,y) = East from (x-1, y).
     */
    public boolean canMoveWest(int x, int y, int plane) {
        return getFlag(x - 1, y, plane, EAST);
    }

    private boolean getFlag(int x, int y, int plane, int direction) {
        int regionX = x / REGION_SIZE;
        int regionY = y / REGION_SIZE;
        int key = regionX | (regionY << 16);
        RegionData region = regions.get(key);
        if (region == null) return true; // unknown = assume walkable
        return region.get(x, y, plane, direction);
    }

    public boolean isLoaded() {
        return !regions.isEmpty();
    }
}
