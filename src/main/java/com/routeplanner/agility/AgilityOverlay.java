package com.routeplanner.agility;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import javax.inject.Inject;
import java.awt.*;
import java.util.Map;

@Slf4j
public class AgilityOverlay extends Overlay {

    private final Client client;
    private final AgilityTaskManager manager;
    private final ModelOutlineRenderer outlineRenderer;

    private boolean flashState = false;
    private long lastFlash = 0;
    private static final long FLASH_MS = 600;

    @Inject
    public AgilityOverlay(Client client, AgilityTaskManager manager,
                          ModelOutlineRenderer outlineRenderer) {
        this.client = client;
        this.manager = manager;
        this.outlineRenderer = outlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        AgilityTask task = manager.getActiveTask();
        if (task == null) return null;

        long now = System.currentTimeMillis();
        if (now - lastFlash > FLASH_MS) {
            flashState = !flashState;
            lastFlash = now;
        }

        AgilityObstacle current = task.getCurrentObstacle();
        if (current == null) return null;

        int currentIndex = task.getCurrentObstacleIndex();

        // Draw all obstacles from our map
        for (Map.Entry<TileObject, Integer> entry : manager.getObstacleMap().entrySet()) {
            TileObject obj = entry.getKey();
            int index = entry.getValue();
            boolean isCurrent = index == currentIndex;
            boolean isNext = index == (currentIndex + 1) % task.getCourse().getObstacles().size();

            Color outlineColor = isCurrent
                ? (flashState ? new Color(255, 165, 0, 255) : new Color(255, 140, 0, 180))
                : isNext
                ? new Color(255, 255, 0, 150)
                : new Color(200, 50, 50, 80);

            int thickness = isCurrent ? (flashState ? 4 : 2) : isNext ? 2 : 1;

            // Draw outline using specific type
            if (obj instanceof GroundObject) {
                outlineRenderer.drawOutline((GroundObject) obj, thickness, outlineColor, 0);
            } else if (obj instanceof DecorativeObject) {
                outlineRenderer.drawOutline((DecorativeObject) obj, thickness, outlineColor, 0);
            } else if (obj instanceof WallObject) {
                outlineRenderer.drawOutline((WallObject) obj, thickness, outlineColor, 0);
            } else if (obj instanceof GameObject) {
                outlineRenderer.drawOutline((GameObject) obj, thickness, outlineColor, 0);
            }

            // Draw tile highlight
            LocalPoint lp = obj.getLocalLocation();
            if (lp != null) {
                Polygon poly = net.runelite.api.Perspective.getCanvasTilePoly(client, lp);
                if (poly != null) {
                    Color fill = isCurrent
                        ? new Color(255, 165, 0, 50)
                        : isNext
                        ? new Color(255, 255, 0, 25)
                        : new Color(200, 50, 50, 15);
                    graphics.setColor(fill);
                    graphics.fill(poly);
                    graphics.setColor(outlineColor);
                    graphics.setStroke(new BasicStroke(thickness));
                    graphics.draw(poly);
                }

                // Flashing arrow above current obstacle
                if (isCurrent && flashState) {
                    drawArrow(graphics, lp);
                }
            }
        }

        return null;
    }

    private void drawArrow(Graphics2D graphics, LocalPoint lp) {
        net.runelite.api.Point p = net.runelite.api.Perspective.localToCanvas(
            client, lp, client.getTopLevelWorldView().getPlane(), 60);
        if (p == null) return;

        int x = p.getX();
        int y = p.getY();
        int s = 14;

        int[] xs = {x, x - s, x + s};
        int[] ys = {y + s, y - s, y - s};
        graphics.setColor(Color.BLACK);
        graphics.fillPolygon(xs, ys, 3);

        int[] xi = {x, x - s + 3, x + s - 3};
        int[] yi = {y + s - 2, y - s + 3, y - s + 3};
        graphics.setColor(new Color(255, 165, 0));
        graphics.fillPolygon(xi, yi, 3);
    }


}
