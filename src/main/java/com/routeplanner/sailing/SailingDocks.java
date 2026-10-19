package com.routeplanner.sailing;

import com.routeplanner.transport.Transport;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Sailing mooring points as routing edges between the land and the sea. The collision map keeps
 * the open sea walkable but cut off from the land, so without these a route can never leave the
 * shore: a dock joins its gangplank tile to the sea tile beside it.
 *
 * <p>Boarding only works where one of the player's boats is moored. The game keeps each boat's port
 * in a varbit but its values aren't documented, so they are learned: when a boat's port changes while
 * the player is at a dock, that value means that dock. Until every boat's port is known, any dock
 * the player's Sailing level allows can be boarded from.
 */
@Slf4j
@Singleton
public class SailingDocks {

    public static final String BOARD = "Board your boat";
    public static final String DISEMBARK = "Disembark";

    private static final int[] BOAT_PORT_VARBITS = {
        VarbitID.SAILING_BOAT_1_PORT, VarbitID.SAILING_BOAT_2_PORT, VarbitID.SAILING_BOAT_3_PORT,
        VarbitID.SAILING_BOAT_4_PORT, VarbitID.SAILING_BOAT_5_PORT
    };
    private static final int[] BOAT_OWNED_VARBITS = {
        VarbitID.SAILING_BOAT_1_OWNED, VarbitID.SAILING_BOAT_2_OWNED, VarbitID.SAILING_BOAT_3_OWNED,
        VarbitID.SAILING_BOAT_4_OWNED, VarbitID.SAILING_BOAT_5_OWNED
    };
    /** Port values that aren't a dock: the boat is lost at sea (253), capsized (254) or bottled (255). */
    private static final int FIRST_NOT_DOCKED = 253;
    private static final String CONFIG_GROUP = "routeplanner";
    private static final String PORTS_KEY = "sailingPorts";
    /** How close to a dock the player must be for a port change to be credited to it. */
    private static final int LEARN_RADIUS = 12;

    public static final class Dock {
        public final String name;
        public final int level;
        public final WorldPoint land;
        public final WorldPoint sea;
        /** The game's id for the dock, a boat's port varbit while moored there; -1 if not known. */
        public final int portId;

        Dock(String name, int level, WorldPoint land, WorldPoint sea, int portId) {
            this.name = name;
            this.level = level;
            this.land = land;
            this.sea = sea;
            this.portId = portId;
        }
    }

    /** A precomputed sea route between two moorings: its length and the straight legs that sail it. */
    public static final class SeaRoute {
        public final WorldPoint from;
        public final WorldPoint to;
        public final int tiles;
        /** Turning points from {@code from} to {@code to}, both included. */
        public final List<WorldPoint> waypoints;

        SeaRoute(WorldPoint from, WorldPoint to, int tiles, List<WorldPoint> waypoints) {
            this.from = from;
            this.to = to;
            this.tiles = tiles;
            this.waypoints = waypoints;
        }

        SeaRoute reversed() {
            List<WorldPoint> back = new ArrayList<>(waypoints);
            Collections.reverse(back);
            return new SeaRoute(to, from, tiles, back);
        }
    }

    private final Client client;
    private final ConfigManager configManager;

    private final List<Dock> docks = new ArrayList<>();
    /** Mooring -> the sea routes leaving it. A search over open water can't find a far dock round a
     *  coast without exploring millions of tiles, so these are worked out ahead of time. */
    private final Map<String, Map<WorldPoint, List<SeaRoute>>> seaRoutesBySize = new HashMap<>();
    /** Water tile -> water tiles a boat reaches through a cave mouth or similar the map doesn't join. */
    private final Map<WorldPoint, List<WorldPoint>> seaLinks = new HashMap<>();
    /** Port varbit value -> dock name, learned in game and kept in the config. */
    private final Map<Integer, String> learnedPorts = new HashMap<>();
    private final int[] lastPorts = new int[BOAT_PORT_VARBITS.length];
    private final boolean[] owned = new boolean[BOAT_PORT_VARBITS.length];
    /** False until the ports have been read once since logging in, so the login itself isn't a change. */
    private boolean portsRead = false;
    /** Routing edges for the current level and boats: tile -> tiles one dock crossing away. */
    private volatile Map<WorldPoint, List<WorldPoint>> edges = Collections.emptyMap();
    private int edgesLevel = -1;
    private String edgesBoats = null;

