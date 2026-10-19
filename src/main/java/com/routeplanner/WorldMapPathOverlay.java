package com.routeplanner;

import com.routeplanner.model.RouteStep;
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
import java.awt.geom.Ellipse2D;

public class WorldMapPathOverlay extends Overlay {

    // World map interface ID and map view child ID
    private static final int WORLD_MAP_INTERFACE = 595;
    private static final int WORLD_MAP_CHILD = 7;

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final WorldMapOverlay worldMapOverlay;
    private final PathfinderOverlay pathfinderOverlay;

    @Inject
    public WorldMapPathOverlay(Client client, RoutePlannerPlugin plugin,
                                WorldMapOverlay worldMapOverlay,
                                PathfinderOverlay pathfinderOverlay) {
        this.client = client;
        this.plugin = plugin;
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

        WorldPoint player = com.routeplanner.sailing.Sailing.position(client);
        if (player == null) return null;

        graphics.setColor(new Color(255, 140, 0, 200));
        float[] dash = {6f, 4f};
        graphics.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND, 1f, dash, 0f));
        Shape oldClip = graphics.getClip();
        graphics.clip(mapBounds);
        // The route the pathfinder worked out (round coasts at sea, along roads on land); a straight
        // line to the destination until there is one
        if (!drawRoute(graphics, pathfinderOverlay.getCachedPath())) {
            Point playerMapPoint = worldMapOverlay.mapWorldPointToGraphicsPoint(player);
            Point targetMapPoint = worldMapOverlay.mapWorldPointToGraphicsPoint(target);
            if (playerMapPoint != null && targetMapPoint != null) {
                graphics.drawLine(playerMapPoint.getX(), playerMapPoint.getY(),
                                  targetMapPoint.getX(), targetMapPoint.getY());
            }
        }
        graphics.setClip(oldClip);

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

    /**
     * Draws the path as one line on the map, broken where it jumps (stairs, a ship, a cave mouth) or goes
     * off the map view. Returns false if there's no path to draw.
     */
    private boolean drawRoute(Graphics2D graphics, java.util.List<WorldPoint> path) {
        if (path == null || path.size() < 2) return false;
        java.awt.geom.Path2D.Float line = new java.awt.geom.Path2D.Float();
        boolean drawing = false;
        WorldPoint prev = null;
        for (WorldPoint wp : path) {
            Point p = worldMapOverlay.mapWorldPointToGraphicsPoint(wp);
            boolean jump = prev != null && (prev.getPlane() != wp.getPlane()
                || Math.max(Math.abs(prev.getX() - wp.getX()), Math.abs(prev.getY() - wp.getY())) > MAX_STEP_TILES);
            if (p == null || jump) drawing = false;
            prev = wp;
            if (p == null) continue;
            if (drawing) line.lineTo(p.getX(), p.getY());
            else line.moveTo(p.getX(), p.getY());
            drawing = true;
        }
        graphics.draw(line);
        return true;
    }

    /** Path points further apart than this are a jump (stairs, a ship, a cave mouth), not a line. */
    private static final int MAX_STEP_TILES = 5;
}
