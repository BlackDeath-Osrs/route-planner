package com.routeplanner;

import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import com.routeplanner.model.TileArea;
import com.routeplanner.sailing.Sailing;
import com.routeplanner.sailing.SailingDocks;
import com.routeplanner.teleport.PortalNexus;
import com.routeplanner.util.ScenePath;
import com.routeplanner.util.Strokes;
import com.routeplanner.transport.Transport;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.WorldEntity;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;

@Slf4j
@Singleton   // one route for everything that shows it (the scene, the world map, the HUD)
public class PathfinderOverlay extends Overlay {

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final RoutePlannerConfig config;
    private final ShortestPathBridge shortestPath;
    private final SailingDocks sailingDocks;

    private WorldCollisionMap collisionMap = null;
    private boolean loadAttempted = false;
    private Map<WorldPoint, List<WorldPoint>> transports = new HashMap<>();
    /** Shortest Path's descriptions of the jumps on cachedPath, when its path is the one in use. */
    private volatile List<Transport> cachedJumps = Collections.emptyList();
    /** The transition currently being routed to, if the active step is on a different plane than
     *  the player. Null whenever no plane redirect is in effect. Read by the overlay/highlight code
     *  to know what (if anything) to highlight as the "climb this" object. */
    private Transport activeTransition;

    /** Returns the transition the player should climb/use next, or null if none. */
    public Transport getActiveTransition() { return activeTransition; }

    /** Returns the loaded collision map, or null if not yet loaded. */
    public WorldCollisionMap getCollisionMap() { return collisionMap; }
    /** The transition currently committed to, persisted across ticks so the lookup can apply
     *  every tick, which is what caused visible flip-flopping between near-equal candidates. */
    private static final int TRANSPORT_COST = 5 * 2; // five tiles of walking (WALK_STEP)
    /** How far a boat may swing off the line (turning, drifting) before the route is planned again. A boat
     *  can't hold a line to the tile like walking does, and every re-plan redraws the line. */
    private static final int ON_ROUTE_SEA_TILES = 6;
    /** Boarding or leaving a boat takes a moment, so a short hop by sea doesn't beat walking. */
    private static final int DOCK_COST = 30;
    /** How far ahead a sea leg is straightened in one go; keeps the line-of-sight checks cheap. */
    private static final int SEA_SMOOTH_WINDOW = 120;
    /** Turning points further apart than a straight run can be are a jump (a cave mouth), not a line. */
    private static final int MAX_STRAIGHT_RUN = 2 * SEA_SMOOTH_WINDOW;
    /**
     * Plane offsets that give the search a state on top of the tile (real planes are 0-3): on land
     * with the boat left behind, and on the boat. The boat stays where it is left, so once off it no
     * dock can be boarded again; and on the boat there are no stairs or ships to take.
     */
    private static final int BOAT_LEFT = 4;
    private static final int ON_BOAT = 8;
    /** Search costs are in half-tiles of walking: a step on foot costs 2, a boat covers ground twice as fast. */
    private static final int WALK_STEP = 2;
    private static final int SAIL_STEP = 1;

    private WorldPoint lastPlayer = null;
    private WorldPoint lastTarget = null;
    private WorldPoint lastFailedTarget = null;
    private WorldPoint lastAttemptPlayer = null;
    private int failRetryCounter = 0;
    @Getter private List<WorldPoint> cachedPath = new ArrayList<>();

    /** The built-in search runs here, off the client thread: a route over the sea can take a moment. */
    private ExecutorService searchExecutor;
    private Future<List<WorldPoint>> search;
    private WorldPoint searchTarget;

    /** Force an immediate path recalculation on the next tick. */
    public void clearPath() {
        cachedPath = new ArrayList<>();
        lastPlayer = null;
        lastTarget = null;
        cancelSearch();
        shortestPath.reset();
    }

    /** Stops the search thread; called when the plugin shuts down. */
    public void shutDown() {
        cancelSearch();
        if (searchExecutor != null) {
            searchExecutor.shutdownNow();
            searchExecutor = null;
        }
    }

    private void cancelSearch() {
        if (search != null) search.cancel(true);
        search = null;
        searchTarget = null;
    }