    @Inject
    public SailingDocks(Client client, ConfigManager configManager) {
        this.client = client;
        this.configManager = configManager;
        load();
    }

    public List<Dock> getDocks() { return docks; }

    private void load() {
        try (InputStream is = getClass().getResourceAsStream("/com/routeplanner/sailing/docks.tsv")) {
            if (is == null) { log.warn("Route Planner: docks.tsv not found"); return; }
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] cols = line.split("\t");
                if (cols.length < 4) continue;
                WorldPoint land = parsePoint(cols[2]), sea = parsePoint(cols[3]);
                if (land == null || sea == null) continue;
                try {
                    int portId = cols.length > 4 ? Integer.parseInt(cols[4].trim()) : -1;
                    docks.add(new Dock(cols[0], Integer.parseInt(cols[1].trim()), land, sea, portId));
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            log.warn("Route Planner: failed to load docks.tsv: {}", e.toString());
        }
        log.info("Route Planner: {} sailing docks", docks.size());
        loadSeaLinks();
    }

    private void loadSeaLinks() {
        try (InputStream is = getClass().getResourceAsStream("/com/routeplanner/sailing/sea_links.tsv")) {
            if (is == null) return;
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] cols = line.split("\t");
                if (cols.length < 2) continue;
                WorldPoint a = parsePoint(cols[0]), b = parsePoint(cols[1]);
                if (a == null || b == null) continue;
                seaLinks.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
                seaLinks.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
            }
        } catch (Exception e) {
            log.warn("Route Planner: failed to load sea_links.tsv: {}", e.toString());
        }
    }

    /** Every sea link as {from, to}, both ways round. */
    public List<WorldPoint[]> getSeaLinkPairs() {
        List<WorldPoint[]> pairs = new ArrayList<>();
        for (Map.Entry<WorldPoint, List<WorldPoint>> e : seaLinks.entrySet()) {
            for (WorldPoint to : e.getValue()) pairs.add(new WorldPoint[]{e.getKey(), to});
        }
        return pairs;
    }

    /** Water tiles a boat at {@code p} can sail straight through to (a cave mouth), or null. */
    public List<WorldPoint> seaLinksFrom(WorldPoint p) {
        return seaLinks.get(p);
    }

    /**
     * The sea route set for a boat this wide: each boat size has its own (generated with the same rules
     * as live pathing), so a sloop's routes keep out of gaps a raft can take. Loaded on first use.
     */
    private synchronized Map<WorldPoint, List<SeaRoute>> seaRoutes(int boatWidthTiles) {
        String size = boatWidthTiles >= 3 ? "sloop" : boatWidthTiles == 2 ? "skiff" : "raft";
        Map<WorldPoint, List<SeaRoute>> routes = seaRoutesBySize.get(size);
        if (routes == null) {
            routes = loadSeaRoutes("/com/routeplanner/sailing/sea_routes_" + size + ".tsv");
            seaRoutesBySize.put(size, routes);
        }
        return routes;
    }

    private Map<WorldPoint, List<SeaRoute>> loadSeaRoutes(String resource) {
        Map<WorldPoint, List<SeaRoute>> routes = new HashMap<>();
        int count = 0;
        try (InputStream is = getClass().getResourceAsStream(resource)) {
            if (is == null) { log.warn("Route Planner: {} not found", resource); return routes; }
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] cols = line.split("\t");
                if (cols.length < 4) continue;
                WorldPoint from = parsePoint(cols[0]), to = parsePoint(cols[1]);
                if (from == null || to == null) continue;
                List<WorldPoint> waypoints = new ArrayList<>();
                for (String wp : cols[3].split(";")) {
                    WorldPoint p = parsePoint(wp + " " + from.getPlane());
                    if (p != null) waypoints.add(p);
                }
                SeaRoute route;
                try {
                    route = new SeaRoute(from, to, Integer.parseInt(cols[2].trim()), waypoints);
                } catch (NumberFormatException e) {
                    continue;
                }
                routes.computeIfAbsent(from, k -> new ArrayList<>()).add(route);
                routes.computeIfAbsent(to, k -> new ArrayList<>()).add(route.reversed());
                count++;
            }
        } catch (Exception e) {
            log.warn("Route Planner: failed to load {}: {}", resource, e.toString());
        }
        log.info("Route Planner: {} sea routes from {}", count, resource);
        return routes;
    }

    /** The precomputed sea routes for a boat this wide leaving the mooring at {@code p}, or null. */
    public List<SeaRoute> seaRoutesFrom(WorldPoint p, int boatWidthTiles) {
        return seaRoutes(boatWidthTiles).get(p);
    }

    /** The precomputed sea route for a boat this wide from one mooring to another, or null. */
    public SeaRoute seaRoute(WorldPoint from, WorldPoint to, int boatWidthTiles) {
        List<SeaRoute> routes = seaRoutes(boatWidthTiles).get(from);
        if (routes == null) return null;
        for (SeaRoute r : routes) if (r.to.equals(to)) return r;
        return null;
    }

    /** Reads the learned ports back from the config; call once the config is available. */
    public void loadLearnedPorts() {
        learnedPorts.clear();
        String saved = configManager.getConfiguration(CONFIG_GROUP, PORTS_KEY);
        if (saved == null || saved.isEmpty()) return;
        for (String pair : saved.split(";")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                learnedPorts.put(Integer.parseInt(pair.substring(0, eq)), pair.substring(eq + 1));
            } catch (NumberFormatException ignored) {}
        }
        edgesBoats = null;
    }

    private void saveLearnedPorts() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> e : learnedPorts.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        configManager.setConfiguration(CONFIG_GROUP, PORTS_KEY, sb.toString());
    }

    /**
     * Once a tick: learns a boat's port when it changes next to a dock, and rebuilds the routing
     * edges when the Sailing level or the boats' ports change.
     */
    public void onGameTick() {
        WorldPoint at = Sailing.position(client);
        for (int i = 0; i < BOAT_PORT_VARBITS.length; i++) {
            int port = client.getVarbitValue(BOAT_PORT_VARBITS[i]);
            owned[i] = client.getVarbitValue(BOAT_OWNED_VARBITS[i]) != 0;
            // The bundled ids cover every known dock; only a new one is learned from where it's moored
            if (portsRead && owned[i] && port != lastPorts[i] && port < FIRST_NOT_DOCKED && dockById(port) == null && at != null) {
                Dock dock = nearest(at, LEARN_RADIUS);
                if (dock != null && !dock.name.equals(learnedPorts.get(port))) {
                    learnedPorts.put(port, dock.name);
                    saveLearnedPorts();
                    log.debug("Route Planner: sailing port {} is {}", port, dock.name);
                }
            }
            lastPorts[i] = port;
        }
        portsRead = true;

        int level = client.getRealSkillLevel(Skill.SAILING);
        String boats = Arrays.toString(lastPorts) + Arrays.toString(owned) + learnedPorts.size();
        if (level != edgesLevel || !boats.equals(edgesBoats)) {
            edgesLevel = level;
            edgesBoats = boats;
            edges = buildEdges(level);
        }
    }

    /** Forget the last-read ports, e.g. on logout, so the next login's values aren't taken as moves. */
    public void reset() {
        portsRead = false;
    }

    /** What to tell the player about boarding at {@code dock}, e.g. "Board your boat at Port Sarim". */
    public String boardingHint(WorldPoint dock) {
        Dock d = dock == null ? null : nearest(dock, 15);
        return d != null ? BOARD + " at " + d.name : BOARD;
    }

    /** The dock whose gangplank or mooring is within {@code radius} tiles of {@code p}, nearest first. */
    public Dock nearest(WorldPoint p, int radius) {
        Dock best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Dock d : docks) {
            if (d.land.getPlane() != p.getPlane()) continue;
            int dist = Math.min(d.land.distanceTo2D(p), d.sea.distanceTo2D(p));
            if (dist <= radius && dist < bestDist) { best = d; bestDist = dist; }
        }
        return best;
    }

    private Map<WorldPoint, List<WorldPoint>> buildEdges(int level) {
        Set<String> moored = mooredDocks();
        Map<WorldPoint, List<WorldPoint>> map = new HashMap<>();
        for (Dock d : docks) {
            if (level > 0 && d.level > level) continue;
            // Getting off works at any dock the level allows; getting on needs a boat there.
            map.computeIfAbsent(d.sea, k -> new ArrayList<>()).add(d.land);
            if (moored == null || moored.contains(d.name)) {
                map.computeIfAbsent(d.land, k -> new ArrayList<>()).add(d.sea);
            }
        }
        return map;
    }

    /**
     * Names of the docks the player's boats are moored at, or null when any owned boat's dock isn't known
     * (then any dock can be boarded from). Lost, capsized or bottled boats aren't at a dock.
     */
    private Set<String> mooredDocks() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < lastPorts.length; i++) {
            int port = lastPorts[i];
            if (!owned[i] || port >= FIRST_NOT_DOCKED) continue;
            Dock dock = dockById(port);
            String name = dock != null ? dock.name : learnedPorts.get(port);
            if (name == null) return null;
            names.add(name);
        }
        return names.isEmpty() ? null : names;
    }

    /** The dock with the game id {@code portId}, or null. */
    private Dock dockById(int portId) {
        for (Dock d : docks) if (d.portId == portId) return d;
        return null;
    }

    /** Tiles near a mooring, where a boat has to come alongside land: packed x << 16 | y, per radius. */
    private final Map<Integer, Set<Integer>> nearMoorings = new HashMap<>();

    /** Whether (x, y) is within {@code radius} tiles of a mooring. */
    public synchronized boolean nearMooring(int x, int y, int radius) {
        Set<Integer> tiles = nearMoorings.get(radius);
        if (tiles == null) {
            tiles = new HashSet<>();
            for (Dock d : docks) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) tiles.add(((d.sea.getX() + dx) << 16) | (d.sea.getY() + dy));
                }
            }
            // and both ends of each cave mouth
            for (WorldPoint p : seaLinks.keySet()) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) tiles.add(((p.getX() + dx) << 16) | (p.getY() + dy));
                }
            }
            nearMoorings.put(radius, tiles);
        }
        return tiles.contains((x << 16) | y);
    }

    /** Whether {@code p} is the sea side of a dock, where the boat is left. */
    public boolean isMooring(WorldPoint p) {
        for (Dock d : docks) if (d.sea.equals(p)) return true;
        return false;
    }

    /** Tiles one dock crossing away from {@code p}, or null. */
    public List<WorldPoint> edgesFrom(WorldPoint p) {
        return edges.get(p);
    }

    /** The dock crossing from {@code a} to {@code b} as a transition to highlight, or null if it isn't one. */
    public Transport crossing(WorldPoint a, WorldPoint b) {
        List<WorldPoint> dests = edges.get(a);
        if (dests == null || !dests.contains(b)) return null;
        for (Dock d : docks) {
            if (d.land.equals(a) && d.sea.equals(b)) return new Transport(a, b, BOARD + " at " + d.name, -1);
            if (d.sea.equals(a) && d.land.equals(b)) return new Transport(a, b, DISEMBARK + " at " + d.name, -1);
        }
        return null;
    }

    private static WorldPoint parsePoint(String s) {
        String[] parts = s.trim().split("\\s+");
        if (parts.length < 3) return null;
        try {
            return new WorldPoint(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
