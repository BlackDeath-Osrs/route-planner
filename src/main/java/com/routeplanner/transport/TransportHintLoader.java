package com.routeplanner.transport;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads plane-changing transitions (staircases, ladders, trapdoors) from transports.tsv, for the
 * plane-transition hint/highlight feature. Deliberately separate from TransportLoader, which reads
 * the same file (plus five others) but discards everything except a bare origin->destination edge
 * for the pathfinder's graph search -- this keeps the menuOption/menuTarget/objectId fields needed
 * to describe and highlight a transition to the player.
 *
 * <p>Only transports.tsv is read here, not the boats/ships/charter_ships/minecarts/agility_shortcuts
 * files TransportLoader also covers -- those are long-distance travel methods, not "the floor
 * above/below you", and are out of scope for this feature.
 *
 * <p>Parsing (tab-split, "#"/blank line skipping, space-split coordinate parsing) intentionally
 * mirrors TransportLoader.parsePoint() exactly, so the two loaders agree on what a valid row looks
 * like even though TransportLoader's version is private and can't be called directly from here.
 */
@Slf4j
public class TransportHintLoader {

    private static final String FILE = "transports.tsv";

    /** Loads every row in transports.tsv where the origin and destination are on different planes.
     *  Same-plane rows (doors, gates) are excluded -- they're not floor transitions. Never throws;
     *  returns an empty list if the file is missing or completely unparseable. */
    public static List<Transport> loadPlaneTransitions() {
        List<Transport> result = new ArrayList<>();
        try (InputStream is = TransportHintLoader.class.getResourceAsStream(
                "/com/routeplanner/transports/" + FILE)) {
            if (is == null) {
                log.warn("Transport file missing: {}", FILE);
                return result;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            int total = 0, kept = 0;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                total++;
                String[] cols = line.split("\t");
                if (cols.length < 2) continue;

                WorldPoint origin = parsePoint(cols[0]);
                WorldPoint dest = parsePoint(cols[1]);
                if (origin == null || dest == null) continue;
                if (origin.getPlane() == dest.getPlane()) continue; // not a floor change

                // The action/object/id all live in ONE tab-column (cols[2]), space-joined, e.g.
                // "Climb-up Staircase 16671" or "Open Door 9398". The object id is always the
                // trailing numeric token; everything before it is the action+object phrase.
                String actionText = null;
                int objectId = -1;
                if (cols.length > 2) {
                    String raw = cols[2].trim();
                    int lastSpace = raw.lastIndexOf(' ');
                    if (lastSpace > 0 && lastSpace < raw.length() - 1) {
                        String tail = raw.substring(lastSpace + 1);
                        try {
                            objectId = Integer.parseInt(tail);
                            actionText = raw.substring(0, lastSpace).trim();
                        } catch (NumberFormatException notNumeric) {
                            // trailing token wasn't an id after all -- keep the whole thing as the text
                            actionText = raw;
                        }
                    } else if (!raw.isEmpty()) {
                        actionText = raw;
                    }
                }
                if (actionText != null && actionText.isEmpty()) actionText = null;

                result.add(new Transport(origin, dest, actionText, objectId));
                kept++;
            }
            log.debug("Loaded {} plane-changing transitions out of {} rows in {}", kept, total, FILE);
        } catch (Exception e) {
            log.warn("Failed to load plane transitions from {}", FILE, e);
        }
        return result;
    }

    /** Mirrors TransportLoader.parsePoint() exactly -- same split, same bounds check, same
     *  exception handling -- so both loaders treat the same row the same way. */
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
