package com.routeplanner.util;

import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;
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

    private static final List<Place> PLACES = Arrays.asList(
        // Misthalin
        new Place("Lumbridge Castle", 3208, 3218, 12),
        new Place("Lumbridge", 3222, 3218, 40),
        new Place("Draynor Village", 3093, 3250, 35),
        new Place("Draynor Manor", 3108, 3352, 20),
        new Place("Wizards' Tower", 3109, 3162, 15),
        new Place("Grand Exchange", 3165, 3487, 20),
        new Place("Varrock", 3213, 3424, 60),
        new Place("Champions' Guild", 3191, 3362, 12),
        new Place("Lumber Yard", 3305, 3490, 20),
        new Place("Digsite", 3360, 3410, 35),
        new Place("Edgeville", 3093, 3493, 25),
        new Place("Barbarian Village", 3082, 3420, 20),
        // Asgarnia
        new Place("Port Sarim", 3023, 3208, 30),
        new Place("Rimmington", 2957, 3214, 25),
        new Place("Falador", 2965, 3380, 55),
        new Place("Taverley", 2895, 3450, 30),
        new Place("Burthorpe", 2900, 3545, 25),
        new Place("Entrana", 2834, 3335, 30),
        // Kharidian Desert
        new Place("Al Kharid", 3293, 3185, 45),
        new Place("Shantay Pass", 3304, 3120, 12),
        new Place("Pollnivneach", 3355, 2970, 30),
        new Place("Nardah", 3420, 2910, 30),
        // Kandarin
        new Place("Catherby", 2810, 3440, 30),
        new Place("Camelot Castle", 2758, 3500, 20),
        new Place("Seers' Village", 2710, 3480, 35),
        new Place("Fishing Guild", 2605, 3415, 20),
        new Place("East Ardougne", 2662, 3305, 55),
        new Place("West Ardougne", 2530, 3300, 40),
        new Place("Witchaven", 2720, 3285, 20),
        new Place("Port Khazard", 2655, 3160, 25),
        new Place("Yanille", 2580, 3090, 35),
        new Place("Castle Wars", 2440, 3090, 25),
        new Place("Tree Gnome Village", 2530, 3170, 30),
        new Place("Tree Gnome Stronghold", 2450, 3420, 70),
        // Fremennik
        new Place("Rellekka", 2660, 3660, 40),
        new Place("Trollheim", 2889, 3681, 40),
        new Place("Miscellania", 2530, 3860, 40),
        new Place("Etceteria", 2610, 3880, 30),
        new Place("Neitiznot", 2335, 3800, 30),
        new Place("Jatizso", 2410, 3800, 30),
        new Place("Lunar Isle", 2100, 3910, 60),
        // Karamja
        new Place("Brimhaven", 2770, 3170, 40),
        new Place("Musa Point", 2915, 3160, 30),
        new Place("Shilo Village", 2850, 2960, 30),
        new Place("Ape Atoll", 2757, 2764, 60),
        // Morytania
        new Place("Canifis", 3493, 3488, 30),
        new Place("Port Phasmatys", 3680, 3480, 30),
        new Place("Barrows", 3565, 3300, 25),
        new Place("Burgh de Rott", 3500, 3210, 25),
        new Place("Fossil Island", 3720, 3810, 60),
        // Tirannwn
        new Place("Lletya", 2340, 3170, 25),
        new Place("Prifddinas", 3260, 6080, 70),
        // Kourend
        new Place("Farming Guild", 1249, 3730, 30),
        new Place("Kourend Castle", 1639, 3673, 30),
        new Place("Hosidius", 1750, 3570, 60),
        new Place("Arceuus", 1690, 3740, 40),
        new Place("Port Piscarilius", 1800, 3720, 40),
        new Place("Wintertodt Camp", 1630, 3945, 30),
        new Place("Mount Karuulm", 1311, 3800, 40),
        // Varlamore
        new Place("Civitas illa Fortis", 1680, 3132, 60)
    );

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

    /** A place name if known, otherwise "x, y". */
    /**
     * " at Lunar Isle" for a step name, or nothing where the map has no name for the spot (most of
     * the open sea), since raw coordinates make a messy name.
     */
    public static String at(WorldPoint p) {
        String name = p == null ? null : nameOf(p);
        return name != null ? " at " + name : "";
    }

    public static String describe(WorldPoint p) {
        String name = nameOf(p);
        return name != null ? name : p.getX() + ", " + p.getY();
    }
}
