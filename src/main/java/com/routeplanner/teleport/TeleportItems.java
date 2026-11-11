package com.routeplanner.teleport;

import net.runelite.api.coords.WorldPoint;

import com.routeplanner.util.LocationNames;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
@Slf4j
public class TeleportItems {

    private static final int[] GLORY   = {1706, 1708, 1710, 1712, 11976, 11978};
    private static final int[] GAMES   = {3867, 3865, 3863, 3861, 3859, 3857, 3855, 3853};
    private static final int[] DUELING = {2561, 2560, 2559, 2558, 2557, 2556, 2555, 2552};
    private static final int[] WEALTH  = {11988, 11986, 11984, 11982, 11980};

    /**
     * Marker destination for teleports into the player-owned house. The house is an instance,
     * so it has no real tile; matches "Teleport to House" in TeleportSpells.
     */
    public static final WorldPoint HOUSE = new WorldPoint(0, 0, 0);

    /** Where the data puts the inside of the house (the template's tile): read as {@link #HOUSE}. */
    private static final WorldPoint DATA_HOUSE = new WorldPoint(1923, 5709, 0);

    /** The hand-made entries: their names are what saved routes refer to, so they stay as they are. */
    private static final List<TeleportItem> HAND = Arrays.asList(
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
        new TeleportItem("Chronicle: Champions Guild",     "Chronicle",      new int[]{13660}, 13660, new WorldPoint(3202, 3354, 0)),

        // --- Ectophial (full 4251, empty 4252) ---
        new TeleportItem("Ectophial: Ectofuntus",           "Ectophial",       new int[]{4251,4252},                                          4251,  new WorldPoint(3659, 3523, 0)),

        // --- Skills necklace (charges 1-6: 11105,11107,11109,11111,11970,11968 + uncharged 11113) ---
        new TeleportItem("Skills necklace: Fishing Guild",  "Skills necklace", new int[]{11105,11107,11109,11111,11113,11968,11970},           11105, new WorldPoint(2611, 3391, 0)),
        new TeleportItem("Skills necklace: Mining Guild",   "Skills necklace", new int[]{11105,11107,11109,11111,11113,11968,11970},           11105, new WorldPoint(3013, 3341, 0)),
        new TeleportItem("Skills necklace: Crafting Guild", "Skills necklace", new int[]{11105,11107,11109,11111,11113,11968,11970},           11105, new WorldPoint(2933, 3295, 0)),
        new TeleportItem("Skills necklace: Cooks Guild",    "Skills necklace", new int[]{11105,11107,11109,11111,11113,11968,11970},           11105, new WorldPoint(3144, 3443, 0)),
        new TeleportItem("Skills necklace: WC Guild",       "Skills necklace", new int[]{11105,11107,11109,11111,11113,11968,11970},           11105, new WorldPoint(1659, 3505, 0)),
        new TeleportItem("Skills necklace: Farming Guild",  "Skills necklace", new int[]{11105,11107,11109,11111,11113,11968,11970},           11105, new WorldPoint(1248, 3718, 0)),

        // --- Combat bracelet (charges 1-6: 11118,11120,11122,11124,11972,11974 + uncharged 11126) ---
        new TeleportItem("Combat bracelet: Warriors Guild", "Combat bracelet", new int[]{11118,11120,11122,11124,11126,11972,11974},           11118, new WorldPoint(2882, 3546, 0)),
        new TeleportItem("Combat bracelet: Champions Guild","Combat bracelet", new int[]{11118,11120,11122,11124,11126,11972,11974},           11118, new WorldPoint(3191, 3365, 0)),
        new TeleportItem("Combat bracelet: Monastery",      "Combat bracelet", new int[]{11118,11120,11122,11124,11126,11972,11974},           11118, new WorldPoint(3052, 3485, 0)),
        new TeleportItem("Combat bracelet: Ranging Guild",  "Combat bracelet", new int[]{11118,11120,11122,11124,11126,11972,11974},           11118, new WorldPoint(2655, 3441, 0)),

        // --- Ardougne cloak 1-4 (1:13121, 2:13122, 3:13123, 4:13124) ---
        new TeleportItem("Ardy cloak: Monastery",          "Ardougne cloak",  new int[]{13121,13122,13123,13124}, 13121, new WorldPoint(2606, 3211, 0)),
        new TeleportItem("Ardy cloak 2+: Farm patch",      "Ardougne cloak",  new int[]{13122,13123,13124},       13122, new WorldPoint(2678, 3374, 0)),

        // --- Explorer's ring 2-4 (2:13126, 3:13127, 4:13128) ---
        new TeleportItem("Explorer's ring: Cabbage patch", "Explorer's ring", new int[]{13126,13127,13128},       13128, new WorldPoint(3055, 3293, 0)),

        // --- Farming cape (9810, trimmed 9811) ---
        new TeleportItem("Farming cape: Farming Guild",    "Farming cape",    new int[]{9810,9811},               9811,  new WorldPoint(1247, 3726, 0)),

        // --- Slayer ring (charges 1-8: 11866-11873, eternal: 21268) ---
        new TeleportItem("Slayer ring: Stronghold Slayer Cave", "Slayer ring", new int[]{11866,11867,11868,11869,11870,11871,11872,11873,21268}, 11866, new WorldPoint(2428, 3423, 0)),
        new TeleportItem("Slayer ring: Slayer Tower",           "Slayer ring", new int[]{11866,11867,11868,11869,11870,11871,11872,11873,21268}, 11866, new WorldPoint(3428, 3535, 0)),
        new TeleportItem("Slayer ring: Fremennik Slayer Dungeon","Slayer ring", new int[]{11866,11867,11868,11869,11870,11871,11872,11873,21268}, 11866, new WorldPoint(2799, 3615, 0)),
        new TeleportItem("Slayer ring: Tarn's Lair",           "Slayer ring", new int[]{11866,11867,11868,11869,11870,11871,11872,11873,21268}, 11866, new WorldPoint(2660, 5168, 0)),
        new TeleportItem("Slayer ring: Dark Beasts",            "Slayer ring", new int[]{11866,11867,11868,11869,11870,11871,11872,11873,21268}, 11866, new WorldPoint(2023, 4635, 0)),

        // --- Digsite pendant (charges 1-5: 11190-11194) ---
        new TeleportItem("Digsite pendant: Digsite",           "Digsite pendant", new int[]{11190,11191,11192,11193,11194}, 11194, new WorldPoint(3341, 3445, 0)),
        new TeleportItem("Digsite pendant: Fossil Island",     "Digsite pendant", new int[]{11190,11191,11192,11193,11194}, 11194, new WorldPoint(3764, 3869, 0)),
        new TeleportItem("Digsite pendant: Lithkren",          "Digsite pendant", new int[]{11190,11191,11192,11193,11194}, 11194, new WorldPoint(3550, 4000, 0)),

        // --- Necklace of passage (charges 1-5: 21146,21149,21151,21153,21155) ---
        new TeleportItem("Necklace of passage: Wizards Tower",  "Necklace of passage", new int[]{21146,21149,21151,21153,21155}, 21146, new WorldPoint(3113, 3179, 0)),
        new TeleportItem("Necklace of passage: Outpost",        "Necklace of passage", new int[]{21146,21149,21151,21153,21155}, 21146, new WorldPoint(2430, 3347, 0)),
        new TeleportItem("Necklace of passage: Eagles Eyrie",   "Necklace of passage", new int[]{21146,21149,21151,21153,21155}, 21146, new WorldPoint(2395, 3481, 0)),

        // --- Burning amulet (charges 1-5: 21166,21169,21171,21173,21175) ---
        new TeleportItem("Burning amulet: Lava Maze",          "Burning amulet", new int[]{21166,21169,21171,21173,21175}, 21166, new WorldPoint(3028, 3840, 0)),
        new TeleportItem("Burning amulet: Chaos Temple",       "Burning amulet", new int[]{21166,21169,21171,21173,21175}, 21166, new WorldPoint(2859, 3679, 0)),
        new TeleportItem("Burning amulet: Bandit Camp",        "Burning amulet", new int[]{21166,21169,21171,21173,21175}, 21166, new WorldPoint(3038, 3703, 0)),

        // --- Player-owned house (destination HOUSE: an instance, so no fixed tile) ---
        new TeleportItem("Construction cape: House", "Construction cape", new int[]{9789, 9790}, 9790, HOUSE),
        new TeleportItem("Construction cape: Hosidius", "Construction cape", new int[]{9789, 9790}, 9790, new WorldPoint(1744, 3517, 0)),
        new TeleportItem("House tab: House",         "Teleport to house", new int[]{8013}, 8013, HOUSE)
    );

