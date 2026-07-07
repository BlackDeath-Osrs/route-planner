package com.routeplanner;

import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;

import javax.inject.Inject;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

@Slf4j
public class PathfinderOverlay extends Overlay {

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final RoutePlannerConfig config;

    private WorldCollisionMap collisionMap = null;
    private boolean loadAttempted = false;
    private java.util.Map<WorldPoint, java.util.List<WorldPoint>> transports = new java.util.HashMap<>();
    private static final int TRANSPORT_COST = 5;

    private WorldPoint lastPlayer = null;
    private WorldPoint lastTarget = null;
    private WorldPoint lastFailedTarget = null;
    private WorldPoint lastAttemptPlayer = null;
    private int failRetryCounter = 0;
    @Getter private List<WorldPoint> cachedPath = new ArrayList<>();

    private int tickCount = 0;


    public void onGameTick() {
        tickCount++;
        if (tickCount % 2 != 0) return;
        if (plugin.getActiveRoute() == null) return;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null) return;
        if (step.isLocationReached()) { cachedPath = new ArrayList<>(); return; }

        WorldPoint target = step.getWorldPoint();
        if (target == null) { cachedPath = new ArrayList<>(); return; }


        WorldPoint player = client.getLocalPlayer().getWorldLocation();

        if (player.getPlane() != target.getPlane()) { cachedPath = new ArrayList<>(); return; }
        if (player.distanceTo(target) <= 3) { cachedPath = new ArrayList<>(); return; }

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
            cachedPath = buildPath(player, target);
            lastFailedTarget = cachedPath.isEmpty() ? target : null;
        }
    }

    @Inject
    public PathfinderOverlay(Client client, RoutePlannerPlugin plugin, RoutePlannerConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
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

        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null) return null;
        if (step.isLocationReached()) return null;
        if (cachedPath.isEmpty()) return null;

        WorldPoint player = client.getLocalPlayer().getWorldLocation();
        WorldPoint target = cachedPath.get(cachedPath.size() - 1);
        if (player.getPlane() != target.getPlane()) return null;

        // Draw path segment by segment, skipping transport "jumps" (far-apart points)
        int plane = client.getTopLevelWorldView().getPlane();
        for (int i = 0; i < cachedPath.size() - 1; i++) {
            WorldPoint wa = cachedPath.get(i);
            WorldPoint wb = cachedPath.get(i + 1);

            // Transport jump: don't draw a line across the map
            if (wa.distanceTo(wb) > 5) continue;

            LocalPoint la = LocalPoint.fromWorld(client, wa);
            LocalPoint lb = LocalPoint.fromWorld(client, wb);
            if (la == null || lb == null) continue;
            net.runelite.api.Point pa = Perspective.localToCanvas(client, la, plane);
            net.runelite.api.Point pb = Perspective.localToCanvas(client, lb, plane);
            if (pa == null || pb == null) continue;

            // Outer glow
            graphics.setColor(new Color(255, 140, 0, 60));
            graphics.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawLine(pa.getX(), pa.getY(), pb.getX(), pb.getY());

            // Main line
            graphics.setColor(new Color(255, 140, 0, 220));
            graphics.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawLine(pa.getX(), pa.getY(), pb.getX(), pb.getY());

            // Arrow every 20 segments
            if (i % 20 == 10) {
                double angle = Math.atan2(pb.getY() - pa.getY(), pb.getX() - pa.getX());
                drawDirectionArrow(graphics, (pa.getX() + pb.getX()) / 2,
                    (pa.getY() + pb.getY()) / 2, angle, new Color(255, 165, 0, 220));
            }
        }

        return null;
    }

    private void drawDirectionArrow(Graphics2D graphics, int x, int y, double angle, Color color) {
        int size = 10;
        java.awt.geom.AffineTransform saved = graphics.getTransform();
        graphics.translate(x, y);
        graphics.rotate(angle);

        int[] xsOut = {size + 2, -size - 1, -size - 1};
        int[] ysOut = {0, -(size / 2) - 2, (size / 2) + 2};
        graphics.setColor(Color.BLACK);
        graphics.fillPolygon(xsOut, ysOut, 3);

        int[] xs = {size, -size, -size};
        int[] ys = {0, -size / 2, size / 2};
        graphics.setColor(color);
        graphics.fillPolygon(xs, ys, 3);

        graphics.setTransform(saved);
    }

    private boolean isOnPath(WorldPoint player) {
        if (cachedPath.isEmpty()) return false;
        for (WorldPoint wp : cachedPath) {
            if (wp.distanceTo(player) <= 3) return true;
        }
        return false;
    }

    private List<WorldPoint> buildPath(WorldPoint start, WorldPoint end) {
        if (collisionMap == null || !collisionMap.isLoaded()) {
            return Collections.emptyList();
        }

        int plane = start.getPlane();

        Map<WorldPoint, WorldPoint> cameFrom = new HashMap<>();
        Map<WorldPoint, Integer> bestG = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>();

        bestG.put(start, 0);
        open.add(new Node(start, 0, heuristic(start, end)));
        cameFrom.put(start, null);

        int maxNodes = 350000;
        int explored = 0;

        while (!open.isEmpty() && explored < maxNodes) {
            Node current = open.poll();
            WorldPoint cp = current.point;
            explored++;

            if (current.g > bestG.getOrDefault(cp, Integer.MAX_VALUE)) continue;
            if (cp.distanceTo(end) <= 1) {
                return reconstructPath(cameFrom, cp, start);
            }

            int x = cp.getX();
            int y = cp.getY();
            int cplane = cp.getPlane();

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
                int ng = current.g + 1;
                if (ng < bestG.getOrDefault(np, Integer.MAX_VALUE)) {
                    bestG.put(np, ng);
                    cameFrom.put(np, cp);
                    open.add(new Node(np, ng, heuristic(np, end)));
                }
            }

            // Transport edges from this tile (doors, stairs, dungeon entrances, shortcuts, boats)
            java.util.List<WorldPoint> dests = transports.get(cp);
            if (dests != null) {
                for (WorldPoint np : dests) {
                    int ng = current.g + TRANSPORT_COST;
                    if (ng < bestG.getOrDefault(np, Integer.MAX_VALUE)) {
                        bestG.put(np, ng);
                        cameFrom.put(np, cp);
                        open.add(new Node(np, ng, heuristic(np, end)));
                    }
                }
            }
        }

        return Collections.emptyList();
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
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }

    private List<WorldPoint> reconstructPath(Map<WorldPoint, WorldPoint> cameFrom,
                                              WorldPoint end, WorldPoint start) {
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
