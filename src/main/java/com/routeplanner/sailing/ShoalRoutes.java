package com.routeplanner.sailing;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.io.InputStream;
import java.util.*;

/**
 * The fixed routes trawling shoals swim (Sailing): each shoal's path, in the order it swims, and the
 * points it stops at, keyed by the shoal's world view id. Data from Maewi's Deep Sea Trawling plugin,
 * BSD 2-Clause (see resources/com/routeplanner/sailing/LICENSE-shoals.txt).
 */
@Slf4j
public final class ShoalRoutes {

    public static final class Route {
        /** The shoal's world view id the route was recorded under; it says what fish it is (see stopTicks). */
        public final int id;
        public final List<WorldPoint> path;
        public final List<WorldPoint> stops;
        /** The box the path and stops fit in, to skip routes nowhere near the player cheaply. */
        public final int minX, minY, maxX, maxY;

        Route(int id, List<WorldPoint> path, List<WorldPoint> stops) {
            this.id = id;
            this.path = path;
            this.stops = stops;
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
            for (List<WorldPoint> points : java.util.Arrays.asList(path, stops)) {
                for (WorldPoint p : points) {
                    x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY());
                    x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY());
                }
            }
            minX = x0; minY = y0; maxX = x1; maxY = y1;
        }

        /** Whether any of the route could be within {@code range} tiles of {@code p}. */
        public boolean near(WorldPoint p, int range) {
            return p.getX() >= minX - range && p.getX() <= maxX + range
                && p.getY() >= minY - range && p.getY() <= maxY + range;
        }
    }

    private static Map<Integer, Route> routes;

    private ShoalRoutes() {}

    /** The route of the shoal whose world view has id {@code worldViewId}, or null if it isn't known. */
    public static synchronized Route get(int worldViewId) {
        if (routes == null) routes = load();
        return routes.get(worldViewId);
    }

    /**
     * How long the shoal on this route stays at each stop, in ticks, by its fish: marlin 50, bluefin 70,
     * halibut 80, yellowfin 100, haddock 120, giant krill 150 (route ids and timings as in Maewi's
     * Deep Sea Trawling plugin); 0 if not known.
     */
    public static int stopTicks(Route route) {
        if (route == null) return 0;
        int id = route.id;
        if (id >= 14 && id <= 15) return 50;
        if (id >= 16 && id <= 17) return 70;
        if (id >= 18 && id <= 19) return 80;
        if (id >= 20 && id <= 22) return 100;
        if (id >= 23 && id <= 25) return 120;
        if (id >= 26 && id <= 29) return 150;
        return 0;
    }

    /** Every known shoal route. */
    public static synchronized java.util.Collection<Route> all() {
        if (routes == null) routes = load();
        return routes.values();
    }

    /**
     * The route of a shoal now at {@code at}: the one stored under its world view id if that route runs
     * near it, otherwise whichever route passes closest, within {@code maxTiles}. World view ids are
     * handed out as shoals load, so they can differ from when the routes were recorded.
     */
    public static Route find(int worldViewId, WorldPoint at, int maxTiles) {
        Route byId = get(worldViewId);
        if (byId != null && distance(byId, at) <= maxTiles) return byId;
        Route best = null;
        int bestDist = maxTiles + 1;
        for (Route r : routes.values()) {
            int d = distance(r, at);
            if (d < bestDist) { bestDist = d; best = r; }
        }
        return best;
    }

    /** How close the route's path comes to {@code at}, in tiles. */
    private static int distance(Route r, WorldPoint at) {
        int best = Integer.MAX_VALUE;
        for (WorldPoint p : r.path) {
            if (p.getPlane() != at.getPlane()) continue;
            best = Math.min(best, p.distanceTo2D(at));
        }
        return best;
    }

    private static Map<Integer, Route> load() {
        Properties props = new Properties();
        try (InputStream in = ShoalRoutes.class.getResourceAsStream("/com/routeplanner/sailing/shoals.properties")) {
            if (in == null) { log.warn("Route Planner: shoals.properties not found"); return Collections.emptyMap(); }
            props.load(in);
        } catch (Exception e) {
            log.warn("Route Planner: failed to load shoals.properties: {}", e.toString());
            return Collections.emptyMap();
        }
        Map<Integer, List<WorldPoint>> paths = new HashMap<>(), stops = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            Map<Integer, List<WorldPoint>> into = key.startsWith("shoalpath.") ? paths
                : key.startsWith("shoalstops.") ? stops : null;
            if (into == null) continue;
            try {
                into.put(Integer.parseInt(key.substring(key.indexOf('.') + 1)), parsePoints(props.getProperty(key)));
            } catch (NumberFormatException ignored) {}
        }
        Map<Integer, Route> out = new HashMap<>();
        for (Integer id : paths.keySet()) {
            out.put(id, new Route(id, paths.get(id), stops.getOrDefault(id, Collections.emptyList())));
        }
        log.info("Route Planner: {} shoal routes", out.size());
        return out;
    }

    /** "x, y, plane|x, y, plane|..." */
    private static List<WorldPoint> parsePoints(String value) {
        List<WorldPoint> points = new ArrayList<>();
        for (String part : value.split("\\|")) {
            String[] xyz = part.trim().split("\\s*,\\s*");
            if (xyz.length < 3) continue;
            try {
                points.add(new WorldPoint(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
            } catch (NumberFormatException ignored) {}
        }
        return points;
    }
}
