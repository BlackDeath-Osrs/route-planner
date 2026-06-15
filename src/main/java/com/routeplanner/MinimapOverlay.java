package com.routeplanner;

import com.routeplanner.model.RouteStep;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

import javax.inject.Inject;
import java.awt.*;

public class MinimapOverlay extends Overlay {

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final RoutePlannerConfig config;
    private final WorldMapOverlay worldMapOverlay;

    @Inject
    public MinimapOverlay(Client client, RoutePlannerPlugin plugin, RoutePlannerConfig config, WorldMapOverlay worldMapOverlay) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.worldMapOverlay = worldMapOverlay;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;

        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null || step.getWorldPoint() == null) return null;

        drawMinimapDot(graphics, step.getWorldPoint());
        return null;
    }

    private void drawMinimapDot(Graphics2D graphics, WorldPoint worldPoint) {
        Point minimapPoint = worldMapOverlay.mapWorldPointToGraphicsPoint(worldPoint);
        if (minimapPoint == null) return;

        int x = minimapPoint.getX();
        int y = minimapPoint.getY();
        int size = 6;

        graphics.setColor(Color.BLACK);
        graphics.fillOval(x - size / 2 - 1, y - size / 2 - 1, size + 2, size + 2);

        graphics.setColor(config.minimapColor());
        graphics.fillOval(x - size / 2, y - size / 2, size, size);
    }
}
