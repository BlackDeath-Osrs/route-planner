package com.routeplanner.util;

import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A short walk over the loaded scene's own collision data. Works where the world map pathfinders
 * can't, inside instances such as the player's house, whose tiles aren't in any static map.
 */
public final class ScenePath {

    private ScenePath() {}

    // N, S, E, W: step, the wall flag on the tile being left
    private static final int[][] DIRS = {
        {0, 1, CollisionDataFlag.BLOCK_MOVEMENT_NORTH},
        {0, -1, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH},
        {1, 0, CollisionDataFlag.BLOCK_MOVEMENT_EAST},
        {-1, 0, CollisionDataFlag.BLOCK_MOVEMENT_WEST},
    };

    /**
     * Tiles from the player's scene tile to one beside any of {@code objects}, or an empty list when
     * already there or no way is found. The path starts with the player's own tile.
     */
    public static List<WorldPoint> toObjects(WorldView wv, int startX, int startY, List<GameObject> objects) {
        int plane = wv.getPlane();
        CollisionData[] maps = wv.getCollisionMaps();
        if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null) return Collections.emptyList();
        int[][] flags = maps[plane].getFlags();
        int w = flags.length, h = flags[0].length;
        if (!inside(startX, startY, w, h)) return Collections.emptyList();

        // Goals: the ring of tiles around each object's footprint.
        boolean[][] goal = new boolean[w][h];
        for (GameObject obj : objects) {
            int minX = obj.getSceneMinLocation().getX(), minY = obj.getSceneMinLocation().getY();
            int maxX = obj.getSceneMaxLocation().getX(), maxY = obj.getSceneMaxLocation().getY();
            for (int x = minX - 1; x <= maxX + 1; x++) {
                for (int y = minY - 1; y <= maxY + 1; y++) {
                    boolean footprint = x >= minX && x <= maxX && y >= minY && y <= maxY;
                    if (!footprint && inside(x, y, w, h)) goal[x][y] = true;
                }
            }
        }
        if (goal[startX][startY]) return Collections.emptyList();

        int[] prev = new int[w * h];
        Arrays.fill(prev, -1);
        int start = startX * h + startY;
        prev[start] = start;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            int x = cur / h, y = cur % h;
            if (goal[x][y]) return walkBack(wv, prev, cur, h, plane);
            for (int[] d : DIRS) {
                int nx = x + d[0], ny = y + d[1];
                if (!inside(nx, ny, w, h) || prev[nx * h + ny] != -1) continue;
                if ((flags[x][y] & d[2]) != 0 || (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0) continue;
                prev[nx * h + ny] = cur;
                queue.add(nx * h + ny);
            }
        }
        return Collections.emptyList();
    }

    private static List<WorldPoint> walkBack(WorldView wv, int[] prev, int end, int h, int plane) {
        List<WorldPoint> path = new ArrayList<>();
        for (int cur = end; ; cur = prev[cur]) {
            path.add(WorldPoint.fromScene(wv, cur / h, cur % h, plane));
            if (prev[cur] == cur) break;
        }
        Collections.reverse(path);
        return path;
    }

    private static boolean inside(int x, int y, int w, int h) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }
}
