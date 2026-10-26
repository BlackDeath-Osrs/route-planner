package com.routeplanner.sailing;

import com.routeplanner.LineStyle;
import com.routeplanner.PathfinderOverlay;
import com.routeplanner.RoutePlannerConfig;
import com.routeplanner.util.Strokes;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * While trawling (a net lowered), draws the nearest shoal's route in the waypoint line's style, its
 * dashes flowing the way the shoal swims, marks its stops, and highlights the shoal.
 *
 */
@lombok.extern.slf4j.Slf4j
public class ShoalRouteOverlay extends Overlay {

    private static final BasicStroke OUTLINE_STROKE = new BasicStroke(2);
    /** Keep showing the route this long after the nets come up (between stops, changing depth). */
    private static final int AFTER_NETS_TICKS = 100;
    /** A dot in the dotted and dash-dot patterns, in tiles (drawn as a round-capped point). */
    private static final double DOT_TILES = 0.05;
    /** One full pattern of dashes slides past every this many milliseconds. */
    private static final long DASH_CYCLE_MS = 1200;
    /** Route points further apart than this aren't joined up. */
    private static final int MAX_SEGMENT_TILES = 20;

    private final Client client;
    private final RoutePlannerConfig config;
    private int netsDownTick = -1000;
    /** The last shoal whose route match was traced, so it's logged once per shoal. */
    private int tracedShoal = -1;
    /** A shoal is on a route if the route's path passes within this many tiles of it. */
    private static final int ROUTE_MATCH_TILES = 10;

    @Inject
    public ShoalRouteOverlay(Client client, RoutePlannerConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        WorldView top = client.getTopLevelWorldView();
        if (top == null) return null;
        boolean sailing = Sailing.isSailing(client);
        if (sailing && Shoals.netsDown(client)) netsDownTick = client.getTickCount();
        boolean trawling = sailing && client.getTickCount() - netsDownTick <= AFTER_NETS_TICKS;

        // "Always show shoal routes": every route in view, shoal or no shoal
        if (config.shoalRoutesAlways()) {
            WorldPoint at = Sailing.position(client);
            int range = Math.max(30, top.getScene().getDrawDistance());
            for (ShoalRoutes.Route r : ShoalRoutes.all()) {
                // Only routes within render distance: the rest aren't worked through at all
                if (r.path.size() > 1 && at != null && r.near(at, range)) drawRoute(graphics, top, r);
            }
        }
        if (!trawling) return null;

        WorldEntity shoal = Shoals.nearest(client);
        if (shoal == null) return null;

        LocalPoint shoalLp = shoal.getLocalLocation();
        WorldPoint shoalAt = shoalLp == null ? null : WorldPoint.fromLocalInstance(client, shoalLp);
        int id = shoal.getWorldView() == null ? -1 : shoal.getWorldView().getId();
        ShoalRoutes.Route route = shoalAt == null ? null : ShoalRoutes.find(id, shoalAt, ROUTE_MATCH_TILES);
        if (id != tracedShoal && config.recordTrace()) {
            tracedShoal = id;
            log.info("[RP-TRACE] SHOAL worldview={} at={} route={}", id, shoalAt,
                route == null ? "none" : route == ShoalRoutes.get(id) ? "by id" : "by position");
        }
        if (route != null && route.path.size() > 1 && !config.shoalRoutesAlways()) drawRoute(graphics, top, route);
        highlightShoal(graphics, shoal);
        return null;
    }

