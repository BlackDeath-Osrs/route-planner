package com.routeplanner.teleport;

import com.routeplanner.util.DataFiles;
import com.routeplanner.model.RouteStep;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import java.util.*;

/**
 * Varlamore's quetzal transport: Renu, waiting at each landing site, flies between them, and the
 * Hunter Guild's quetzal whistle calls a quetzal to any of them from anywhere. Both open the same
 * map of landing sites. Read from the pathfinder's quetzals.tsv (sites with an origin are where
 * Renu waits, rows with a destination name the sites).
 */
public final class Quetzals {

    /** Flying with Renu from a landing site. The step's teleportNexus holds the site's name. */
    public static final String METHOD_RENU = "QUETZAL";
    /** Calling a quetzal with the whistle. The step's teleportNexus holds the site's name. */
    public static final String METHOD_WHISTLE = "QUETZAL_WHISTLE";

    public static final String RENU = "Renu";

    /** Every whistle: basic, enhanced, perfected, and the perfected one that never runs out. */
    private static final int[] WHISTLES = {
        ItemID.HG_QUETZALWHISTLE_BASIC, ItemID.HG_QUETZALWHISTLE_ENHANCED,
        ItemID.HG_QUETZALWHISTLE_PERFECTED, ItemID.HG_QUETZALWHISTLE_PERFECTED_INFINITE};

    /** How far from a site a landing still counts as that site. */
    private static final int LANDING_SLACK = 8;

    private static final Map<String, WorldPoint> SITES = new LinkedHashMap<>();
    private static final List<WorldPoint> RENU_TILES = new ArrayList<>();

    static {
        load();
    }

    private Quetzals() {}

    private static void load() {
        for (String[] cols : DataFiles.rowsAt("/com/routeplanner/transports/quetzals.tsv")) {
            if (cols.length < 2) continue;
            WorldPoint origin = DataFiles.point(cols[0]);
            WorldPoint dest = DataFiles.point(cols[1]);
            if (origin != null) {
                if (!RENU_TILES.contains(origin)) RENU_TILES.add(origin);
            } else if (dest != null && cols.length > 5 && !cols[5].trim().isEmpty()) {
                SITES.putIfAbsent(cols[5].trim(), dest);
            }
        }
    }

    /** Every landing site's name, in the data's order. */
    public static List<String> sites() {
        return Collections.unmodifiableList(new ArrayList<>(SITES.keySet()));
    }

    /** The site's name as the data spells it, matched without regard to case; null if unknown. */
    public static String site(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (String s : SITES.keySet()) if (s.toLowerCase(Locale.ROOT).equals(n)) return s;
        return null;
    }

    /** Where a site's quetzal lands, or null. */
    public static WorldPoint destination(String site) {
        String s = site(site);
        return s == null ? null : SITES.get(s);
    }

    /** The site whose landing spot is at (or next to) {@code landed}, or null. */
    public static String siteAt(WorldPoint landed) {
        if (landed == null) return null;
        String best = null;
        int bestDist = LANDING_SLACK + 1;
        for (Map.Entry<String, WorldPoint> e : SITES.entrySet()) {
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

    /** The landing site with Renu closest to {@code from}, or null. */
    public static WorldPoint nearestRenu(WorldPoint from) {
        if (from == null) return null;
        WorldPoint best = null;
        int bestDist = Integer.MAX_VALUE;
        for (WorldPoint t : RENU_TILES) {
            if (t.getPlane() != from.getPlane()) continue;
            int d = t.distanceTo2D(from);
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }

    public static boolean isWhistle(int itemId) {
        for (int id : WHISTLES) if (id == itemId) return true;
        return false;
    }

    /** Whether the step flies by quetzal (either way) to a known site. */
    public static boolean isQuetzalStep(RouteStep step) {
        return step != null && (METHOD_RENU.equals(step.getTeleportMethod()) || METHOD_WHISTLE.equals(step.getTeleportMethod()))
            && site(step.getTeleportNexus()) != null;
    }

    public static boolean isRenuStep(RouteStep step) {
        return isQuetzalStep(step) && METHOD_RENU.equals(step.getTeleportMethod());
    }

    public static boolean isWhistleStep(RouteStep step) {
        return isQuetzalStep(step) && METHOD_WHISTLE.equals(step.getTeleportMethod());
    }

    /** Whether the player is already where the step's quetzal goes. */
    public static boolean arrived(RouteStep step, WorldPoint player) {
        WorldPoint dest = destination(step.getTeleportNexus());
        return player != null && dest != null && player.getPlane() == dest.getPlane() && player.distanceTo2D(dest) <= 25;
    }

}
