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
        if (step == null || step.getWorldPoint() == null) return null;

        // Check world map is open
        Widget mapWidget = client.getWidget(WORLD_MAP_INTERFACE, WORLD_MAP_CHILD);
        if (mapWidget == null || mapWidget.isHidden()) return null;

        Rectangle mapBounds = mapWidget.getBounds();
        if (mapBounds == null) return null;

        WorldPoint player = client.getLocalPlayer().getWorldLocation();
        WorldPoint target = step.getWorldPoint();

        // Draw straight line from player to destination on world map
        // (path tiles are too small to see on world map zoom, a line is clearer)
        Point playerMapPoint = worldMapOverlay.mapWorldPointToGraphicsPoint(player);
        Point targetMapPoint = worldMapOverlay.mapWorldPointToGraphicsPoint(target);

        if (playerMapPoint != null && targetMapPoint != null) {
            // Draw dashed line
            graphics.setColor(new Color(255, 140, 0, 200));
            float[] dash = {6f, 4f};
            graphics.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 1f, dash, 0f));
            graphics.drawLine(playerMapPoint.getX(), playerMapPoint.getY(),
                              targetMapPoint.getX(), targetMapPoint.getY());
        }

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
}