    /**
     * The whole route, fixed in place like the Deep Sea Trawling plugin draws it: the path in the order
     * the shoal swims it (the dashes flow that way) wherever it's in the scene, and a marker on each stop.
     */
    private void drawRoute(Graphics2D g, WorldView top, ShoalRoutes.Route route) {
        List<WorldPoint> path = route.path;
        int plane = top.getPlane();
        int thick = config.lineThickness();
        Color c = config.lineColor();

        Path2D.Float line = new Path2D.Float();
        boolean drawing = false;
        WorldPoint prevWp = null;
        // Back to the first point at the end when it's a loop
        boolean loop = path.get(0).distanceTo2D(path.get(path.size() - 1)) <= MAX_SEGMENT_TILES;
        for (int i = 0; i < path.size() + (loop ? 1 : 0); i++) {
            WorldPoint wp = path.get(i % path.size());
            Point p = toCanvas(top, wp, plane);
            // A gap (out of the scene, or points too far apart to be one stretch) starts a new run
            if (p == null || (prevWp != null && wp.distanceTo2D(prevWp) > MAX_SEGMENT_TILES)) drawing = false;
            prevWp = wp;
            if (p == null) continue;
            if (drawing) line.lineTo(p.getX(), p.getY());
            else line.moveTo(p.getX(), p.getY());
            drawing = true;
        }
        if (config.lineGlow()) {
            g.setColor(ColorUtil.colorWithAlpha(c, Math.min(120, c.getAlpha()) / 2));
            g.setStroke(Strokes.round(thick + 3));
            g.draw(line);
        }
        LineStyle style = config.lineStyle();
        if (style == LineStyle.TRACER) {
            g.setColor(PathfinderOverlay.tracerBaseColor(config));
            g.setStroke(Strokes.round(thick));
            g.draw(line);
            drawWaterPulses(g, top, path, loop, thick);
        } else if (style == LineStyle.SOLID) {
            g.setColor(c);
            g.setStroke(Strokes.round(thick));
            g.draw(line);
        } else {
            if (config.lineUseDashColor()) {
                // Faint line under the dashes when they have their own color, as on the waypoint line
                g.setColor(ColorUtil.colorWithAlpha(c, (int) (c.getAlpha() * 0.45)));
                g.setStroke(Strokes.round(thick));
                g.draw(line);
            }
            drawWaterDashes(g, top, path, loop, style, config.lineUseDashColor() ? config.lineDashColor() : c, thick);
        }

        // Where it stops (and the nets catch most): a square on the water
        g.setStroke(OUTLINE_STROKE);
        for (WorldPoint stop : route.stops) {
            LocalPoint lp = LocalPoint.fromWorld(top, stop);
            Polygon poly = lp == null ? null : Perspective.getCanvasTileAreaPoly(client, lp, 3);
            if (poly == null) continue;
            g.setColor(ColorUtil.colorWithAlpha(c, 40));
            g.fillPolygon(poly);
            g.setColor(c);
            g.drawPolygon(poly);
        }
    }

    /**
     * Dashes laid out on the water along the route, a fixed length in tiles, sliding forward the way the
     * shoal swims. Measured on screen instead (as a dashed stroke does), they'd squash and jump while the
     * boat follows the shoal and the camera moves.
     */
    private void drawWaterDashes(Graphics2D g, WorldView top, List<WorldPoint> path, boolean loop,
                                 LineStyle style, Color color, int thick) {
        // dash, gap (and a dot and gap after it for dash-dot), in tiles
        double[] pattern;
        if (style == LineStyle.DOTTED)        pattern = new double[]{DOT_TILES, 0.6};
        else if (style == LineStyle.DASH_DOT) pattern = new double[]{1.2, 0.5, DOT_TILES, 0.5};
        else                                  pattern = new double[]{1.2, 0.8};
        double period = 0;
        for (double d : pattern) period += d;
        double shift = 0;
        if (config.lineAnimate()) shift = (System.currentTimeMillis() % DASH_CYCLE_MS) / (double) DASH_CYCLE_MS * period;

        g.setColor(color);
        g.setStroke(Strokes.round(thick));
        int plane = top.getPlane();
        double along = 0;  // distance along the route at the start of this segment, in tiles
        int n = path.size() + (loop ? 1 : 0);
        for (int i = 0; i + 1 < n; i++) {
            WorldPoint a = path.get(i % path.size()), b = path.get((i + 1) % path.size());
            double len = Math.hypot(b.getX() - a.getX(), b.getY() - a.getY());
            if (len == 0 || len > MAX_SEGMENT_TILES) { along += len; continue; }
            // Each dash and dot of the pattern that falls on this segment, the pattern moved on by shift
            double first = Math.floor((along - shift) / period) * period + shift;
            for (double start = first; start < along + len; start += period) {
                double at = start;
                for (int k = 0; k < pattern.length; k += 2) {
                    double from = Math.max(at, along), to = Math.min(at + pattern[k], along + len);
                    if (to > from || (pattern[k] == DOT_TILES && from <= to)) {
                        drawWaterLine(g, top, plane, a, b, (from - along) / len, (to - along) / len);
                    }
                    at += pattern[k] + pattern[k + 1];
                }
            }
            along += len;
        }
    }

