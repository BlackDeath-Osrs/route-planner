package com.routeplanner.transport;

import net.runelite.api.coords.WorldPoint;

/**
 * Picks a tile adjacent to a transition's origin that can actually be walked INTO from that
 * direction, to path to instead of the transition's own tile directly. Staircases/ladders/etc. can
 * sit in collision-map spots (doorway thresholds, stairwell nooks) that don't resolve reliably as
 * a pathfinding destination from a distance -- routing to a known-open adjacent tile, then letting
 * the existing 3-tile "arrived" check take over, sidesteps that entirely.
 *
 * <p>WorldCollisionMap exposes directional movement checks (canMoveNorth/South/East/West from a
 * given tile), not a blanket "is this tile open" query -- a tile can be open space but still be
 * unreachable from a particular side if there's a wall on that edge. So each candidate is checked
 * as "can I move from the origin TOWARD this candidate", which is the actual question that matters
 * for whether a path to that candidate can realistically connect back to the transition.
 *
 * <p>Among the directions that check out, the one CLOSEST TO THE PLAYER is preferred, not a fixed
 * N/S/E/W priority order. A staircase built into a wall typically has one open side facing into
 * the building and another facing outside it; always trying North first (regardless of where the
 * player actually is) can pick the outside tile even when the player is approaching from inside,
 * sending them on a long walk around the building instead of the short walk they are already on.
 */
public final class TransitionAnchor {

    private TransitionAnchor() {}

    private static final int[][] CARDINAL_OFFSETS = {
        { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 }, // N, S, E, W -- order no longer matters for selection,
    };                                             // only for iteration; the closest-to-player wins

    /**
     * @param player        used to prefer whichever open adjacent tile is actually closest to where
     *                      the player is standing, rather than a fixed direction order.
     * @param collisionMap  used to check whether the origin can actually be exited toward each
     *                      candidate direction. Passed in rather than looked up here so this stays
     *                      a pure, easily testable function.
     * @return the walkable-and-reachable tile adjacent to origin that is closest to player, or
     *         origin itself if none of the four cardinal directions check out (better to attempt
     *         the original tile than return null and abandon the transition over an edge case).
     */
    public static WorldPoint pickAnchor(WorldPoint origin, WorldPoint player, com.routeplanner.WorldCollisionMap collisionMap) {
        if (origin == null) return null;
        if (collisionMap == null) return origin; // no way to check, fall back to the origin itself

        int x = origin.getX(), y = origin.getY(), plane = origin.getPlane();

        WorldPoint best = null;
        int bestDistance = Integer.MAX_VALUE;

        boolean[] openDirs = {
            collisionMap.canMoveNorth(x, y, plane),
            collisionMap.canMoveSouth(x, y, plane),
            collisionMap.canMoveEast(x, y, plane),
            collisionMap.canMoveWest(x, y, plane),
        };
        for (int i = 0; i < CARDINAL_OFFSETS.length; i++) {
            if (!openDirs[i]) continue;
            WorldPoint candidate = new WorldPoint(x + CARDINAL_OFFSETS[i][0], y + CARDINAL_OFFSETS[i][1], plane);
            int dist = (player != null) ? player.distanceTo(candidate) : 0;
            if (dist < 0) dist = Integer.MAX_VALUE; // different plane somehow; deprioritize, do not crash
            if (dist < bestDistance) {
                bestDistance = dist;
                best = candidate;
            }
        }

        return best != null ? best : origin; // none of the four directions are open; try the original tile
    }
}