    /**
     * Starts a built-in search for {@code target} unless one is already running for it. The answer is
     * picked up by applySearchResult on a later update.
     */
    private void startSearch(WorldPoint player, WorldPoint target, boolean exact, boolean sailing) {
        if (search != null && target.equals(searchTarget)) return;
        cancelSearch();
        if (searchExecutor == null) {
            searchExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "route-planner-path");
                t.setDaemon(true);
                return t;
            });
        }
        searchTarget = target;
        search = searchExecutor.submit(() -> {
            List<WorldPoint> path = exact ? buildPathExact(player, target) : buildPath(player, target, sailing);
            return smoothSeaLegs(path, sailing);
        });
    }

    /** Installs a finished built-in search for this target. */
    private void applySearchResult(WorldPoint player, WorldPoint target, boolean sailing) {
        if (search == null || !search.isDone()) return;
        Future<List<WorldPoint>> done = search;
        boolean forTarget = target.equals(searchTarget);
        search = null;
        searchTarget = null;
        if (!forTarget || done.isCancelled()) return;
        try {
            cachedPath = new ArrayList<>(done.get());
        } catch (Exception e) {
            log.warn("Route Planner: path search failed", e);
            cachedPath = new ArrayList<>();
        }
        cachedJumps = Collections.emptyList();
        if (config.recordTrace()) {
            log.info("[RP-TRACE] BUILT-IN to {}: tiles={} end={} jumps={}", target, cachedPath.size(),
                cachedPath.isEmpty() ? null : cachedPath.get(cachedPath.size() - 1), describeJumps(cachedPath));
        }
        // The player kept moving while it ran; a boat can be several tiles on.
        trimCachedPathTo(indexOnPath(player, sailing ? 8 : 2));
        lastFailedTarget = cachedPath.isEmpty() ? target : null;
    }

    private int tickCount = 0;


    public void onGameTick() {
        sailingDocks.onGameTick();
        WorldEntity boat = Sailing.boat(client);
        if (boat != null && boat.getConfig() != null) {
            int width = Math.max(1, Math.round(boat.getConfig().getBoundsWidth() / (float) Perspective.LOCAL_TILE_SIZE));
            if (width != boatWidthTiles && config.recordTrace()) {
                log.info("[RP-TRACE] BOAT {} tiles wide (config {}), keeps {} tiles clear of the shore",
                    width, boat.getConfig().getId(), clearanceFor(width));
            }
            if (width != boatWidthTiles) lastPlayer = null;   // re-plan for the new boat
            boatWidthTiles = width;
        }
        updatePath();
        // Stairs set on the step (and still needed) were picked by updatePath. Otherwise use the
        // first floor change on the current route, worked out fresh each tick so it never lingers
        // after the climb or the step.
        RouteStep step = plugin.getActiveRoute() == null ? null : plugin.getActiveRoute().getActiveStep();
        WorldPoint at = Sailing.position(client);
        boolean stepStairs = step != null && step.getTransitionPoint() != null && step.getWorldPoint() != null
            && at != null && otherFloor(at, step.getWorldPoint());
        // Likewise the gangplank set on the step, until the player is on the boat.
        boolean stepBoarding = step != null && isBoardingPending(step);
        if (!stepStairs && !stepBoarding) {
            activeTransition = step == null || step.isLocationReached() ? null : firstFloorChange();
            if (config.recordTrace() && !sameTransition(lastLoggedTransition, activeTransition)) {
                lastLoggedTransition = activeTransition;
                log.info("[RP-TRACE] STAIRS {}", activeTransition == null ? "none"
                    : activeTransition.actionText + " " + activeTransition.objectId + " at " + activeTransition.origin
                        + " -> " + activeTransition.destination);
            }
        }
    }

    /**
     * Whether the step still has the player board a boat before going on: it has a boarding point,
     * Sailing is on, and the player hasn't got on a boat yet during this step.
     */
    public boolean isBoardingPending(RouteStep step) {
        return step.hasBoarding() && !step.isBoarded() && !Sailing.isSailing(client);
    }

    /**
     * Where the route leaves the player's floor (stairs, ladder...) or gets on or off a boat, or
     * null when the path stays on this floor. Lets steps on another floor guide to and highlight
     * the right stairs even when nobody set them on the step (e.g. a recording that started
     * upstairs), and points at the dock before a sea crossing.
     */
    /** The stairs last written to the bug report log (activeTransition itself is reset each update). */
    private Transport lastLoggedTransition;

    /** Shortest Path's description of the jump from a to b on the path in use, or null. */
    private Transport shortestPathJump(WorldPoint a, WorldPoint b) {
        for (Transport t : cachedJumps) {
            if (t.origin.equals(a) && t.destination.equals(b)) return t;
        }
        return null;
    }

    private static boolean sameTransition(Transport a, Transport b) {
        if (a == null || b == null) return a == b;
        return Objects.equals(a.origin, b.origin) && Objects.equals(a.destination, b.destination);
    }

    /** The places a path jumps (stairs, ladders, teleports), for the bug report log. */
    static String describeJumps(List<WorldPoint> path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < path.size(); i++) {
            WorldPoint a = path.get(i), b = path.get(i + 1);
            if (a.getPlane() != b.getPlane() || a.distanceTo2D(b) > 2) {
                sb.append(sb.length() == 0 ? "" : ", ").append(a.getX()).append(',').append(a.getY()).append(',')
                    .append(a.getPlane()).append(" -> ").append(b.getX()).append(',').append(b.getY()).append(',').append(b.getPlane());
            }
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    /** Underground areas sit on plane 0 like the surface, but this far north of it (about y + 6400). */
    private static final int UNDERGROUND_Y = 4400;

    /**
     * Whether two tiles are on different floors: another plane, or one underground and the other not
     * (the ladder into the Wizards' Tower basement stays on plane 0 but jumps from y 3162 to 9576).
     */
    public static boolean otherFloor(WorldPoint a, WorldPoint b) {
        return a.getPlane() != b.getPlane() || (a.getY() >= UNDERGROUND_Y) != (b.getY() >= UNDERGROUND_Y);
    }

    /** Further than this between two path tiles underground is a ladder or vine, not a step. */
    private static final int UNDERGROUND_JUMP = 20;

    /**
     * Whether the route changes floor between two neighbouring path tiles. Besides otherFloor, a jump
     * underground counts: dungeon levels all sit on plane 0, side by side (Stronghold floor 3 to floor 2
     * is a vine from 2122,5251 to 2042,5245).
     */
    private static boolean floorChange(WorldPoint a, WorldPoint b) {
        return otherFloor(a, b) || (a.getY() >= UNDERGROUND_Y && a.distanceTo2D(b) > UNDERGROUND_JUMP);
    }

    private Transport firstFloorChange() {
        WorldPoint at = Sailing.position(client);
        if (at == null || cachedPath.size() < 2) return null;
        for (int i = 0; i + 1 < cachedPath.size(); i++) {
            WorldPoint a = cachedPath.get(i), b = cachedPath.get(i + 1);
            if (otherFloor(a, at)) return null;
            Transport dock = sailingDocks.crossing(a, b);
            if (dock != null) return dock;
            Transport sp = shortestPathJump(a, b);
            // A Shortest Path teleport counts anywhere, surface to surface too
            if (sp != null && sp.teleport) return sp;
            if (floorChange(a, b)) {
                Transport known = TransportLoader.find(a, b);
                if (known != null) return known;
                return sp != null ? sp : new Transport(a, b, null, -1);
            }
            // Same floor: a raft, a rope swing, an agility shortcut. Doors and gates (one tile) aren't
            // worth pointing at, so only longer jumps with an object to click count.
            if (a.distanceTo2D(b) > SAME_FLOOR_JUMP) {
                Transport known = TransportLoader.find(a, b);
                if (known != null && known.objectId > 0) return known;
            }
        }
        return null;
    }

    /** Further than this between two path tiles on one floor is a transport (raft, rope...), not a door. */
    private static final int SAME_FLOOR_JUMP = 2;

    private void updatePath() {
        tickCount++;
        if (tickCount % 2 != 0) return;
        // Shortest Path stopped answering: force a rebuild so the built-in pathfinder takes over.
        if (shortestPath.tick()) lastPlayer = null;
        if (plugin.getActiveRoute() == null) return;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null) { return; }
        // If locationReached but the arrival-hold timer is still counting down,
        // keep the cached path alive so the line/tile linger for that many ticks.
        if (step.isLocationReached()) {
            if (plugin.getArrivalHoldTicksRemaining() <= 0) {
                cachedPath = new ArrayList<>();
            }
            return;
        }

        // In the house on a nexus or house portal step, the way on is that object, a short walk
        // inside the instance. Shortest Path would route around it (a construction cape teleport)
        // and the built-in map has no instances, so walk the loaded scene to it instead.
        Predicate<String> houseObject = PortalNexus.houseObjectName(step);
        if (houseObject != null && client.getTopLevelWorldView().isInstance()) {
            updateHousePath(houseObject);
            return;
        }

        WorldPoint target = step.getWorldPoint();
        // A boarding step goes to the dock first, even when getting on the boat is all it asks.
        boolean boarding = isBoardingPending(step);
        if (boarding) target = step.getBoardPoint();
        if (target == null) { cachedPath = new ArrayList<>(); return; }


        WorldPoint player = Sailing.position(client);
        if (player == null) return;
        boolean sailing = Sailing.isSailing(client);
        // A moored boat can sit over the jetty; start the search from the water beside it.
        if (sailing) player = nearestOpenTile(player, 3);

        // Two-phase transition: if this step has a transitionPoint set and the player
        // is not yet on the target's plane, path to the transitionPoint (staircase/ladder).
        // Once the player climbs it and planes match, fall through to normal pathing.
        activeTransition = null;
        boolean exactTargetMatch = false;
        if (boarding) {
            activeTransition = new Transport(step.getBoardPoint(), step.getWorldPoint(),
                sailingDocks.boardingHint(step.getBoardPoint()), step.getBoardObjectId());
        } else if (step.getTransitionPoint() != null && otherFloor(player, target)) {
            target = step.getTransitionPoint();
            exactTargetMatch = true; // path to exact tile, not "within 1" which can cut through walls
            activeTransition = new Transport(
                step.getTransitionPoint(), step.getWorldPoint(), "Climb", 0);
        }
        if (player.distanceTo(target) <= 3 || (sailing && Sailing.isWithin(client, target, 3))) {
            cachedPath = new ArrayList<>();
            return;
        }
        applyShortestPathResult(player, target, exactTargetMatch, sailing);
        applySearchResult(player, target, sailing);

        // Only rebuild if player moved to a new tile or target changed
        boolean targetChanged = !target.equals(lastTarget);
        if (targetChanged || !player.equals(lastPlayer)) {
            // Throttle retries on a known-unreachable target, but retry promptly
            // when the situation likely changed (plane change or a big move).
            if (!targetChanged && target.equals(lastFailedTarget)) {
                boolean planeChanged = lastAttemptPlayer == null
                    || lastAttemptPlayer.getPlane() != player.getPlane();
                boolean movedFar = lastAttemptPlayer != null
                    && lastAttemptPlayer.getPlane() == player.getPlane()
                    && lastAttemptPlayer.distanceTo(player) > 6;
                if (!planeChanged && !movedFar) {
                    if (++failRetryCounter < 8) return;   // ~10s when stationary
                    failRetryCounter = 0;
                } else {
                    failRetryCounter = 0;   // situation changed, retry now
                }
            }
            lastAttemptPlayer = player;
            lastPlayer = player;
            lastTarget = target;
            // Stability: if the target is unchanged and we're still standing on the existing
            // path, just trim the tiles we've walked past instead of recomputing. A full A*
            // rebuild every step causes the line to visibly reshuffle (equal-cost tie-breaks),
            // so we only rebuild when the target changes or we've stepped OFF the cached route.
            //
            // Special case for plane transitions: once we've built a path to the staircase/ladder,
            // don't rebuild it just because the player stepped off the theoretical optimal line.
            int onIdx = !targetChanged ? indexOnPath(player, sailing ? ON_ROUTE_SEA_TILES : 1) : -1;
                if (onIdx > 0) {
                    trimCachedPathTo(onIdx);
                    lastFailedTarget = null;
                } else if (onIdx == 0) {
                    lastFailedTarget = null;
                } else if (shortestPath.isActive() && !sailing) {
                    // Shortest Path can't route a boat yet, so sailing always uses the built-in search.
                    // Asynchronous: the answer is picked up by applyShortestPathResult on a later update.
                    // A long route can keep it busy for seconds, so a new target also gets the built-in
                    // search (well under a second), drawn until Shortest Path's answer replaces it.
                    if (targetChanged) {
                        cachedPath = new ArrayList<>();
                        startSearch(player, target, exactTargetMatch, sailing);
                    }
                    shortestPath.request(player, target);
                } else {
                    if (targetChanged) cachedPath = new ArrayList<>();
                    startSearch(player, target, exactTargetMatch, sailing);
                }
        }
    }

    private void updateHousePath(Predicate<String> objectName) {
        cancelSearch();
        lastTarget = null;   // stepping out of the house plans the rest from scratch
        activeTransition = null;
        cachedJumps = Collections.emptyList();
        Player me = client.getLocalPlayer();
        if (me == null) return;
        LocalPoint at = me.getLocalLocation();
        List<GameObject> objects = PortalNexus.findObjects(client, objectName);
        cachedPath = objects.isEmpty() ? new ArrayList<>()
            : new ArrayList<>(ScenePath.toObjects(client.getTopLevelWorldView(), at.getSceneX(), at.getSceneY(), objects));
    }

    /**
     * Installs a Shortest Path answer for this target once it has arrived. Falls back to the
     * built-in search when Shortest Path couldn't get there (e.g. its own settings exclude a
     * transport the route needs), so switching pathfinders never loses a path we could draw.
     */
    private void applyShortestPathResult(WorldPoint player, WorldPoint target, boolean exact, boolean sailing) {
        ShortestPathBridge.Result result = shortestPath.takeResult(target);
        if (result == null) return;
        if (result.cancelled) { lastPlayer = null; return; }   // re-ask on the next update

        List<WorldPoint> path = result.path;
        // Like buildPath, a path ending next to the target is good enough unless the step needs
        // the exact tile: the target is often an NPC or object tile that can't be stood on.
        boolean usable = result.reached || (!exact && !path.isEmpty()
            && path.get(path.size() - 1).distanceTo(target) <= 1);
        if (config.recordTrace()) log.info("[RP-TRACE] SP {} for {}", usable ? "USED" : "NOT USED, built-in instead", target);
        if (!usable) {
            startSearch(player, target, exact, sailing);
            return;
        }
        cancelSearch();   // a stand-in built-in search still running mustn't overwrite this
        cachedPath = new ArrayList<>(path);
        cachedJumps = result.jumps;
        trimCachedPathTo(indexOnPath(player, 1));
        lastFailedTarget = cachedPath.isEmpty() ? target : null;
    }

    @Inject
    public PathfinderOverlay(Client client, RoutePlannerPlugin plugin, RoutePlannerConfig config,
                             ShortestPathBridge shortestPath, SailingDocks sailingDocks) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.shortestPath = shortestPath;
        this.sailingDocks = sailingDocks;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    private void loadCollisionMap() {
        if (loadAttempted) return;
        loadAttempted = true;
        try (InputStream is = getClass().getResourceAsStream("/com/routeplanner/collision-map.zip")) {
            if (is == null) {
                log.warn("Route Planner: collision-map.zip not found");
                return;
            }
            collisionMap = new WorldCollisionMap(is);
            log.info("Route Planner: collision map loaded ok={}", collisionMap.isLoaded());
            transports = TransportLoader.load();

        } catch (IOException e) {
            log.error("Route Planner: failed to load collision map", e);
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        loadCollisionMap();

        WorldPoint player = Sailing.position(client);
        if (player == null) return null;

        // Preview mode draws a short demo path from the player so the waypoint settings can be
        // seen without an active route. It deliberately overrides the real path while enabled.
        List<WorldPoint> path;
        if (config.waypointPreview()) {
            path = buildDemoPath(player);
        } else {
            if (plugin.getActiveRoute() == null) return null;
            RouteStep step = plugin.getActiveRoute().getActiveStep();
            if (step == null) return null;
            if (step.isLocationReached() && plugin.getArrivalHoldTicksRemaining() <= 0) return null;
            if (cachedPath.isEmpty()) return null;
            // Only the part of the route on this floor, and on land or at sea as the player is now:
            // it ends at the stairs to the next floor, or at the dock where the boat is boarded or left.
            path = new ArrayList<>();
            // A step with an area is reached at its edge: the line stops where it goes in.
            TileArea area = isBoardingPending(step) ? null : plugin.areaOf(step);
            for (WorldPoint wp : cachedPath) {
                if (otherFloor(wp, player)) break;
                if (!path.isEmpty() && floorChange(path.get(path.size() - 1), wp)) break;
                if (!path.isEmpty()) {
                    Transport sp = shortestPathJump(path.get(path.size() - 1), wp);
                    if (sp != null && sp.teleport) break;
                }
                if (!path.isEmpty() && sailingDocks.crossing(path.get(path.size() - 1), wp) != null) break;
                path.add(wp);
                if (area != null && area.contains(wp)) break;
            }
        }
        if (path.size() < 2) return null;

        int plane = client.getTopLevelWorldView().getPlane();

        int maxDist = pathDrawDistance();

        // Tiles are drawn first so that in BOTH mode the line sits on top of them.
        PathDisplayMode mode = config.pathDisplayMode();
        if (mode != PathDisplayMode.LINE) {
            drawPathTiles(graphics, plane, player, maxDist, path);
        }
        boolean drawLine = mode != PathDisplayMode.TILES;

        boolean tracer = config.lineStyle() == LineStyle.TRACER;
        double tracerShift = tracerShift(config);
        double along = 0;  // tiles along the path to the start of this segment, for the tracer pulse

        // Draw path segment by segment, skipping transport "jumps" (far-apart points)
        for (int i = 0; i < path.size() - 1; i++) {
            if (!drawLine) break;
            WorldPoint wa = path.get(i);
            WorldPoint wb = path.get(i + 1);
            int len = wa.distanceTo(wb);
            double segStart = along;
            along += len;

            // Transport jump: don't draw a line across the map
            if (len > 5) continue;

            // Beyond the draw limit: skip only when BOTH ends are out of range
            if (wa.distanceTo(player) > maxDist && wb.distanceTo(player) > maxDist) continue;

            LocalPoint la = LocalPoint.fromWorld(client, wa);
            LocalPoint lb = LocalPoint.fromWorld(client, wb);
            if (la == null || lb == null) continue;
            Point pa = Perspective.localToCanvas(client, la, plane);
            Point pb = Perspective.localToCanvas(client, lb, plane);
            if (pa == null || pb == null) continue;

            int thick = config.lineThickness();
            Color lineC = config.lineColor();

            // Optional glow underlay
            if (config.lineGlow()) {
                graphics.setColor(ColorUtil.colorWithAlpha(lineC, Math.min(120, lineC.getAlpha()) / 2));
                graphics.setStroke(Strokes.round(thick + 3));
                graphics.drawLine(pa.getX(), pa.getY(), pb.getX(), pb.getY());
            }

            if (tracer) {
                // A dim line with a bright pulse on the segments a pulse is passing over
                boolean lit = tracerLit(segStart, segStart + Math.max(1, len), tracerShift);
                graphics.setColor(lit ? tracerPulseColor(config) : tracerBaseColor(config));
                graphics.setStroke(Strokes.round(thick));
                graphics.drawLine(pa.getX(), pa.getY(), pb.getX(), pb.getY());
                continue;
            }

            boolean dashed = config.lineStyle() != LineStyle.SOLID;
            boolean useDashColor = dashed && config.lineUseDashColor();

            // Faint base line under the dashes when a separate dash color is used
            if (useDashColor) {
                graphics.setColor(ColorUtil.colorWithAlpha(lineC, (int) (lineC.getAlpha() * 0.45)));
                graphics.setStroke(Strokes.round(thick));
                graphics.drawLine(pa.getX(), pa.getY(), pb.getX(), pb.getY());
            }

            // Main stroke (dashes/dots layered on top, or the solid line)
            graphics.setColor(useDashColor ? config.lineDashColor() : lineC);
            graphics.setStroke(lineStroke(config, thick));
            graphics.drawLine(pa.getX(), pa.getY(), pb.getX(), pb.getY());
        }

        return null;
    }

    /**
     * Draw each node of the cached path as a highlighted game tile.
     * Alpha optionally fades with distance along the path and/or pulses over time.
     */
    private void drawPathTiles(Graphics2D graphics, int plane, WorldPoint player, int maxDist,
                               List<WorldPoint> path) {
        int total = path.size();
        if (total == 0) return;

        Color fill = config.tileFillColor();
        Color border = config.tileBorderColor();
        int borderW = config.tileBorderThickness();
        boolean rounded = config.tileRounded();

        // Hoisted out of the loop: the stroke is identical for every tile.
        BasicStroke borderStroke = borderW > 0
            ? Strokes.round(borderW)
            : null;

        float pulse = 1f;
        if (config.tilePulse()) {
            double t = (System.currentTimeMillis() % 1600L) / 1600.0;
            pulse = (float) (0.65 + 0.35 * Math.sin(t * 2 * Math.PI));
        }

        for (int i = 0; i < total; i++) {
            WorldPoint wp = path.get(i);
            if (wp.getPlane() != plane) continue;
            if (wp.distanceTo(player) > maxDist) continue;

            LocalPoint lp = LocalPoint.fromWorld(client, wp);
            if (lp == null) continue;
            Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly == null) continue;

            float scale = pulse;
            if (config.tileFade() && total > 1) {
                scale *= (float) (1.0 - 0.6 * ((double) i / (total - 1)));
            }

            // Built at most once per tile, then reused for both the fill and the border.
            Shape shape = rounded ? roundedTile(poly) : null;

            graphics.setColor(scaleAlpha(fill, scale));
            if (shape != null) {
                graphics.fill(shape);
            } else {
                graphics.fillPolygon(poly);
            }

            if (borderStroke != null) {
                graphics.setColor(scaleAlpha(border, Math.min(1f, scale * 1.6f)));
                graphics.setStroke(borderStroke);
                if (shape != null) {
                    graphics.draw(shape);
                } else {
                    graphics.drawPolygon(poly);
                }
            }
        }
    }

    /**
     * A short zig-zag of tiles leading away from the player, used only by the preview toggle.
     * No pathfinding or collision is involved; it exists purely to show off the current
     * line/tile settings when there is no active route to draw.
     */
    private List<WorldPoint> buildDemoPath(WorldPoint player) {
        List<WorldPoint> demo = new ArrayList<>();
        int x = player.getX();
        int y = player.getY();
        int plane = player.getPlane();
        int[] wiggle = { 0, 1, 1, 0, 0, -1, -1, 0, 0, 1, 1, 0 };
        for (int i = 0; i < 22; i++) {
            demo.add(new WorldPoint(x, y, plane));
            x += wiggle[i % wiggle.length];
            y += 1;
        }
        return demo;
    }

    /**
     * How far ahead to draw the path, in tiles. Follows the client's draw distance by default so the
     * path never extends past the terrain you can actually see; otherwise uses the configured limit.
     */
    private int pathDrawDistance() {
        if (config.pathFollowDrawDistance()) {
            Scene scene = client.getScene();
            if (scene != null) {
                int dd = scene.getDrawDistance();
                if (dd > 0) return dd;
            }
        }
        int manual = config.pathMaxDistance();
        return manual > 0 ? manual : 40;
    }

    /** Multiply a color's alpha by a 0..1 factor, clamped. */
    private Color scaleAlpha(Color c, float factor) {
        int a = Math.max(0, Math.min(255, Math.round(c.getAlpha() * Math.max(0f, Math.min(1f, factor)))));
        return ColorUtil.colorWithAlpha(c, a);
    }

    /**
     * Soften the corners of a projected tile polygon. Because tiles are drawn in perspective the
     * result is a rounded diamond rather than a rounded square, but it takes the hard edge off.
     */
    private Path2D roundedTile(Polygon poly) {
        int n = poly.npoints;
        Path2D.Float p = new Path2D.Float();
        if (n < 3) { p.append(poly, false); return p; }
        for (int i = 0; i < n; i++) {
            int prev = (i - 1 + n) % n;
            int next = (i + 1) % n;
            float cx = poly.xpoints[i], cy = poly.ypoints[i];
            float px = poly.xpoints[prev], py = poly.ypoints[prev];
            float nx = poly.xpoints[next], ny = poly.ypoints[next];
            // pull the corner points 25% toward each neighbour, curve through the corner
            float ax = cx + (px - cx) * 0.25f, ay = cy + (py - cy) * 0.25f;
            float bx = cx + (nx - cx) * 0.25f, by = cy + (ny - cy) * 0.25f;
            if (i == 0) p.moveTo(ax, ay); else p.lineTo(ax, ay);
            p.quadTo(cx, cy, bx, by);
        }
        p.closePath();
        return p;
    }

    /** Tiles between one tracer pulse and the next along the line. */
    public static final double TRACER_SPACING = 50;

    /** How far the tracer pulses have moved along the line by now, in tiles (0..spacing). */
    public static double tracerShift(RoutePlannerConfig config) {
        if (!config.lineAnimate() || config.tracerSpeed() <= 0) return 0;
        long cycleMs = Math.round(TRACER_SPACING * 1000 / config.tracerSpeed());
        return (System.currentTimeMillis() % cycleMs) / (double) cycleMs * TRACER_SPACING;
    }

    /** Whether a pulse sits in the stretch [from, to) of the line, in tiles along it. */
    public static boolean tracerLit(double from, double to, double shift) {
        double first = Math.ceil((from - shift) / TRACER_SPACING) * TRACER_SPACING + shift;
        return first < to;
    }

    /** The pulse: the line (or dash) color at full strength. */
    public static Color tracerPulseColor(RoutePlannerConfig config) {
        return config.lineUseDashColor() ? config.lineDashColor() : config.lineColor();
    }

    /** The line between pulses: the line color darkened and faded by the tracer intensity. */
    public static Color tracerBaseColor(RoutePlannerConfig config) {
        Color c = config.lineColor();
        float f = 1f - Math.max(0, Math.min(100, config.tracerIntensity())) / 100f;
        return new Color(Math.round(c.getRed() * f), Math.round(c.getGreen() * f),
            Math.round(c.getBlue() * f), Math.round(c.getAlpha() * f));
    }

    /** Build the main line stroke: dash pattern from the style, animated offset when enabled.
     *  Shared with the shoal routes so they look like the waypoint line. */
    public static BasicStroke lineStroke(RoutePlannerConfig config, int thick) {
        float t = Math.max(1, thick);
        LineStyle style = config.lineStyle();
        float[] dash;
        if (style == LineStyle.DASHED)        dash = new float[]{ t * 4f, t * 3f };
        else if (style == LineStyle.DOTTED)   dash = new float[]{ 0.1f, t * 2.2f };
        else if (style == LineStyle.DASH_DOT) dash = new float[]{ t * 4f, t * 2f, 0.1f, t * 2f };
        else return Strokes.round((int) t);
        float offset = 0f;
        if (config.lineAnimate()) {
            // Continuous flow: advance the dash phase over time (render runs per-frame).
            // BasicStroke requires dash_phase >= 0: a negative phase throws and the line
            // silently stops drawing, so keep this positive and cycle it over the pattern
            // period for a seamless loop. An increasing phase makes the dashes appear to
            // travel forward along the line toward the destination.
            float period = 0f;
            for (float d : dash) period += d;
            if (period <= 0f) period = 1f;
            long cycleMs = 1200L;                       // one full pattern shift per 1.2s
            float phase = (System.currentTimeMillis() % cycleMs) / (float) cycleMs;
            // Count the phase DOWN through the pattern period. An increasing phase shifts the
            // dashes backward along the path; decreasing it makes them travel forward toward
            // the destination. BasicStroke forbids a negative dash_phase, so we stay in (0, period].
            offset = period - (phase * period);         // period .. 0+, always >= 0
            if (offset < 0f) offset = 0f;               // belt & braces
        }
        int cap = (style == LineStyle.DOTTED)
            ? BasicStroke.CAP_ROUND : BasicStroke.CAP_BUTT;
        return new BasicStroke(t, cap, BasicStroke.JOIN_ROUND, 10f, dash, offset);
    }

    private boolean isOnPath(WorldPoint player) {
        if (cachedPath.isEmpty()) return false;
        for (WorldPoint wp : cachedPath) {
            if (wp.distanceTo(player) <= 3) return true;
        }
        return false;
    }

    /**
     * If the player is standing within {@code tolerance} tiles of a tile in the current cachedPath,
     * return that tile's index; otherwise -1 (meaning we've left the route and need a fresh path).
     * A boat drifts wide of the line as it turns, so sailing allows more slack than walking.
     */
    private int indexOnPath(WorldPoint player, int tolerance) {
        if (cachedPath == null || cachedPath.isEmpty()) return -1;
        int bestIdx = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < cachedPath.size(); i++) {
            WorldPoint wp = cachedPath.get(i);
            if (wp.getPlane() != player.getPlane()) continue;
            int d = wp.distanceTo(player);
            if (d < bestDist) { bestDist = d; bestIdx = i; }
        }
        return bestDist <= tolerance ? bestIdx : -1;
    }

    /** Drop path nodes before idx so the line starts at the player's current position. */
    private void trimCachedPathTo(int idx) {
        if (idx <= 0 || cachedPath == null || idx >= cachedPath.size()) return;
        cachedPath = new ArrayList<>(cachedPath.subList(idx, cachedPath.size()));
    }

    /** The route line from start to end: a search that keeps the boat clear of the shore at sea,
     *  then one without that margin if the first finds no way. */
    private List<WorldPoint> buildPath(WorldPoint start, WorldPoint end, boolean onBoat) {
        int cap = SAILING_MAX_NODES;
        WorldPoint from = onBoat ? start.dz(ON_BOAT) : start;
        // A boat is up to three tiles wide: at sea, keep a tile of open water either side of it. If that
        // can't get there (a narrow channel), sail it as before rather than show no line at all.
        marginStart = start;
        marginEnd = end;
        hullMargin = true;
        hullClearance = clearanceFor(boatWidthTiles);
        List<WorldPoint> path = search(from, end, cap);
        if (path.isEmpty() && hullMargin) {
            hullMargin = false;
            path = search(from, end, cap);
        }
        return path;
    }

    /** For the current search (it runs on one thread): whether sailing keeps clear of the shore, the
     *  ends it's relaxed near (where the boat has to come alongside), and how much room the hull needs. */
    private boolean hullMargin;
    private WorldPoint marginStart, marginEnd;
    private int hullClearance = 1;
    /**
     * Clearance only depends on the map, so it's kept between searches: per 64x64 region and plane, a byte
     * per tile holding clearance + 1 (0 = not worked out yet). Re-planning at sea then costs a fraction.
     * Only the search thread (and tests) use it.
     */
    private final Map<Long, byte[]> clearanceRegions = new HashMap<>();
    /** Within this many tiles of the start, the end or a mooring, the boat may come right up to land. */
    private static final int MARGIN_EXEMPT_TILES = 6;
    /** How far out from the shore clearance is measured; open water this far out costs nothing extra. */
    private static final int CLEARANCE_MAX = 7;
    /** Extra cost per tile, for each tile of clearance short of what the boat needs (a narrow gap). */
    private static final int NARROW_COST = 8;
    /** Whether searches may jump between moorings on the precomputed sea routes (off while generating them). */
    boolean useSeaRoutes = true;
    /** The player's boat's width in tiles (raft 1, skiff 2, sloop 3), kept up on the client thread. */
    private volatile int boatWidthTiles = 1;

    /**
     * How much clearance a boat this wide wants, so it only threads a gap it fits with room to steer: a raft
     * (1x3) any gap 3 tiles wide, a skiff (2x5) 7, a sloop (3x10) 11. Narrower gaps aren't forbidden (a
     * harbour can have a tight mouth), just costly enough that the route goes round them when it can.
     */
    static int clearanceFor(int boatWidthTiles) {
        return boatWidthTiles >= 3 ? 5 : boatWidthTiles == 2 ? 3 : 1;
    }

    /** Whether the boat can be on (x, y): not on water touching land or rocks, unless near an end or a dock. */
    private boolean roomForHull(int x, int y, int plane) {
        return !hullMargin || clearance(x, y, plane) >= 1 || nearEndOrDock(x, y);
    }

    /** The clearance a straight run from or to p may drop to: p's own, but not below what the boat wants
     *  just because p is at a dock or an end, where it's allowed in close. */
    private int endClearance(WorldPoint p) {
        if (nearEndOrDock(p.getX(), p.getY())) return hullClearance;
        return Math.min(hullClearance, clearance(p.getX(), p.getY(), p.getPlane()));
    }

    private boolean nearEndOrDock(int x, int y) {
        return near(marginStart, x, y) || near(marginEnd, x, y) || sailingDocks.nearMooring(x, y, MARGIN_EXEMPT_TILES);
    }

    /**
     * How far (x, y) is from the shore, in tiles, up to CLEARANCE_MAX: 0 for water touching land or rocks
     * (not open on every side), 1 when the nearest such tile is next to it, and so on.
     */
    private int clearance(int x, int y, int plane) {
        long region = ((long) plane << 32) | ((long) ((x >> 6) & 0xFFFF) << 16) | ((y >> 6) & 0xFFFF);
        byte[] tiles = clearanceRegions.computeIfAbsent(region, k -> new byte[64 * 64]);
        int index = ((y & 63) << 6) | (x & 63);
        if (tiles[index] != 0) return tiles[index] - 1;
        int c = CLEARANCE_MAX;
        if (!openWater(x, y, plane)) {
            c = 0;
        } else {
            ring:
            for (int r = 1; r < CLEARANCE_MAX; r++) {
                for (int d = -r; d <= r; d++) {
                    if (!openWater(x + d, y + r, plane) || !openWater(x + d, y - r, plane)
                        || !openWater(x + r, y + d, plane) || !openWater(x - r, y + d, plane)) {
                        c = r;
                        break ring;
                    }
                }
            }
        }
        tiles[index] = (byte) (c + 1);
        return c;
    }

    private boolean openWater(int x, int y, int plane) {
        return collisionMap.canMoveNorth(x, y, plane) && collisionMap.canMoveSouth(x, y, plane)
            && collisionMap.canMoveEast(x, y, plane) && collisionMap.canMoveWest(x, y, plane);
    }

    /**
     * Extra cost of sailing onto (x, y): a little more the closer it is to the shore, so the line keeps to
     * open water, and a lot more in a gap too narrow for the boat (not near the ends or a dock, where it
     * has to come in close).
     */
    private int shorePenalty(int x, int y, int plane) {
        if (!hullMargin) return 0;
        int c = clearance(x, y, plane);
        int penalty = CLEARANCE_MAX - c;
        if (c < hullClearance && !nearEndOrDock(x, y)) penalty += NARROW_COST * (hullClearance - c);
        return penalty;
    }

    private static boolean near(WorldPoint p, int x, int y) {
        return p != null && Math.max(Math.abs(p.getX() - x), Math.abs(p.getY() - y)) <= MARGIN_EXEMPT_TILES;
    }

    /** Like buildPath but requires stepping ON the exact end tile (no <= 1 early termination).
     *  Used for transition targets so A* doesn't stop at a wall-adjacent tile. */
    private List<WorldPoint> buildPathExact(WorldPoint start, WorldPoint end) {
        if (collisionMap == null || !collisionMap.isLoaded()) return Collections.emptyList();
        Map<WorldPoint, WorldPoint> cameFrom = new HashMap<>();
        Map<WorldPoint, Integer> bestG = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>();
        bestG.put(start, 0);
        open.add(new Node(start, 0, heuristic(start, end)));
        cameFrom.put(start, null);
        int explored = 0;
        while (!open.isEmpty() && explored < DEFAULT_MAX_NODES) {
            Node current = open.poll();
            WorldPoint cp = current.point;
            explored++;
            if (current.g > bestG.getOrDefault(cp, Integer.MAX_VALUE)) continue;
            if (cp.equals(end)) return walkBack(cameFrom, cp, start);
            int x = cp.getX(), y = cp.getY(), cplane = cp.getPlane();
            int[][] moves = {{x, y+1}, {x, y-1}, {x+1, y}, {x-1, y}};
            boolean[] allowed = {
                collisionMap.canMoveNorth(x, y, cplane),
                collisionMap.canMoveSouth(x, y, cplane),
                collisionMap.canMoveEast(x, y, cplane),
                collisionMap.canMoveWest(x, y, cplane)
            };
            for (int i = 0; i < 4; i++) {
                if (!allowed[i]) continue;
                WorldPoint np = new WorldPoint(moves[i][0], moves[i][1], cplane);
                int ng = current.g + WALK_STEP;
                if (ng < bestG.getOrDefault(np, Integer.MAX_VALUE)) {
                    bestG.put(np, ng);
                    cameFrom.put(np, cp);
                    open.add(new Node(np, ng, heuristic(np, end)));
                }
            }
        }
        return Collections.emptyList();
    }

    private static final int DEFAULT_MAX_NODES = 350000;
    /** With sailing on, a search can spread over the sea as well as the land. Long crossings jump
     *  between moorings on precomputed sea routes, so this only has to cover the legs either side. */
    private static final int SAILING_MAX_NODES = 1000000;

    /** The A* search over walking, transports, boats and sea routes, exploring at most
     *  {@code maxNodes} nodes. Ends on the target or, failing that, next to it (NEAR_ENOUGH). */
    private List<WorldPoint> search(WorldPoint start, WorldPoint end, int maxNodes) {
        if (collisionMap == null || !collisionMap.isLoaded()) {
            return Collections.emptyList();
        }

        SearchGraph graph = new SearchGraph(1 << 16);
        int ex = end.getX(), ey = end.getY(), eplane = end.getPlane();
        // A target inside a sea cave sits in dungeon coordinates thousands of tiles away; the
        // estimate has to know the way in, or the search heads off across the wrong ocean.
        estimateLinks = sailingDocks.getSeaLinkPairs();
        // Same for a dungeon: its tiles are far from the entrance, so estimate through the ways down
        if (ey >= UNDERGROUND_Y && (start.getY() < UNDERGROUND_Y || otherDungeon(start.getX(), start.getY(), ex, ey))) {
            List<WorldPoint[]> links = new ArrayList<>(estimateLinks);
            links.addAll(entrancesNear(end));
            estimateLinks = links;
        }
        graph.offer(SearchGraph.key(start.getX(), start.getY(), start.getPlane()), 0,
            heuristic(start.getX(), start.getY(), ex, ey), SearchGraph.NONE);

        int explored = 0;
        int node;
        // The nearest the search got on foot, for a target nobody can stand on or next to
        int closest = SearchGraph.NONE, closestDist = Integer.MAX_VALUE, closestAt = 0;
        while (explored < maxNodes && (node = graph.poll()) != SearchGraph.NONE) {
            // Close by and no nearer for a good while: the target itself can't be reached
            if (closestDist <= NEAR_ENOUGH && explored - closestAt > GIVE_UP_AFTER) break;
            if ((++explored & 0xFFF) == 0 && Thread.currentThread().isInterrupted()) return Collections.emptyList();
            long key = graph.key(node);
            int x = SearchGraph.x(key);
            int y = SearchGraph.y(key);
            // Search nodes carry the boat state in their plane (see ON_BOAT); rplane is the real one.
            int cplane = SearchGraph.plane(key);
            int rplane = cplane % BOAT_LEFT;
            int dist = rplane == eplane ? Math.max(Math.abs(x - ex), Math.abs(y - ey)) : Integer.MAX_VALUE;
            if (dist <= 1) {
                return reconstructPath(graph, node);
            }
            boolean onBoat = cplane >= ON_BOAT;
            if (!onBoat && dist < closestDist) { closest = node; closestDist = dist; closestAt = explored; }
            boolean boatLeft = !onBoat && cplane >= BOAT_LEFT;
            int g = graph.g(node);
            int step = onBoat ? SAIL_STEP : WALK_STEP;

            if (collisionMap.canMoveNorth(x, y, rplane)) offerMove(graph, node, x, y + 1, cplane, rplane, g + step, onBoat, ex, ey);
            if (collisionMap.canMoveSouth(x, y, rplane)) offerMove(graph, node, x, y - 1, cplane, rplane, g + step, onBoat, ex, ey);
            if (collisionMap.canMoveEast(x, y, rplane)) offerMove(graph, node, x + 1, y, cplane, rplane, g + step, onBoat, ex, ey);
            if (collisionMap.canMoveWest(x, y, rplane)) offerMove(graph, node, x - 1, y, cplane, rplane, g + step, onBoat, ex, ey);

            WorldPoint real = null;
            // Transport edges from this tile (doors, stairs, dungeon entrances, shortcuts, boats).
            // A boat has no stairs or ships to take.
            if (!onBoat) {
                real = new WorldPoint(x, y, rplane);
                List<WorldPoint> dests = transports.get(real);
                if (dests != null) {
                    int layer = boatLeft ? BOAT_LEFT : 0;
                    for (WorldPoint d : dests) {
                        // Off the boat near where it's going: no hopping on a ship or minecart instead.
                        if (boatLeft && TransportLoader.isTravel(real, d)) continue;
                        graph.offer(SearchGraph.key(d.getX(), d.getY(), d.getPlane() + layer), g + TRANSPORT_COST,
                            heuristic(d.getX(), d.getY(), ex, ey), node);
                    }
                }
            }

            // From one mooring straight to the others, along sea routes worked out ahead of time
            if (onBoat) {
                if (real == null) real = new WorldPoint(x, y, rplane);
                List<SailingDocks.SeaRoute> routes = useSeaRoutes ? sailingDocks.seaRoutesFrom(real, boatWidthTiles) : null;
                if (routes != null) {
                    for (SailingDocks.SeaRoute r : routes) {
                        graph.offer(SearchGraph.key(r.to.getX(), r.to.getY(), r.to.getPlane() + ON_BOAT),
                            g + r.tiles * SAIL_STEP, heuristic(r.to.getX(), r.to.getY(), ex, ey), node);
                    }
                }
                // Through a cave mouth into water the map keeps apart (Wyrmscraig Cavern)
                List<WorldPoint> links = sailingDocks.seaLinksFrom(real);
                if (links != null) {
                    for (WorldPoint d : links) {
                        graph.offer(SearchGraph.key(d.getX(), d.getY(), d.getPlane() + ON_BOAT),
                            g + TRANSPORT_COST, heuristic(d.getX(), d.getY(), ex, ey), node);
                    }
                }
            }

            // Docks: board the boat to the sea beside the jetty, or leave it onto the land. The boat
            // stays where it was left, so once off it the search carries on BOAT_LEFT planes up,
            // where no dock can be boarded again.
            if (!boatLeft) {
                if (real == null) real = new WorldPoint(x, y, rplane);
                List<WorldPoint> crossings = sailingDocks.edgesFrom(real);
                if (crossings != null && onBoat == sailingDocks.isMooring(real)) {
                    for (WorldPoint d : crossings) {
                        graph.offer(SearchGraph.key(d.getX(), d.getY(), d.getPlane() + (onBoat ? BOAT_LEFT : ON_BOAT)),
                            g + DOCK_COST, heuristic(d.getX(), d.getY(), ex, ey), node);
                    }
                }
            }
        }

        // Couldn't get there (a tile in the water, inside a wall...): lead as close as it can, the way
        // Shortest Path does, rather than drawing nothing
        if (closestDist <= NEAR_ENOUGH) return reconstructPath(graph, closest);
        return Collections.emptyList();
    }

    /** How far off an unreachable target the nearest reachable tile may be and still be led to. */
    private static final int NEAR_ENOUGH = 10;
    /** Tiles searched without getting any nearer before settling for the nearest one. */
    private static final int GIVE_UP_AFTER = 40000;


    /** A step to (x, y); at sea only where the hull fits, and dearer the closer it is to the shore. */
    private void offerMove(SearchGraph graph, int node, int x, int y, int cplane, int rplane, int cost,
                           boolean onBoat, int ex, int ey) {
        if (onBoat) {
            if (!roomForHull(x, y, rplane)) return;
            cost += shorePenalty(x, y, rplane);
        }
        graph.offer(SearchGraph.key(x, y, cplane), cost, heuristic(x, y, ex, ey), node);
    }

    /**
     * Sea paths from {@code start} to each of {@code targets} for a boat this wide, sailing only (no docks,
     * stairs or ships, nor the precomputed routes), by the same rules and costs as a live search. One sweep
     * serves them all; it's how the precomputed routes are made (see SeaRouteGenerator in the tests).
     */
    Map<WorldPoint, List<WorldPoint>> seaPathsFrom(WorldPoint start, Set<WorldPoint> targets, int boatWidth) {
        Map<WorldPoint, List<WorldPoint>> found = new HashMap<>();
        if (collisionMap == null || !collisionMap.isLoaded()) return found;
        Set<WorldPoint> left = new HashSet<>(targets);
        hullMargin = true;
        hullClearance = clearanceFor(boatWidth);
        marginStart = start;
        marginEnd = null;
        int plane = start.getPlane(), layer = plane + ON_BOAT;
        SearchGraph graph = new SearchGraph(1 << 18);
        graph.offer(SearchGraph.key(start.getX(), start.getY(), layer), 0, 0, SearchGraph.NONE);
        int node;
        while (!left.isEmpty() && (node = graph.poll()) != SearchGraph.NONE) {
            long key = graph.key(node);
            int x = SearchGraph.x(key), y = SearchGraph.y(key), g = graph.g(node);
            WorldPoint real = new WorldPoint(x, y, plane);
            if (left.remove(real)) found.put(real, reconstructPath(graph, node));
            int[][] moves = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
            for (int[] m : moves) {
                if (!canStep(x, y, plane, m[0], m[1])) continue;
                int nx = x + m[0], ny = y + m[1];
                if (!roomForHull(nx, ny, plane)) continue;
                graph.offer(SearchGraph.key(nx, ny, layer), g + SAIL_STEP + shorePenalty(nx, ny, plane), 0, node);
            }
            List<WorldPoint> links = sailingDocks.seaLinksFrom(real);
            if (links != null) {
                for (WorldPoint d : links) graph.offer(SearchGraph.key(d.getX(), d.getY(), layer), g + TRANSPORT_COST, 0, node);
            }
        }
        return found;
    }

    /** The path to {@code node} as real tiles, start first. */
    private static List<WorldPoint> reconstructPath(SearchGraph graph, int node) {
        List<WorldPoint> path = new ArrayList<>();
        for (int n = node; n != SearchGraph.NONE; n = graph.parent(n)) {
            long key = graph.key(n);
            path.add(new WorldPoint(SearchGraph.x(key), SearchGraph.y(key), SearchGraph.plane(key) % BOAT_LEFT));
        }
        Collections.reverse(path);
        return path;
    }

    /** The nearest tile to {@code p}, within {@code radius}, that can be moved off; {@code p} if none. */
    private WorldPoint nearestOpenTile(WorldPoint p, int radius) {
        if (collisionMap == null || !collisionMap.isLoaded() || canLeave(p.getX(), p.getY(), p.getPlane())) return p;
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (canLeave(p.getX() + dx, p.getY() + dy, p.getPlane())) {
                        return new WorldPoint(p.getX() + dx, p.getY() + dy, p.getPlane());
                    }
                }
            }
        }
        return p;
    }

    private boolean canLeave(int x, int y, int plane) {
        return collisionMap.canMoveNorth(x, y, plane) || collisionMap.canMoveSouth(x, y, plane)
            || collisionMap.canMoveEast(x, y, plane) || collisionMap.canMoveWest(x, y, plane);
    }

    /**
     * Straightens the legs of a path that are sailed. The search moves in four directions, which over
     * open water draws a staircase, while a boat sails straight at its destination. Each sea leg is
     * redrawn as straight runs between the farthest points still in clear water, then filled back in
     * tile by tile so the rest of the overlay (trimming, tiles mode) works on it unchanged.
     *
     * @param startsAtSea whether the path begins on a boat; otherwise legs start after boarding
     */
    private List<WorldPoint> smoothSeaLegs(List<WorldPoint> path, boolean startsAtSea) {
        if (path.size() < 3 || collisionMap == null) return path;
        List<WorldPoint> out = new ArrayList<>(path.size());
        boolean atSea = startsAtSea;
        int legStart = 0;
        for (int i = 1; i <= path.size(); i++) {
            Transport crossing = i < path.size()
                ? sailingDocks.crossing(path.get(i - 1), path.get(i)) : null;
            if (i < path.size() && crossing == null) continue;
            List<WorldPoint> leg = path.subList(legStart, i);
            out.addAll(atSea ? straighten(expandSeaRoutes(leg)) : leg);
            if (crossing != null) atSea = crossing.actionText.startsWith(SailingDocks.BOARD);
            legStart = i;
        }
        return out;
    }

    /** Fills in the tiles of each precomputed mooring-to-mooring jump the search took. */
    private List<WorldPoint> expandSeaRoutes(List<WorldPoint> leg) {
        List<WorldPoint> out = null;
        for (int i = 0; i + 1 < leg.size(); i++) {
            WorldPoint a = leg.get(i), b = leg.get(i + 1);
            SailingDocks.SeaRoute route = a.distanceTo2D(b) > 1 ? sailingDocks.seaRoute(a, b, boatWidthTiles) : null;
            if (route == null) {
                if (out != null) out.add(b);
                continue;
            }
            if (out == null) out = new ArrayList<>(leg.subList(0, i + 1));
            List<WorldPoint> tiles = throughTurningPoints(route.waypoints);
            out.addAll(tiles.subList(1, tiles.size()));
        }
        return out == null ? leg : out;
    }

    private List<WorldPoint> straighten(List<WorldPoint> leg) {
        if (leg.size() < 3) return leg;
        return throughTurningPoints(turningPoints(leg));
    }

    /** Where a sailed leg turns once straightened: its start, then each point it sails straight to. */
    List<WorldPoint> turningPoints(List<WorldPoint> leg) {
        List<WorldPoint> out = new ArrayList<>();
        if (leg.isEmpty()) return out;
        out.add(leg.get(0));
        int anchor = 0;
        while (anchor < leg.size() - 1) {
            int reach = anchor + 1;
            int limit = Math.min(leg.size() - 1, anchor + SEA_SMOOTH_WINDOW);
            for (int j = anchor + 2; j <= limit; j++) {
                // A jump (through a cave mouth) can't be straightened across
                WorldPoint prev = leg.get(j - 1), cur = leg.get(j);
                if (Math.max(Math.abs(cur.getX() - prev.getX()), Math.abs(cur.getY() - prev.getY())) > 1) break;
                if (cur.getPlane() != leg.get(anchor).getPlane() || !clearLine(leg.get(anchor), cur)) break;
                reach = j;
            }
            out.add(leg.get(reach));
            anchor = reach;
        }
        return out;
    }

    /** The tiles of straight runs between turning points; a cave mouth's jump is kept as a jump. */
    private static List<WorldPoint> throughTurningPoints(List<WorldPoint> turns) {
        List<WorldPoint> out = new ArrayList<>();
        if (turns.isEmpty()) return out;
        out.add(turns.get(0));
        for (int i = 0; i + 1 < turns.size(); i++) {
            WorldPoint a = turns.get(i), b = turns.get(i + 1);
            if (Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())) > MAX_STRAIGHT_RUN) out.add(b);
            else appendLine(out, a, b);
        }
        return out;
    }

    /** Whether every step of the straight line from a to b is open water (diagonals need both sides open),
     *  with room for the hull along it: no narrower anywhere than the boat wants, or than at its own ends
     *  (so a line doesn't cut back through a gap the search went round). */
    private boolean clearLine(WorldPoint a, WorldPoint b) {
        int floor = hullMargin ? Math.min(endClearance(a), endClearance(b)) : 0;
        List<WorldPoint> line = new ArrayList<>();
        line.add(a);
        appendLine(line, a, b);
        for (int i = 0; i + 1 < line.size(); i++) {
            WorldPoint p = line.get(i), q = line.get(i + 1);
            int x = p.getX(), y = p.getY(), z = p.getPlane();
            int dx = q.getX() - x, dy = q.getY() - y;
            if (dx != 0 && dy != 0) {
                if (!canStep(x, y, z, dx, 0) || !canStep(x + dx, y, z, 0, dy)
                    || !canStep(x, y, z, 0, dy) || !canStep(x, y + dy, z, dx, 0)) return false;
            } else if (!canStep(x, y, z, dx, dy)) {
                return false;
            }
            if (!roomForHull(q.getX(), q.getY(), z)) return false;
            if (hullMargin && clearance(q.getX(), q.getY(), z) < floor && !nearEndOrDock(q.getX(), q.getY())) return false;
        }
        return true;
    }

    private boolean canStep(int x, int y, int z, int dx, int dy) {
        if (dy > 0) return collisionMap.canMoveNorth(x, y, z);
        if (dy < 0) return collisionMap.canMoveSouth(x, y, z);
        if (dx > 0) return collisionMap.canMoveEast(x, y, z);
        return collisionMap.canMoveWest(x, y, z);
    }

    /** Appends the tiles of the straight line from a (exclusive) to b (inclusive), one step at a time. */
    private static void appendLine(List<WorldPoint> out, WorldPoint a, WorldPoint b) {
        int x0 = a.getX(), y0 = a.getY();
        int dx = b.getX() - x0, dy = b.getY() - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        for (int s = 1; s <= steps; s++) {
            int x = x0 + Math.round((float) dx * s / steps);
            int y = y0 + Math.round((float) dy * s / steps);
            out.add(new WorldPoint(x, y, a.getPlane()));
        }
    }

    private static class Node implements Comparable<Node> {
        final WorldPoint point;
        final int g, f;
        Node(WorldPoint point, int g, int h) {
            this.point = point;
            this.g = g;
            this.f = g + h;
        }
        @Override public int compareTo(Node o) { return Integer.compare(f, o.f); }
    }

    private int heuristic(WorldPoint a, WorldPoint b) {
        return heuristic(a.getX(), a.getY(), b.getX(), b.getY());
    }

    /** Underground tiles further apart than this are in different dungeons (Varrock sewers and the
     *  museum basement are 1,500 tiles apart): the way between is up to the surface and down again. */
    private static final int OTHER_DUNGEON = 500;

    private static boolean otherDungeon(int x, int y, int ex, int ey) {
        return Math.max(Math.abs(x - ex), Math.abs(y - ey)) > OTHER_DUNGEON;
    }

    /** How far from an underground goal a way down may land and still count as its way in. */
    private static final int ENTRANCE_REACH = 300;
    private static final int MAX_ENTRANCES = 16;

    /** The ways down from the surface (origin, landing) that land nearest an underground goal. */
    private List<WorldPoint[]> entrancesNear(WorldPoint goal) {
        List<WorldPoint[]> found = new ArrayList<>();
        for (Map.Entry<WorldPoint, List<WorldPoint>> e : transports.entrySet()) {
            if (e.getKey().getY() >= UNDERGROUND_Y) continue;
            for (WorldPoint d : e.getValue()) {
                if (d.getY() >= UNDERGROUND_Y && d.distanceTo2D(goal) <= ENTRANCE_REACH) {
                    found.add(new WorldPoint[]{e.getKey(), d});
                }
            }
        }
        found.sort(Comparator.comparingInt(l -> l[1].distanceTo2D(goal)));
        return found.size() > MAX_ENTRANCES ? new ArrayList<>(found.subList(0, MAX_ENTRANCES)) : found;
    }

    /** Sea links (cave mouths) and dungeon entrances the current search's estimate may go through;
     *  set per search. */
    private List<WorldPoint[]> estimateLinks = Collections.emptyList();

    /** In walking steps: exact on land, and over-eager at sea, which only costs some optimality on a
     *  leg the sea smoothing straightens anyway. Goes through a sea link when that's shorter. */
    private int heuristic(int x, int y, int ex, int ey) {
        // Climbing out, the straight line to a surface goal says nothing about which way up is nearest
        // (floor 4 of the Stronghold "looks" nearer Barbarian Village than floor 2's ladders), so no
        // estimate there: the underground part is small and gets searched in full. Going down keeps the
        // estimate, or a long walk to a dungeon would search half the map.
        if (y >= UNDERGROUND_Y && (ey < UNDERGROUND_Y || otherDungeon(x, y, ex, ey))) return 0;
        int direct = Math.abs(x - ex) + Math.abs(y - ey);
        for (WorldPoint[] link : estimateLinks) {
            int via = Math.abs(x - link[0].getX()) + Math.abs(y - link[0].getY())
                + Math.abs(link[1].getX() - ex) + Math.abs(link[1].getY() - ey);
            if (via < direct) direct = via;
        }
        return WALK_STEP * direct;
    }

    /** buildPathExact's path: from end back to start through cameFrom, then reversed. */
    private List<WorldPoint> walkBack(Map<WorldPoint, WorldPoint> cameFrom, WorldPoint end, WorldPoint start) {
        List<WorldPoint> path = new ArrayList<>();
        WorldPoint cur = end;
        while (cur != null) {
            path.add(cur);
            if (cur.equals(start)) break;
            cur = cameFrom.get(cur);
        }
        Collections.reverse(path);
        return path;
    }
}
