package com.routeplanner.sailing;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldEntityConfig;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Where the player really is while sailing. On a boat the player stands in the boat's own world
 * view, so {@code getWorldLocation()} gives a tile on the boat's deck, not on the sea. The boat
 * itself is a world entity in the top-level world, and its position is the one that matters for
 * routing, arrival and drawing.
 */
public final class Sailing {

    private Sailing() {}


    /** The boat the player is on, or null when they're not on one. */
    public static WorldEntity boat(Client client) {
        Player player = client.getLocalPlayer();
        if (player == null) return null;
        WorldView wv = player.getWorldView();
        if (wv == null || wv.isTopLevel()) return null;
        WorldView top = client.getTopLevelWorldView();
        if (top == null || top.worldEntities() == null) return null;
        return top.worldEntities().byIndex(wv.getId());
    }

    public static boolean isSailing(Client client) {
        return boat(client) != null;
    }

    /** The player's tile in the top-level world: the boat's tile while sailing. Null if logged out. */
    public static WorldPoint position(Client client) {
        Player player = client.getLocalPlayer();
        if (player == null) return null;
        WorldEntity boat = boat(client);
        if (boat != null) {
            LocalPoint lp = boat.getLocalLocation();
            // Mid-hop the world view can be rebuilding; the deck tile is better than nothing.
            if (lp != null && client.getWorldView(lp.getWorldView()) != null) {
                return WorldPoint.fromLocalInstance(client, lp);
            }
        }
        return player.getWorldLocation();
    }

    /**
     * How many tiles {@code tile} is from the player's boat: 0 when the hull covers it, otherwise the
     * gap to the nearest edge of the hull. -1 when the player isn't on a boat, or its size isn't known.
     * While sailing the boat stands in for the player, and a sloop is ten tiles long, so arriving is
     * measured from the hull rather than from the boat's centre.
     */
    public static int hullDistance(Client client, WorldPoint tile) {
        WorldEntity boat = boat(client);
        if (boat == null || tile == null) return -1;
        WorldEntityConfig hull = boat.getConfig();
        LocalPoint lp = boat.getLocalLocation();
        WorldView top = client.getTopLevelWorldView();
        if (hull == null || lp == null || top == null || hull.getBoundsWidth() <= 0 || hull.getBoundsHeight() <= 0) return -1;
        if (tile.getPlane() != top.getPlane()) return Integer.MAX_VALUE;

        // From the boat's centre to the middle of the tile, in local units (128 per tile)
        int dx = tile.getX() * Perspective.LOCAL_TILE_SIZE + Perspective.LOCAL_HALF_TILE_SIZE
            - (lp.getX() + top.getBaseX() * Perspective.LOCAL_TILE_SIZE);
        int dy = tile.getY() * Perspective.LOCAL_TILE_SIZE + Perspective.LOCAL_HALF_TILE_SIZE
            - (lp.getY() + top.getBaseY() * Perspective.LOCAL_TILE_SIZE);
        // Into the boat's own frame, undoing the turn the game gives its model (see the bounds getters)
        int angle = boat.getOrientation() & 2047;
        long cos = Perspective.COSINE[angle], sin = Perspective.SINE[angle];
        long mx = (dx * cos - dy * sin) >> 16;
        long my = (dx * sin + dy * cos) >> 16;
        long outX = Math.max(0, Math.abs(mx - hull.getBoundsX()) - hull.getBoundsWidth() / 2);
        long outY = Math.max(0, Math.abs(my - hull.getBoundsY()) - hull.getBoundsHeight() / 2);
        // The tile's own half-width counts as touching
        long gap = Math.max(0, Math.max(outX, outY) - Perspective.LOCAL_HALF_TILE_SIZE);
        return (int) ((gap + Perspective.LOCAL_TILE_SIZE - 1) / Perspective.LOCAL_TILE_SIZE);
    }

    /**
     * Whether the player is within {@code tiles} of {@code tile}: from the hull while sailing,
     * otherwise from where they stand.
     */
    public static boolean isWithin(Client client, WorldPoint tile, int tiles) {
        int hull = hullDistance(client, tile);
        if (hull >= 0) return hull <= tiles;
        WorldPoint at = position(client);
        return at != null && tile != null && at.distanceTo(tile) <= tiles;
    }
}
