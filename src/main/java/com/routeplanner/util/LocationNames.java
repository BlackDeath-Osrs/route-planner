package com.routeplanner.util;

import net.runelite.api.coords.WorldPoint;
import java.util.List;

/**
 * Common names for places, so recorded steps read "Go to Varrock" instead of raw coordinates.
 *
 * Each place is a centre tile plus a radius (in tiles, square). When areas overlap, the
 * smallest one containing the tile wins, so "Grand Exchange" beats "Varrock". Centres are
 * approximate surface positions; anything that matches nothing (dungeons, the wilderness,
 * unlisted spots) falls back to coordinates, and every name can be edited in the review.
 */
public final class LocationNames {

    private LocationNames() {}

    private static final class Place {
        final String name;
        final int x, y, radius;
        Place(String name, int x, int y, int radius) {
            this.name = name; this.x = x; this.y = y; this.radius = radius;
        }
    }

    private static final List<Place> PLACES = DataFiles.load("location_names.tsv", r -> new Place(r[0], DataFiles.num(r[1]), DataFiles.num(r[2]), DataFiles.num(r[3])));

    /** The best-known name for a tile, or null when it isn't in any listed place. */
    public static String nameOf(WorldPoint p) {
        if (p == null) return null;
        Place best = null;
        for (Place place : PLACES) {
            int d = Math.max(Math.abs(p.getX() - place.x), Math.abs(p.getY() - place.y));
            if (d <= place.radius && (best == null || place.radius < best.radius)) best = place;
        }
        return best == null ? null : best.name;
    }

    /**
     * " at Lunar Isle" for a step name, or nothing where the map has no name for the spot (most of
     * the open sea), since raw coordinates make a messy name.
     */
    public static String at(WorldPoint p) {
        String name = p == null ? null : nameOf(p);
        return name != null ? " at " + name : "";
    }

    /** A place name if known, otherwise "x, y". */
    public static String describe(WorldPoint p) {
        String name = nameOf(p);
        return name != null ? name : p.getX() + ", " + p.getY();
    }
}
