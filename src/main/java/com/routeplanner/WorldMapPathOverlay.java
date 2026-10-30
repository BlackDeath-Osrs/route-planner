package com.routeplanner;

import com.routeplanner.model.RouteStep;
import com.routeplanner.model.TileArea;
import com.routeplanner.sailing.Sailing;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

public class WorldMapPathOverlay extends Overlay {

    // World map interface ID and map view child ID
    private static final int WORLD_MAP_INTERFACE = 595;
    private static final int WORLD_MAP_CHILD = 7;

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final WorldMapOverlay worldMapOverlay;
    private final PathfinderOverlay pathfinderOverlay;
    private final RoutePlannerConfig config;

    @Inject
    public WorldMapPathOverlay(Client client, RoutePlannerPlugin plugin, RoutePlannerConfig config,
                                WorldMapOverlay worldMapOverlay,
                                PathfinderOverlay pathfinderOverlay) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.worldMapOverlay = worldMapOverlay;
        this.pathfinderOverlay = pathfinderOverlay;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null) return null;
        // Until the player boards (Sailing), the dock is where they're headed.
        WorldPoint target = plugin.getPathfinderOverlay().isBoardingPending(step) ? step.getBoardPoint() : step.getWorldPoint();
        if (target == null) return null;

        // Check world map is open
        Widget mapWidget = client.getWidget(WORLD_MAP_INTERFACE, WORLD_MAP_CHILD);
        if (mapWidget == null || mapWidget.isHidden()) return null;

        Rectangle mapBounds = mapWidget.getBounds();
        if (mapBounds == null) return null;

        WorldPoint player = Sailing.position(client);
        if (player == null) return null;

        // The area first, under the line
        TileArea area = plugin.areaOf(step);
        if (area != null) {
            Shape clip = graphics.getClip();
            graphics.clip(mapBounds);
            drawArea(graphics, area);
            graphics.setClip(clip);
        }

        // A thin line with small arrowheads along it pointing the way to go
        Color lineColor = config.lineColor();
        graphics.setColor(new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue()));
        graphics.setStroke(new BasicStroke(1));
        Object oldAa = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape oldClip = graphics.getClip();
        graphics.clip(mapBounds);
        // The route the pathfinder worked out (round coasts at sea, along roads on land); a straight
        // line to the destination until there is one
        if (!drawRoute(graphics, pathfinderOverlay.getCachedPath())) {
            Point playerMapPoint = worldMapOverlay.mapWorldPointToGraphicsPoint(player);
            Point targetMapPoint = worldMapOverlay.mapWorldPointToGraphicsPoint(target);
            if (playerMapPoint != null && targetMapPoint != null) {
                List<Point> run = new ArrayList<>();
                run.add(playerMapPoint);
                run.add(targetMapPoint);
                drawRun(graphics, run);
            }
        }
        graphics.setClip(oldClip);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);

        // Draw destination dot
        Point destPoint = worldMapOverlay.mapWorldPointToGraphicsPoint(target);
        if (destPoint != null && mapBounds.contains(destPoint.getX(), destPoint.getY())) {
            int size = 10;
            int x = destPoint.getX() - size / 2;
            int y = destPoint.getY() - size / 2;

            graphics.setColor(Color.BLACK);
            graphics.fill(new Ellipse2D.Double(x - 1, y - 1, size + 2, size + 2));
            graphics.setColor(new Color(255, 140, 0));
            graphics.fill(new Ellipse2D.Double(x, y, size, size));

            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font("Arial", Font.BOLD, 11));
            graphics.drawString(step.getName(), destPoint.getX() + 8, destPoint.getY() + 4);
        }

        // Draw player dot
        Point playerPoint = worldMapOverlay.mapWorldPointToGraphicsPoint(player);
        if (playerPoint != null && mapBounds.contains(playerPoint.getX(), playerPoint.getY())) {
            int size = 8;
            int x = playerPoint.getX() - size / 2;
            int y = playerPoint.getY() - size / 2;
            graphics.setColor(Color.BLACK);
            graphics.fill(new Ellipse2D.Double(x - 1, y - 1, size + 2, size + 2));
            graphics.setColor(Color.WHITE);
            graphics.fill(new Ellipse2D.Double(x, y, size, size));
        }

        return null;
    }

    /** The step's area on the map, in the tile highlight colour. */
    private void drawArea(Graphics2D graphics, TileArea area) {
        Path2D.Float fill = new Path2D.Float();
        for (int[] r : area.rects()) {
            // Outer corners: one past the far tile, so a small area still covers its tiles
            Point a = mapPoint(r[0], r[1], area.getPlane()), b = mapPoint(r[0] + r[2], r[1] + r[3], area.getPlane());
            if (a == null || b == null) return;
            fill.append(new Rectangle(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                Math.max(1, Math.abs(b.getX() - a.getX())), Math.max(1, Math.abs(b.getY() - a.getY()))), false);
        }
        Path2D.Float outline = new Path2D.Float();
        for (int[] e : area.edges()) {
            Point a = mapPoint(e[0], e[1], area.getPlane()), b = mapPoint(e[2], e[3], area.getPlane());
            if (a == null || b == null) return;
            outline.moveTo(a.getX(), a.getY());
            outline.lineTo(b.getX(), b.getY());
        }
        Color c = config.tileHighlightColor();
        graphics.setColor(AreaGraphics.fill(c));
        graphics.fill(fill);
        graphics.setColor(AreaGraphics.outline(c));
        graphics.setStroke(new BasicStroke(2));
        graphics.draw(outline);
    }

    private Point mapPoint(int x, int y, int plane) {
        return worldMapOverlay.mapWorldPointToGraphicsPoint(new WorldPoint(x, y, plane));
    }

    /**
     * Draws the path on the map, broken where it jumps (stairs, a ship, a cave mouth) or goes off the
     * map view. Returns false if there's no path to draw.
     */
    private boolean drawRoute(Graphics2D graphics, List<WorldPoint> path) {
        if (path == null || path.size() < 2) return false;
        List<Point> run = new ArrayList<>();
        WorldPoint prev = null;
        for (WorldPoint wp : path) {
            Point p = worldMapOverlay.mapWorldPointToGraphicsPoint(wp);
            boolean jump = prev != null && (prev.getPlane() != wp.getPlane()
                || Math.max(Math.abs(prev.getX() - wp.getX()), Math.abs(prev.getY() - wp.getY())) > MAX_STEP_TILES);
            if (p == null || jump) {
                drawRun(graphics, run);
                run = new ArrayList<>();
            }
            prev = wp;
            if (p != null) run.add(p);
        }
        drawRun(graphics, run);
        return true;
    }

    /** One unbroken stretch of the route: the line, an arrowhead every so often, and one at its end. */
    private void drawRun(Graphics2D graphics, List<Point> run) {
        if (run.size() < 2) return;
        Path2D.Float line = new Path2D.Float();
        line.moveTo(run.get(0).getX(), run.get(0).getY());
        double[] at = new double[run.size()];  // distance along the run to each point, in pixels
        for (int i = 1; i < run.size(); i++) {
            line.lineTo(run.get(i).getX(), run.get(i).getY());
            at[i] = at[i - 1] + Math.hypot(run.get(i).getX() - run.get(i - 1).getX(), run.get(i).getY() - run.get(i - 1).getY());
        }
        graphics.draw(line);
        double total = at[run.size() - 1];
        if (total < ARROW_LENGTH) return;
        for (double d = ARROW_SPACING; d < total - ARROW_SPACING / 2; d += ARROW_SPACING) {
            drawArrowhead(graphics, run, at, d);
        }
        drawArrowhead(graphics, run, at, total);
    }

    /** A small filled arrowhead with its tip {@code d} pixels along the run, pointing along it. */
    private void drawArrowhead(Graphics2D graphics, List<Point> run, double[] at, double d) {
        // Aimed from a little way back rather than along the last segment: a path drawn tile by tile
        // zigzags on the diagonals, and the arrows would wobble with it
        double[] tip = pointAt(run, at, d);
        double[] back = pointAt(run, at, d - ARROW_LENGTH * 2);
        double angle = Math.atan2(tip[1] - back[1], tip[0] - back[0]);
        if (tip[0] == back[0] && tip[1] == back[1]) return;
        Path2D.Double head = new Path2D.Double();
        head.moveTo(0, 0);
        head.lineTo(-ARROW_LENGTH, -ARROW_LENGTH / 2);
        head.lineTo(-ARROW_LENGTH, ARROW_LENGTH / 2);
        head.closePath();
        AffineTransform tx = AffineTransform.getTranslateInstance(tip[0], tip[1]);
        tx.rotate(angle);
        graphics.fill(tx.createTransformedShape(head));
    }

    /** The point {@code d} pixels along the run (clamped to its ends). */
    private static double[] pointAt(List<Point> run, double[] at, double d) {
        if (d <= 0) return new double[]{run.get(0).getX(), run.get(0).getY()};
        for (int i = 1; i < run.size(); i++) {
            if (at[i] >= d) {
                double seg = at[i] - at[i - 1];
                double f = seg == 0 ? 0 : (d - at[i - 1]) / seg;
                Point a = run.get(i - 1), b = run.get(i);
                return new double[]{a.getX() + (b.getX() - a.getX()) * f, a.getY() + (b.getY() - a.getY()) * f};
            }
        }
        Point last = run.get(run.size() - 1);
        return new double[]{last.getX(), last.getY()};
    }

    /** Path points further apart than this are a jump (stairs, a ship, a cave mouth), not a line. */
    private static final int MAX_STEP_TILES = 5;
    /** Pixels between the arrowheads along the line, and the arrowhead's length. */
    private static final double ARROW_SPACING = 60;
    private static final double ARROW_LENGTH = 6;
}
