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
    private java.util.List<com.routeplanner.transport.Transport> planeTransitions = new java.util.ArrayList<>();
    private static final int PLANE_TRANSITION_SEARCH_RADIUS = 30;
    /** The transition currently being routed to, if the active step is on a different plane than
     *  the player. Null whenever no plane redirect is in effect. Read by the overlay/highlight code
     *  to know what (if anything) to highlight as the "climb this" object. */
    private com.routeplanner.transport.Transport activeTransition;
    /** The transition currently committed to, persisted across ticks so the lookup can apply
     *  hysteresis (see TransportHintLookup.SWITCH_MARGIN) instead of re-deciding from scratch
     *  every tick, which is what caused visible flip-flopping between near-equal candidates. */
    private com.routeplanner.transport.Transport committedTransition;
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
        if (step == null) { committedTransition = null; return; }
        if (step.isLocationReached()) { committedTransition = null; cachedPath = new ArrayList<>(); return; }

        WorldPoint target = step.getWorldPoint();
        if (target == null) { cachedPath = new ArrayList<>(); return; }


        WorldPoint player = client.getLocalPlayer().getWorldLocation();

        // If the step's destination is on a different plane, redirect the path target to the
        // nearest known transition (staircase/ladder/etc.) instead of giving up. Everything below
        // this point -- trim/rebuild, distance checks, render() -- operates on whatever "target"
        // is, so substituting the transition's origin tile here is enough to path to it using the
        // exact same machinery as any other destination. The moment the player actually climbs it,
        // player.getPlane() will equal step.getWorldPoint().getPlane() again on the very next tick,
        // this branch stops firing, and normal pathing to the real destination resumes with no
        // special "arrived, now continue" logic needed.
        activeTransition = null;
        if (player.getPlane() != target.getPlane()) {
            activeTransition = com.routeplanner.transport.TransportHintLookup.findNearest(
                planeTransitions, player, target, PLANE_TRANSITION_SEARCH_RADIUS, committedTransition);
            if (activeTransition == null) {
                committedTransition = null;
                cachedPath = new ArrayList<>();
                return; // Level 1 fallback: no known route up/down from here
            }
            committedTransition = activeTransition;
            // Path to a verified-walkable tile adjacent to the transition, not the transition's own
            // tile -- staircases/ladders can sit in collision-map spots that don't resolve reliably
            // as a destination from a distance. The existing 3-tile "arrived" check below then
            // triggers highlighting the transition object once the player is genuinely close.
            target = com.routeplanner.transport.TransitionAnchor.pickAnchor(activeTransition.origin, player, collisionMap);
        }
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
            // Stability: if the target is unchanged and we're still standing on the existing
            // path, just trim the tiles we've walked past instead of recomputing. A full A*
            // rebuild every step causes the line to visibly reshuffle (equal-cost tie-breaks),
            // so we only rebuild when the target changes or we've stepped OFF the cached route.
            int onIdx = !targetChanged ? indexOnPath(player) : -1;
            if (onIdx > 0) {
                trimCachedPathTo(onIdx);
                lastFailedTarget = null;
            } else if (onIdx == 0) {
                // already at the head of the path; nothing to trim, keep it as-is
                lastFailedTarget = null;
            } else {
                cachedPath = buildPath(player, target);
                lastFailedTarget = cachedPath.isEmpty() ? target : null;
            }
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
            planeTransitions = com.routeplanner.transport.TransportHintLoader.loadPlaneTransitions();
            log.info("Route Planner: loaded {} plane-changing transitions", planeTransitions.size());
        } catch (IOException e) {
            log.error("Route Planner: failed to load collision map", e);
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        loadCollisionMap();

        if (client.getLocalPlayer() == null) return null;
        WorldPoint player = client.getLocalPlayer().getWorldLocation();
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
            if (step.isLocationReached()) return null;
            if (cachedPath.isEmpty()) return null;
            path = cachedPath;
            WorldPoint target = path.get(path.size() - 1);
            if (player.getPlane() != target.getPlane()) return null;
        }
        if (path.size() < 2) return null;

        int plane = client.getTopLevelWorldView().getPlane();

        int maxDist = pathDrawDistance();

        // Tiles are drawn first so that in BOTH mode the line sits on top of them.
        com.routeplanner.PathDisplayMode mode = config.pathDisplayMode();
        if (mode != com.routeplanner.PathDisplayMode.LINE) {
            drawPathTiles(graphics, plane, player, maxDist, path);
        }
        boolean drawLine = mode != com.routeplanner.PathDisplayMode.TILES;

        // Draw path segment by segment, skipping transport "jumps" (far-apart points)
        for (int i = 0; i < path.size() - 1; i++) {
            if (!drawLine) break;
            WorldPoint wa = path.get(i);
            WorldPoint wb = path.get(i + 1);

            // Transport jump: don't draw a line across the map
            if (wa.distanceTo(wb) > 5) continue;

            // Beyond the draw limit: skip only when BOTH ends are out of range
            if (wa.distanceTo(player) > maxDist && wb.distanceTo(player) > maxDist) continue;

            LocalPoint la = LocalPoint.fromWorld(client, wa);
            LocalPoint lb = LocalPoint.fromWorld(client, wb);
            if (la == null || lb == null) continue;
            net.runelite.api.Point pa = Perspective.localToCanvas(client, la, plane);
            net.runelite.api.Point pb = Perspective.localToCanvas(client, lb, plane);
            if (pa == null || pb == null) continue;

            int thick = config.lineThickness();
            Color lineC = config.lineColor();

            // Optional glow underlay
            if (config.lineGlow()) {
                graphics.setColor(new Color(lineC.getRed(), lineC.getGreen(), lineC.getBlue(),
                    Math.min(120, lineC.getAlpha()) / 2));
                graphics.setStroke(new BasicStroke(thick + 3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.drawLine(pa.getX(), pa.getY(), pb.getX(), pb.getY());
            }

            boolean dashed = config.lineStyle() != com.routeplanner.LineStyle.SOLID;
            boolean useDashColor = dashed && config.lineUseDashColor();

            // Faint base line under the dashes when a separate dash color is used
            if (useDashColor) {
                graphics.setColor(new Color(lineC.getRed(), lineC.getGreen(), lineC.getBlue(),
                    (int) (lineC.getAlpha() * 0.45)));
                graphics.setStroke(new BasicStroke(thick, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.drawLine(pa.getX(), pa.getY(), pb.getX(), pb.getY());
            }

            // Main stroke (dashes/dots layered on top, or the solid line)
            graphics.setColor(useDashColor ? config.lineDashColor() : lineC);
            graphics.setStroke(lineStroke(thick));
            graphics.drawLine(pa.getX(), pa.getY(), pb.getX(), pb.getY());

            // Arrows
            com.routeplanner.ArrowMode am = config.arrowMode();
            if (am == com.routeplanner.ArrowMode.ALONG && i % 20 == 10) {
                double angle = Math.atan2(pb.getY() - pa.getY(), pb.getX() - pa.getX());
                drawDirectionArrow(graphics, (pa.getX() + pb.getX()) / 2,
                    (pa.getY() + pb.getY()) / 2, angle, lineC);
            }
        }

        // End-only arrow at the destination
        if (drawLine && config.arrowMode() == com.routeplanner.ArrowMode.END && path.size() >= 2) {
            WorldPoint wa = path.get(path.size() - 2);
            WorldPoint wb = path.get(path.size() - 1);
            LocalPoint la = LocalPoint.fromWorld(client, wa);
            LocalPoint lb = LocalPoint.fromWorld(client, wb);
            if (la != null && lb != null) {
                net.runelite.api.Point pa = Perspective.localToCanvas(client, la, plane);
                net.runelite.api.Point pb = Perspective.localToCanvas(client, lb, plane);
                if (pa != null && pb != null) {
                    double angle = Math.atan2(pb.getY() - pa.getY(), pb.getX() - pa.getX());
                    drawDirectionArrow(graphics, pb.getX(), pb.getY(), angle, config.lineColor());
                }
            }
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
            ? new BasicStroke(borderW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
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
            java.awt.Shape shape = rounded ? roundedTile(poly) : null;

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
     * No pathfinding or collision is involved -- it exists purely to show off the current
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
            net.runelite.api.Scene scene = client.getScene();
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
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /**
     * Soften the corners of a projected tile polygon. Because tiles are drawn in perspective the
     * result is a rounded diamond rather than a rounded square, but it takes the hard edge off.
     */
    private java.awt.geom.Path2D roundedTile(Polygon poly) {
        int n = poly.npoints;
        java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
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

    /** Build the main line stroke: dash pattern from the style, animated offset when enabled. */
    private BasicStroke lineStroke(int thick) {
        float t = Math.max(1, thick);
        float[] dash;
        switch (config.lineStyle()) {
            case DASHED:   dash = new float[]{ t * 4f, t * 3f }; break;
            case DOTTED:   dash = new float[]{ 0.1f, t * 2.2f }; break;
            case DASH_DOT: dash = new float[]{ t * 4f, t * 2f, 0.1f, t * 2f }; break;
            default:       return new BasicStroke(t, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        }
        float offset = 0f;
        if (config.lineAnimate()) {
            // Continuous flow: advance the dash phase over time (render runs per-frame).
            // BasicStroke requires dash_phase >= 0 -- a negative phase throws and the line
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
        int cap = (config.lineStyle() == com.routeplanner.LineStyle.DOTTED)
            ? BasicStroke.CAP_ROUND : BasicStroke.CAP_BUTT;
        return new BasicStroke(t, cap, BasicStroke.JOIN_ROUND, 10f, dash, offset);
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

    /**
     * If the player is standing on (or adjacent to) a tile in the current cachedPath, return that
     * tile's index; otherwise -1 (meaning we've left the route and need a fresh path).
     */
    private int indexOnPath(WorldPoint player) {
        if (cachedPath == null || cachedPath.isEmpty()) return -1;
        int bestIdx = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < cachedPath.size(); i++) {
            WorldPoint wp = cachedPath.get(i);
            if (wp.getPlane() != player.getPlane()) continue;
            int d = wp.distanceTo(player);
            if (d < bestDist) { bestDist = d; bestIdx = i; }
        }
        // Consider "on path" only if within 1 tile of a path node.
        return bestDist <= 1 ? bestIdx : -1;
    }

    /** Drop path nodes before idx so the line starts at the player's current position. */
    private void trimCachedPathTo(int idx) {
        if (idx <= 0 || cachedPath == null || idx >= cachedPath.size()) return;
        cachedPath = new ArrayList<>(cachedPath.subList(idx, cachedPath.size()));
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
