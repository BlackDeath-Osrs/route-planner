package com.routeplanner.teleport;

import com.routeplanner.model.RouteStep;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

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

/**
 * Spirit trees: where they stand, and where each of them can send you. Read from the pathfinder's
 * spirit_trees.tsv: rows with an origin are the tiles around a tree (with its object id), rows
 * with a destination name a place in the trees' menu ("4: Grand Exchange").
 */
@Slf4j
public final class SpiritTrees {

    /** The teleport method saved on a step; the destination's name is kept in teleportNexus. */
    public static final String METHOD = "SPIRIT_TREE";
    /** How the path's transition names a spirit tree trip: "Spirit tree to Grand Exchange". */
    public static final String TRANSITION_PREFIX = "Spirit tree to ";

    /** The spirit tree farming patch: a tree there only works for whoever planted it. */
    private static final int PLANTED_PATCH = 8355;
    /** The menu's own name for the tree in the player's house. */
    private static final String HOUSE = "Your house";

    /** A tree, by the tiles around it that its Travel option is used from. */
    public static final class Tree {
        public final int objectId;
        public final boolean planted;
        final List<WorldPoint> tiles = new ArrayList<>();

        Tree(int objectId) {
            this.objectId = objectId;
            this.planted = objectId == PLANTED_PATCH;
        }

        /** The tile beside the tree that's closest to {@code from}. */
        public WorldPoint tileNearest(WorldPoint from) {
            WorldPoint best = tiles.get(0);
            for (WorldPoint t : tiles) if (t.distanceTo2D(from) < best.distanceTo2D(from)) best = t;
            return best;
        }

        /** The tiles around the tree. */
        public List<WorldPoint> tiles() {
            return Collections.unmodifiableList(tiles);
        }

        /** Names the tree for saving: its first tile, "x,y,plane". */
        public String key() {
            WorldPoint t = tiles.get(0);
            return t.getX() + "," + t.getY() + "," + t.getPlane();
        }

        int distanceTo(WorldPoint from) {
            return tileNearest(from).distanceTo2D(from);
        }
    }

    private static final Map<String, WorldPoint> DESTINATIONS = new LinkedHashMap<>();
    private static final List<Tree> TREES = new ArrayList<>();

    static {
        load();
    }

    private SpiritTrees() {}

    private static void load() {
        try (InputStream is = SpiritTrees.class.getResourceAsStream("/com/routeplanner/transports/spirit_trees.tsv")) {
            if (is == null) {
                log.warn("Route Planner: spirit_trees.tsv missing");
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
                if (origin != null && cols.length > 2) {
                    addTreeTile(origin, objectId(cols[2]));
                } else if (dest != null && cols.length > 6) {
                    String name = menuName(cols[6]);
                    // The house is an instance: its tree's row has no tile worth walking to
                    if (name != null) DESTINATIONS.putIfAbsent(name, HOUSE.equalsIgnoreCase(name) ? TeleportItems.HOUSE : dest);
                }
            }
        } catch (IOException e) {
            log.warn("Route Planner: couldn't read spirit_trees.tsv", e);
        }
    }

    /** A tile around a tree joins the tree with the same object id it's next to, or starts one. */
    private static void addTreeTile(WorldPoint tile, int objectId) {
        for (Tree t : TREES) {
            if (t.objectId == objectId && t.tiles.get(0).getPlane() == tile.getPlane() && t.distanceTo(tile) <= 6) {
                t.tiles.add(tile);
                return;
            }
        }
        Tree t = new Tree(objectId);
        t.tiles.add(tile);
        TREES.add(t);
    }

    /** "4: Grand Exchange" -> "Grand Exchange". */
    static String menuName(String display) {
        if (display == null) return null;
        String n = display.trim().replaceFirst("^\\w\\s*:\\s*", "").trim();
        return n.isEmpty() ? null : n;
    }

    /** Every destination's name, in the menu's order. */
    public static List<String> destinations() {
        return Collections.unmodifiableList(new ArrayList<>(DESTINATIONS.keySet()));
    }

    /** The destination's name as the menu spells it, matched without regard to case; null if unknown. */
    public static String destinationName(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (String d : DESTINATIONS.keySet()) if (d.toLowerCase(Locale.ROOT).equals(n)) return d;
        return null;
    }

    /** Where a destination lands (the house marker for "Your house"), or null. */
    public static WorldPoint destination(String name) {
        String d = destinationName(name);
        return d == null ? null : DESTINATIONS.get(d);
    }

    /** The destination landing at (or next to) {@code landed}, or null. */
    public static String destinationAt(WorldPoint landed) {
        if (landed == null) return null;
        String best = null;
        int bestDist = 9;
        for (Map.Entry<String, WorldPoint> e : DESTINATIONS.entrySet()) {
            WorldPoint d = e.getValue();
            if (d.getPlane() != landed.getPlane() || TeleportItems.isHouse(d)) continue;
            int dist = d.distanceTo2D(landed);
            if (dist < bestDist) {
                bestDist = dist;
                best = e.getKey();
            }
        }
        return best;
    }

    /**
     * The tree closest to {@code from} on the same floor, or null. Trees grown in farming patches
     * are left out: they only work for whoever planted them, and the route can't tell whose they are.
     */
    public static Tree nearest(WorldPoint from) {
        return nearest(from, Collections.emptySet());
    }

    /** Like {@link #nearest(WorldPoint)}, but planted trees in {@code grown} (by key) count too. */
    public static Tree nearest(WorldPoint from, java.util.Set<String> grown) {
        if (from == null) return null;
        Tree best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Tree t : TREES) {
            if ((t.planted && !grown.contains(t.key())) || t.tiles.get(0).getPlane() != from.getPlane()) continue;
            int d = t.distanceTo(from);
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }

    /** The trees grown in farming patches. */
    public static List<Tree> plantedTrees() {
        List<Tree> out = new ArrayList<>();
        for (Tree t : TREES) if (t.planted) out.add(t);
        return out;
    }

    public static boolean isSpiritTreeStep(RouteStep step) {
        return step != null && METHOD.equals(step.getTeleportMethod()) && destination(step.getTeleportNexus()) != null;
    }

    /** Whether an object's (lowercase) name is a spirit tree: a plain one, or the house's tree that's also a fairy ring. */
    public static boolean isTreeName(String lowerName) {
        return lowerName != null && (lowerName.contains("spirit tree") || lowerName.contains("spiritual fairy tree"));
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

    /** The object id at the end of "Travel Spirit tree 26261", or -1. */
    private static int objectId(String menu) {
        String[] parts = menu.trim().split("\\s+");
        try {
            return parts.length == 0 ? -1 : Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