    /**
     * Every item teleport: the hand-made ones, then every other one in the pathfinder's
     * teleportation_items.tsv (diary rewards, capes, tablets, scrolls...).
     */
    public static final List<TeleportItem> ALL = build();

    private static List<TeleportItem> build() {
        List<TeleportItem> all = new ArrayList<>(HAND);
        Set<String> names = new HashSet<>();
        for (TeleportItem h : HAND) names.add(h.getDisplayName());
        for (TeleportItem it : load()) {
            if (coveredByHand(it)) continue;
            // A name the hand-made list already uses, for somewhere else: add the place to it
            if (!names.add(it.getDisplayName())) {
                String placed = it.getDisplayName() + " (" + LocationNames.describe(it.getDestination()) + ")";
                if (!names.add(placed)) continue;
                it = new TeleportItem(placed, it.getItemName(), it.getChargeIds(), it.getIconId(), it.getDestination());
            }
            all.add(it);
        }
        return Collections.unmodifiableList(all);
    }

    /**
     * The data's rows, one per name. Rows sharing a name and landing spot (charge or tier
     * variants) become one entry for all their items; a name that lands in several places
     * (the house's outside portal, wherever it was built) gets the place added to it.
     */
    static List<TeleportItem> load() {
        List<Row> rows = new ArrayList<>();
        try (InputStream is = TeleportItems.class.getResourceAsStream("/com/routeplanner/transports/teleportation_items.tsv")) {
            if (is == null) {
                log.warn("Route Planner: teleportation_items.tsv missing");
                return Collections.emptyList();
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] cols = line.split("\t", -1);
                if (cols.length < 7) continue;
                WorldPoint dest = point(cols[0]);
                String name = cols[6].trim();
                int[] ids = itemIds(cols[3]);
                if (dest == null || name.isEmpty() || ids.length == 0) continue;
                rows.add(new Row(name, dest.equals(DATA_HOUSE) ? HOUSE : dest, ids));
            }
        } catch (IOException e) {
            log.warn("Route Planner: couldn't read teleportation_items.tsv", e);
            return Collections.emptyList();
        }

