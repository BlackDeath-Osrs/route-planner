package com.routeplanner;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Loads transport edges (origin -> destination) from the bundled Shortest Path
 * TSV datasets. Only rows with BOTH a valid origin and destination are kept,
 * which naturally includes doors, stairs, dungeon entrances, agility shortcuts,
 * boats, ships and minecarts, and skips "from anywhere" teleports / hub types.
 */
@Slf4j
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

    /** Every loaded edge with its menu text and object id, for highlighting the stairs to use. */
    private static final Map<WorldPoint, List<com.routeplanner.transport.Transport>> DETAILS = new HashMap<>();

    /** The transport going from {@code origin} to {@code destination}, or null if none is known. */
    public static com.routeplanner.transport.Transport find(WorldPoint origin, WorldPoint destination) {
        List<com.routeplanner.transport.Transport> list = DETAILS.get(origin);
        if (list == null) return null;
        for (com.routeplanner.transport.Transport t : list) if (t.destination.equals(destination)) return t;
        return null;
    }

    public static Map<WorldPoint, List<WorldPoint>> load() {
        Map<WorldPoint, List<WorldPoint>> edges = new HashMap<>();
        int total = 0;
        for (String f : FILES) {
            int count = 0;
            try (InputStream is = TransportLoader.class.getResourceAsStream(
                    "/com/routeplanner/transports/" + f)) {
                if (is == null) { log.warn("Transport file missing: {}", f); continue; }
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] cols = line.split("\t");
                    if (cols.length < 2) continue;
                    WorldPoint origin = parsePoint(cols[0]);
                    WorldPoint dest = parsePoint(cols[1]);
                    if (origin == null || dest == null) continue;
                    edges.computeIfAbsent(origin, k -> new ArrayList<>()).add(dest);
                    DETAILS.computeIfAbsent(origin, k -> new ArrayList<>()).add(detail(origin, dest, cols.length > 2 ? cols[2] : ""));
                    count++;
                }
            } catch (Exception e) {
                log.warn("Failed to load transport file {}: {}", f, e.toString());
            }
            log.info("TransportLoader: {} edges from {}", count, f);
            total += count;
        }
        log.info("TransportLoader: {} total edges across {} origins", total, edges.size());
        return edges;
    }

    /** "Climb-up Staircase 16671" -> action "Climb-up Staircase", object 16671. */
    private static com.routeplanner.transport.Transport detail(WorldPoint origin, WorldPoint dest, String col) {
        String text = col.trim();
        int id = -1;
        int sp = text.lastIndexOf(' ');
        if (sp > 0) {
            try {
                id = Integer.parseInt(text.substring(sp + 1));
                text = text.substring(0, sp);
            } catch (NumberFormatException ignored) {}
        }
        return new com.routeplanner.transport.Transport(origin, dest, text.isEmpty() ? null : text, id);
    }

    private static WorldPoint parsePoint(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        String[] parts = s.split("\\s+");
        if (parts.length < 3) return null;
        try {
            return new WorldPoint(Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
