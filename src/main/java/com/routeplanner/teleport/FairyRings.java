package com.routeplanner.teleport;

import com.routeplanner.model.RouteStep;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fairy rings: where the rings stand, where each three-letter code lands, and the interface's
 * dials. Read from the pathfinder's fairy_rings.tsv (rings are rows with an origin, codes are rows
 * with a destination and the code in the last column).
 */
@Slf4j
public final class FairyRings {

    /** The teleport method saved on a step; its code (e.g. "CKR") is kept in teleportNexus. */
    public static final String METHOD = "FAIRY_RING";
    /** Zanaris is reached by its own option on any ring, not a code. */
    public static final String ZANARIS = "ZANARIS";

    /**
     * Each dial's letters in the order its varbit counts them (0 is the first letter). Pressing
     * a dial's clockwise button moves one letter on in this order.
     */
    private static final String[] DIALS = {"ADCB", "ILKJ", "PSRQ"};
    private static final int[] DIAL_VARBITS = {
        VarbitID.FAIRYRING_1, VarbitID.FAIRYRING_2, VarbitID.FAIRYRING_3};

    private static final Pattern CODE = Pattern.compile("^[ABCD][IJKL][PQRS]$");
    private static final Pattern LAST_DESTINATION = Pattern.compile("(?i)last-destination\\s*\\(([a-z]{3})\\)");

    /** A ring the player can step into. */
    public static final class Ring {
        public final WorldPoint tile;
        public final int objectId;

        Ring(WorldPoint tile, int objectId) {
            this.tile = tile;
            this.objectId = objectId;
        }
    }

    private static final Map<String, WorldPoint> DESTINATIONS = new LinkedHashMap<>();
    private static final List<Ring> RINGS = new ArrayList<>();

    static {
        load();
    }

    private FairyRings() {}

    private static void load() {
        try (InputStream is = FairyRings.class.getResourceAsStream("/com/routeplanner/transports/fairy_rings.tsv")) {
            if (is == null) {
                log.warn("Route Planner: fairy_rings.tsv missing");
                return;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] cols = line.split("\t", -1);
                if (cols.length < 2) continue;
                WorldPoint origin = point(cols[0]);
                WorldPoint dest = point(cols[1]);
                if (origin != null) {
                    RINGS.add(new Ring(origin, objectId(cols.length > 2 ? cols[2] : "")));
                } else if (dest != null && cols.length > 1) {
                    String code = normalize(cols[cols.length - 1]);
                    if (code != null) DESTINATIONS.putIfAbsent(code, dest);
                }
            }
        } catch (IOException e) {
            log.warn("Route Planner: couldn't read fairy_rings.tsv", e);
        }
    }

    /** "C K R", "ckr" -> "CKR"; "Zanaris" -> "ZANARIS"; null if it isn't a code. */
    public static String normalize(String code) {
        if (code == null) return null;
        String c = code.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        if (c.equals(ZANARIS)) return ZANARIS;
        return CODE.matcher(c).matches() ? c : null;
    }

    /** "CKR" -> "C K R", the way the game shows it. */
    public static String spaced(String code) {
        String c = normalize(code);
        if (c == null) return code;
        return c.equals(ZANARIS) ? "Zanaris" : c.charAt(0) + " " + c.charAt(1) + " " + c.charAt(2);
    }

    /** Where a code lands, or null for a code that goes nowhere. */
    public static WorldPoint destination(String code) {
        String c = normalize(code);
        return c == null ? null : DESTINATIONS.get(c);
    }

    /** How far from a code's destination a landing still counts as that code's. */
    private static final int LANDING_SLACK = 10;

    /** The code whose destination is at (or next to) {@code landed}, or null. */
    public static String codeLandingAt(WorldPoint landed) {
        if (landed == null) return null;
        String best = null;
        int bestDist = LANDING_SLACK + 1;
        for (Map.Entry<String, WorldPoint> e : DESTINATIONS.entrySet()) {
            WorldPoint d = e.getValue();
            if (d.getPlane() != landed.getPlane()) continue;
            int dist = d.distanceTo2D(landed);
            if (dist < bestDist) {
                bestDist = dist;
                best = e.getKey();
            }
        }
        return best;
    }

    /** Every code that goes somewhere, in the data's order. */
    public static List<String> codes() {
        return Collections.unmodifiableList(new ArrayList<>(DESTINATIONS.keySet()));
    }

    public static List<Ring> rings() {
        return Collections.unmodifiableList(RINGS);
    }

    /** The ring closest to {@code from} on the same floor, or null. */
    public static Ring nearest(WorldPoint from) {
        if (from == null) return null;
        Ring best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Ring r : RINGS) {
            if (r.tile.getPlane() != from.getPlane()) continue;
            int d = r.tile.distanceTo2D(from);
            if (d < bestDist) {
                bestDist = d;
                best = r;
            }
        }
        return best;
    }

    /** Whether the step teleports by fairy ring (with a code that's known). */
    public static boolean isFairyRingStep(RouteStep step) {
        return step != null && METHOD.equals(step.getTeleportMethod()) && normalize(step.getTeleportNexus()) != null;
    }

    /** Whether an object's (lowercase) name is a fairy ring: plain rings and the spirit tree/ring combinations. */
    public static boolean isRingName(String lowerName) {
        return lowerName != null && (lowerName.contains("fairy ring") || lowerName.contains("fairy tree"));
    }

    /** The code a "Last-destination (CKR)" option dials, or null. */
    public static String lastDestinationCode(String option) {
        if (option == null) return null;
        Matcher m = LAST_DESTINATION.matcher(option);
        return m.find() ? normalize(m.group(1)) : null;
    }

    /** The code the interface's dials show now. Call on the client thread. */
    public static String dialledCode(Client client) {
        StringBuilder sb = new StringBuilder(3);
        for (int i = 0; i < 3; i++) {
            int v = client.getVarbitValue(DIAL_VARBITS[i]);
            if (v < 0 || v > 3) return null;
            sb.append(DIALS[i].charAt(v));
        }
        return sb.toString();
    }

    /**
     * Turns for dial {@code dial} (0-2) to show {@code code}'s letter: +1 or +2 for the clockwise
     * button (2 either way), -1 for anticlockwise, 0 when it's already there.
     */
    public static int turns(Client client, int dial, String code) {
        String c = normalize(code);
        if (c == null || c.equals(ZANARIS)) return 0;
        int want = DIALS[dial].indexOf(c.charAt(dial));
        int now = client.getVarbitValue(DIAL_VARBITS[dial]);
        int diff = Math.floorMod(want - now, 4);
        return diff == 3 ? -1 : diff;
    }

    /** The travel log's entry for a code: one per code, A I P first, each followed by its favourite star. */
    private static final int LOG_FIRST_ENTRY = net.runelite.api.gameval.InterfaceID.FairyringsLog.AIP;

    /**
     * The travel log widget for {@code code} (clicking it sets the dials to it), or -1 for Zanaris
     * or a code that isn't one. The log lists codes in alphabetical order of their letters.
     */
    public static int logEntry(String code) {
        String c = normalize(code);
        if (c == null || c.equals(ZANARIS)) return -1;
        int index = (c.charAt(0) - 'A') * 16 + (c.charAt(1) - 'I') * 4 + (c.charAt(2) - 'P');
        return LOG_FIRST_ENTRY + index * 2;
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

    /** The object id at the end of "Configure Fairy ring 29495", or -1. */
    private static int objectId(String menu) {
        String[] parts = menu.trim().split("\\s+");
        try {
            return parts.length == 0 ? -1 : Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