        // Group by name, then by landing spot within the name
        Map<String, Map<WorldPoint, Set<Integer>>> byName = new LinkedHashMap<>();
        for (Row r : rows) {
            Set<Integer> ids = byName.computeIfAbsent(r.name, k -> new LinkedHashMap<>())
                .computeIfAbsent(r.dest, k -> new LinkedHashSet<>());
            for (int id : r.ids) ids.add(id);
        }
        List<TeleportItem> out = new ArrayList<>();
        for (Map.Entry<String, Map<WorldPoint, Set<Integer>>> e : byName.entrySet()) {
            String name = e.getKey();
            boolean several = e.getValue().size() > 1;
            int colon = name.indexOf(':');
            String itemName = colon > 0 ? name.substring(0, colon).trim() : name;
            for (Map.Entry<WorldPoint, Set<Integer>> d : e.getValue().entrySet()) {
                int[] ids = d.getValue().stream().mapToInt(Integer::intValue).toArray();
                String display = several && !isHouse(d.getKey()) ? name + " (" + LocationNames.describe(d.getKey()) + ")" : name;
                out.add(new TeleportItem(display, itemName, ids, ids[0], d.getKey()));
            }
        }
        return out;
    }

    /** A data entry the hand-made list already has: one of the same items, landing in the same place. */
    private static boolean coveredByHand(TeleportItem it) {
        for (TeleportItem h : HAND) {
            boolean sameItem = false;
            for (int id : it.getChargeIds()) if (h.matchesItemId(id)) { sameItem = true; break; }
            if (!sameItem) continue;
            if (isHouse(h.getDestination()) || isHouse(it.getDestination())) {
                if (isHouse(h.getDestination()) && isHouse(it.getDestination())) return true;
                continue;
            }
            if (h.getDestination().getPlane() == it.getDestination().getPlane()
                && h.getDestination().distanceTo2D(it.getDestination()) <= 10) return true;
        }
        return false;
    }

    private static final class Row {
        final String name;
        final WorldPoint dest;
        final int[] ids;

        Row(String name, WorldPoint dest, int[] ids) {
            this.name = name;
            this.dest = dest;
            this.ids = ids;
        }
    }

    /** "13121=1||13122=1" -> {13121, 13122}. */
    static int[] itemIds(String items) {
        List<Integer> ids = new ArrayList<>();
        for (String alt : items.split("\\|\\|")) {
            String id = alt.split("=")[0].trim();
            if (id.isEmpty()) continue;
            try {
                ids.add(Integer.parseInt(id));
            } catch (NumberFormatException ignored) {
                // not an item id
            }
        }
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    private static WorldPoint point(String s) {
        String[] p = s.trim().split("\\s+");
        if (p.length != 3) return null;
        try {
            return new WorldPoint(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Whether a teleport destination is the player's house, which has no fixed world tile. */
    public static boolean isHouse(WorldPoint dest) {
        return dest != null && dest.getX() == 0 && dest.getY() == 0;
    }

    public static TeleportItem getByDisplayName(String name) {
        return ALL.stream().filter(t -> t.getDisplayName().equals(name)).findFirst().orElse(null);
    }
}
