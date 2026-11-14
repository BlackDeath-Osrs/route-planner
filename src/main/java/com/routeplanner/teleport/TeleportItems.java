package com.routeplanner.teleport;

import lombok.RequiredArgsConstructor;
import com.routeplanner.util.*;
import net.runelite.api.coords.WorldPoint;
import java.util.*;

/**
 * Item-teleport data. Each entry is one pickable destination; entries for the same
 * physical item share the same chargeIds set (all charge states) and iconId.
 * The hand-made entries are in teleport_items.tsv.
 */
public class TeleportItems {

    /**
     * Marker destination for teleports into the player-owned house. The house is an instance,
     * so it has no real tile; matches "Teleport to House" in TeleportSpells.
     */
    public static final WorldPoint HOUSE = new WorldPoint(0, 0, 0);

    /** Where the data puts the inside of the house (the template's tile): read as {@link #HOUSE}. */
    private static final WorldPoint DATA_HOUSE = new WorldPoint(1923, 5709, 0);

    /** The hand-made entries: their names are what saved routes refer to, so they stay as they are. */
    private static final List<TeleportItem> HAND = DataFiles.load("teleport_items.tsv", r -> new TeleportItem(r[0], r[1], DataFiles.ints(r[2]), DataFiles.num(r[3]),
        "house".equals(r[4]) ? HOUSE : DataFiles.point(r[4])));

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
        for (String[] cols : DataFiles.rowsAt("/com/routeplanner/transports/teleportation_items.tsv")) {
            if (cols.length < 7) continue;
            WorldPoint dest = DataFiles.point(cols[0]);
            String name = cols[6].trim();
            int[] ids = itemIds(cols[3]);
            if (dest == null || name.isEmpty() || ids.length == 0) continue;
            rows.add(new Row(name, dest.equals(DATA_HOUSE) ? HOUSE : dest, ids));
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

    @RequiredArgsConstructor
    private static final class Row {
        final String name;
        final WorldPoint dest;
        final int[] ids;

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

    /** Whether a teleport destination is the player's house, which has no fixed world tile. */
    public static boolean isHouse(WorldPoint dest) {
        return dest != null && dest.getX() == 0 && dest.getY() == 0;
    }

    public static TeleportItem getByDisplayName(String name) {
        return ALL.stream().filter(t -> t.getDisplayName().equals(name)).findFirst().orElse(null);
    }
}
