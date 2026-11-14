package com.routeplanner;

import com.routeplanner.util.DataFiles;
import com.routeplanner.transport.Transport;
import net.runelite.api.coords.WorldPoint;
import java.util.*;

/**
 * Loads transport edges (origin -> destination) from the bundled Shortest Path
 * TSV datasets. Only rows with BOTH a valid origin and destination are kept,
 * which naturally includes doors, stairs, dungeon entrances, agility shortcuts,
 * boats, ships and minecarts, and skips "from anywhere" teleports / hub types.
 */
public class TransportLoader {

    // Files that contain simple origin->destination edges
    private static final String[] FILES = {
        "transports.tsv",
        "agility_shortcuts.tsv",
        "boats.tsv",
        "ships.tsv",
        "charter_ships.tsv",
        "minecarts.tsv"
    };

    /** Files whose edges travel between places (ships, minecarts) rather than through a door or up stairs. */
    private static final Set<String> TRAVEL_FILES = new HashSet<>(Arrays.asList(
        "boats.tsv", "ships.tsv", "charter_ships.tsv", "minecarts.tsv"));
    /** Origins whose edges are all journeys from TRAVEL_FILES, mapped to those destinations. */
    private static final Map<WorldPoint, Set<WorldPoint>> TRAVEL = new HashMap<>();

    /** Whether the edge from {@code origin} to {@code destination} is a ship or minecart journey. */
    public static boolean isTravel(WorldPoint origin, WorldPoint destination) {
        Set<WorldPoint> dests = TRAVEL.get(origin);
        return dests != null && dests.contains(destination);
    }

    /** Every loaded edge with its menu text and object id, for highlighting the stairs to use. */
    private static final Map<WorldPoint, List<Transport>> DETAILS = new HashMap<>();

    /** The transport going from {@code origin} to {@code destination}, or null if none is known. */
    public static Transport find(WorldPoint origin, WorldPoint destination) {
        List<Transport> list = DETAILS.get(origin);
        if (list == null) return null;
        for (Transport t : list) if (t.destination.equals(destination)) return t;
        return null;
    }

    public static Map<WorldPoint, List<WorldPoint>> load() {
        Map<WorldPoint, List<WorldPoint>> edges = new HashMap<>();
        int total = 0;
        for (String f : FILES) {
            int count = 0;
            for (String[] cols : DataFiles.rowsAt("/com/routeplanner/transports/" + f)) {
                if (cols.length < 2) continue;
                WorldPoint origin = DataFiles.point(cols[0]);
                WorldPoint dest = DataFiles.point(cols[1]);
                if (origin == null || dest == null) continue;
                edges.computeIfAbsent(origin, k -> new ArrayList<>()).add(dest);
                if (TRAVEL_FILES.contains(f)) TRAVEL.computeIfAbsent(origin, k -> new HashSet<>()).add(dest);
                DETAILS.computeIfAbsent(origin, k -> new ArrayList<>()).add(detail(origin, dest, DataFiles.col(cols, 2)));
                count++;
            }
            total += count;
        }
        return edges;
    }

    /** "Climb-up Staircase 16671" -> action "Climb-up Staircase", object 16671. */
    private static Transport detail(WorldPoint origin, WorldPoint dest, String col) {
        String text = col.trim();
        int id = -1;
        int sp = text.lastIndexOf(' ');
        if (sp > 0) {
            try {
                id = Integer.parseInt(text.substring(sp + 1));
                text = text.substring(0, sp);
            } catch (NumberFormatException ignored) {}
        }
        return new Transport(origin, dest, text.isEmpty() ? null : text, id);
    }
}
