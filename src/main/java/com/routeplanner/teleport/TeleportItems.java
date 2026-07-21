package com.routeplanner.teleport;

import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;
import java.util.List;

/**
 * Item-teleport data. Each entry is one pickable destination; entries for the same
 * physical item share the same chargeIds set (all charge states) and iconId.
 *
 * Charge id sets (verified against OSRS Wiki Item_IDs, July 2026):
 *   Amulet of glory : 1706,1708,1710,1712,11976,11978            (charges 1..6)   icon 11978
 *   Games necklace  : 3867,3865,3863,3861,3859,3857,3855,3853    (charges 1..8)   icon 3853
 *   Ring of dueling : 2561,2560,2559,2558,2557,2556,2555,2552    (charges 1..8)   icon 2552
 *   Ring of wealth  : 11988,11986,11984,11982,11980              (charges 1..5)   icon 11980
 *
 * Destination WorldPoints are teleport-arrival tiles; fine-tune in-client if a
 * pathfinder start looks off (they only need to be within pathfinding range).
 */
public class TeleportItems {

    private static final int[] GLORY   = {1706, 1708, 1710, 1712, 11976, 11978};
    private static final int[] GAMES   = {3867, 3865, 3863, 3861, 3859, 3857, 3855, 3853};
    private static final int[] DUELING = {2561, 2560, 2559, 2558, 2557, 2556, 2555, 2552};
    private static final int[] WEALTH  = {11988, 11986, 11984, 11982, 11980};

    public static final List<TeleportItem> ALL = Arrays.asList(
        // --- Amulet of glory (icon 11978) ---
        new TeleportItem("Glory: Edgeville",        "Amulet of glory", GLORY, 11978, new WorldPoint(3087, 3496, 0)),
        new TeleportItem("Glory: Karamja",          "Amulet of glory", GLORY, 11978, new WorldPoint(2918, 3176, 0)),
        new TeleportItem("Glory: Draynor Village",  "Amulet of glory", GLORY, 11978, new WorldPoint(3105, 3251, 0)),
        new TeleportItem("Glory: Al Kharid",        "Amulet of glory", GLORY, 11978, new WorldPoint(3293, 3163, 0)),

        // --- Games necklace (icon 3853) ---
        new TeleportItem("Games necklace: Burthorpe",         "Games necklace", GAMES, 3853, new WorldPoint(2898, 3553, 0)),
        new TeleportItem("Games necklace: Barbarian Outpost", "Games necklace", GAMES, 3853, new WorldPoint(2519, 3571, 0)),
        new TeleportItem("Games necklace: Corp Beast Cave",   "Games necklace", GAMES, 3853, new WorldPoint(2967, 4383, 2)),
        new TeleportItem("Games necklace: Wintertodt",        "Games necklace", GAMES, 3853, new WorldPoint(1630, 3937, 0)),

        // --- Ring of dueling (icon 2552) ---
        new TeleportItem("Ring of dueling: PvP Arena",     "Ring of dueling", DUELING, 2552, new WorldPoint(3315, 3235, 0)),
        new TeleportItem("Ring of dueling: Castle Wars",   "Ring of dueling", DUELING, 2552, new WorldPoint(2440, 3090, 0)),
        new TeleportItem("Ring of dueling: Ferox Enclave", "Ring of dueling", DUELING, 2552, new WorldPoint(3151, 3635, 0)),

        // --- Ring of wealth (icon 11980) ---
        new TeleportItem("Ring of wealth: Grand Exchange", "Ring of wealth", WEALTH, 11980, new WorldPoint(3163, 3478, 0)),
        new TeleportItem("Ring of wealth: Falador Park",   "Ring of wealth", WEALTH, 11980, new WorldPoint(2995, 3375, 0)),
        new TeleportItem("Ring of wealth: Miscellania",    "Ring of wealth", WEALTH, 11980, new WorldPoint(2531, 3861, 0)),

        // --- Chronicle (icon 13660) ---
        new TeleportItem("Chronicle: Champions Guild",     "Chronicle",      new int[]{13660}, 13660, new WorldPoint(3202, 3354, 0))
    );

    public static TeleportItem getByDisplayName(String name) {
        return ALL.stream().filter(t -> t.getDisplayName().equals(name)).findFirst().orElse(null);
    }
}