    /** The tracer's pulses on the water: a tile-long bright stretch every few dozen tiles, moving the way the shoal swims. */
    private void drawWaterPulses(Graphics2D g, WorldView top, List<WorldPoint> path, boolean loop, int thick) {
        double spacing = PathfinderOverlay.TRACER_SPACING;
        double shift = PathfinderOverlay.tracerShift(config);
        g.setColor(PathfinderOverlay.tracerPulseColor(config));
        g.setStroke(Strokes.round(thick));
        int plane = top.getPlane();
        double along = 0;
        int n = path.size() + (loop ? 1 : 0);
        for (int i = 0; i + 1 < n; i++) {
            WorldPoint a = path.get(i % path.size()), b = path.get((i + 1) % path.size());
            double len = Math.hypot(b.getX() - a.getX(), b.getY() - a.getY());
            if (len == 0 || len > MAX_SEGMENT_TILES) { along += len; continue; }
            double first = Math.ceil((along - shift) / spacing) * spacing + shift;
            for (double at = first; at < along + len; at += spacing) {
                drawWaterLine(g, top, plane, a, b, (at - along) / len, Math.min(1, (at + 1 - along) / len));
            }
            along += len;
        }
    }

    /** The stretch from fraction {@code f0} to {@code f1} of the way from a to b, through tile centres. */
    private void drawWaterLine(Graphics2D g, WorldView top, int plane, WorldPoint a, WorldPoint b, double f0, double f1) {
        Point p0 = waterToCanvas(top, plane, a, b, f0), p1 = waterToCanvas(top, plane, a, b, f1);
        if (p0 != null && p1 != null) g.drawLine(p0.getX(), p0.getY(), p1.getX(), p1.getY());
    }

    private Point waterToCanvas(WorldView top, int plane, WorldPoint a, WorldPoint b, double f) {
        double x = a.getX() + (b.getX() - a.getX()) * f, y = a.getY() + (b.getY() - a.getY()) * f;
        int lx = (int) Math.round((x - top.getBaseX()) * Perspective.LOCAL_TILE_SIZE) + Perspective.LOCAL_HALF_TILE_SIZE;
        int ly = (int) Math.round((y - top.getBaseY()) * Perspective.LOCAL_TILE_SIZE) + Perspective.LOCAL_HALF_TILE_SIZE;
        int size = top.getSizeX() * Perspective.LOCAL_TILE_SIZE, sizeY = top.getSizeY() * Perspective.LOCAL_TILE_SIZE;
        if (lx < 0 || ly < 0 || lx >= size || ly >= sizeY) return null;
        return Perspective.localToCanvas(client, new LocalPoint(lx, ly, top), plane);
    }

    private Point toCanvas(WorldView top, WorldPoint wp, int plane) {
        LocalPoint lp = LocalPoint.fromWorld(top, wp);
        return lp == null ? null : Perspective.localToCanvas(client, lp, plane);
    }

    /**
     * Marks the shoal with a square on the water the size of its clickbox (the biggest object in the
     * shoal's own little world), or 3x3 where the shoal is when that isn't loaded. Its fish and
     * ripple models make an odd outline, so they aren't traced.
     */
    private void highlightShoal(Graphics2D g, WorldEntity shoal) {
        LocalPoint at = shoal.getLocalLocation();
        int size = 3;
        WorldView wv = shoal.getWorldView();
        // The shoal's scene can still be loading when it comes into view
        Tile[][][] tiles = wv == null || wv.getScene() == null ? null : wv.getScene().getTiles();
        if (tiles != null && wv.getPlane() >= 0 && wv.getPlane() < tiles.length) {
            int best = 0;
            for (Tile[] row : tiles[wv.getPlane()]) {
                if (row == null) continue;
                for (Tile t : row) {
                    if (t == null) continue;
                    for (GameObject obj : t.getGameObjects()) {
                        if (obj == null) continue;
                        ObjectComposition def = client.getObjectDefinition(obj.getId());
                        if (def == null) continue;
                        int area = def.getSizeX() * def.getSizeY();
                        if (area > best && obj.getLocalLocation() != null) {
                            best = area;
                            size = Math.max(1, Math.max(def.getSizeX(), def.getSizeY()));
                            at = obj.getLocalLocation();
                        }
                    }
                }
            }
        }
        Polygon poly = at == null ? null : Perspective.getCanvasTileAreaPoly(client, at, size);
        if (poly == null) return;
        Color base = config.npcHighlightColor();
        g.setStroke(OUTLINE_STROKE);
        g.setColor(ColorUtil.colorWithAlpha(base, Math.max(0, Math.min(255, base.getAlpha() / 4))));
        g.fillPolygon(poly);
        g.setColor(base);
        g.drawPolygon(poly);
    }
}
