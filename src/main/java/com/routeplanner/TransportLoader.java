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
