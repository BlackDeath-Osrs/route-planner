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
import java.awt.geom.AffineTransform;

public class MinimapArrowOverlay extends Overlay {

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final WorldMapOverlay worldMapOverlay;

    private boolean flashState = false;
    private long lastFlash = 0;
    private static final long FLASH_MS = 600;

    @Inject
    public MinimapArrowOverlay(Client client, RoutePlannerPlugin plugin, WorldMapOverlay worldMapOverlay) {
        this.client = client;
        this.plugin = plugin;
        this.worldMapOverlay = worldMapOverlay;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null || step.getWorldPoint() == null) return null;

        WorldPoint player = client.getLocalPlayer().getWorldLocation();
        WorldPoint target = step.getWorldPoint();

        if (player.equals(target)) return null;

        // Flash logic
        long now = System.currentTimeMillis();
        if (now - lastFlash > FLASH_MS) {
            flashState = !flashState;
            lastFlash = now;
        }
        if (!flashState) return null;

        // Get minimap position of player and target
        Point playerMini = worldMapOverlay.mapWorldPointToGraphicsPoint(player);
        Point targetMini = worldMapOverlay.mapWorldPointToGraphicsPoint(target);

        if (playerMini == null || targetMini == null) return null;

        // Calculate angle from player to target on minimap
        double angle = Math.atan2(
            targetMini.getY() - playerMini.getY(),
            targetMini.getX() - playerMini.getX()
        );

        // Draw arrow at player position pointing toward target
        drawArrow(graphics, playerMini.getX(), playerMini.getY(), angle);

        return null;
    }

    private void drawArrow(Graphics2D graphics, int x, int y, double angle) {
        int size = 10;

        Polygon arrow = new Polygon();
        arrow.addPoint(size, 0);       // tip
        arrow.addPoint(-size / 2, -size / 2); // left base
        arrow.addPoint(-size / 2, size / 2);  // right base

        AffineTransform old = graphics.getTransform();
        graphics.translate(x, y);
        graphics.rotate(angle);

        // Shadow
        graphics.setColor(Color.BLACK);
        graphics.fillPolygon(arrow);

        // Blue arrow
        graphics.setColor(new Color(255, 140, 0));
        Polygon innerArrow = new Polygon();
        innerArrow.addPoint(size - 2, 0);
        innerArrow.addPoint(-size / 2 + 1, -size / 2 + 2);
        innerArrow.addPoint(-size / 2 + 1, size / 2 - 2);
        graphics.fillPolygon(innerArrow);

        graphics.setTransform(old);
    }
}
