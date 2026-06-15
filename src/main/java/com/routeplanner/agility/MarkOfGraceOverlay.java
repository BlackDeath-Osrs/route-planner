package com.routeplanner.agility;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.ItemDespawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
public class MarkOfGraceOverlay extends Overlay {

    private static final int MARK_OF_GRACE_ID = 11849;
    private static final Color MARK_COLOR      = new Color(0, 255, 150, 200);
    private static final Color MARK_FILL       = new Color(0, 255, 150, 50);
    private static final Color MARK_TEXT       = new Color(0, 255, 150, 255);

    private final Client client;
    private final AgilityTaskManager manager;

    // WorldPoint -> TileItem
    private final Map<WorldPoint, TileItem> marks = new HashMap<>();

    private boolean flashState = false;
    private long lastFlash = 0;

    @Inject
    public MarkOfGraceOverlay(Client client, AgilityTaskManager manager) {
        this.client = client;
        this.manager = manager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Subscribe
    public void onItemSpawned(ItemSpawned event) {
        if (event.getItem().getId() == MARK_OF_GRACE_ID) {
            marks.put(event.getTile().getWorldLocation(), event.getItem());
            log.debug("Mark of Grace spawned at {}", event.getTile().getWorldLocation());
        }
    }

    @Subscribe
    public void onItemDespawned(ItemDespawned event) {
        if (event.getItem().getId() == MARK_OF_GRACE_ID) {
            marks.remove(event.getTile().getWorldLocation());
        }
    }

    public void clearMarks() {
        marks.clear();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (manager.getActiveTask() == null) return null;
        if (marks.isEmpty()) return null;

        long now = System.currentTimeMillis();
        if (now - lastFlash > 500) {
            flashState = !flashState;
            lastFlash = now;
        }

        for (WorldPoint wp : marks.keySet()) {
            LocalPoint lp = LocalPoint.fromWorld(client, wp);
            if (lp == null) continue;

            // Tile highlight
            Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly != null) {
                graphics.setColor(MARK_FILL);
                graphics.fill(poly);
                graphics.setColor(flashState ? MARK_COLOR : new Color(0, 200, 120, 150));
                graphics.setStroke(new BasicStroke(2));
                graphics.draw(poly);
            }

            // Label above tile
            net.runelite.api.Point textPoint = Perspective.getCanvasTextLocation(
                client, graphics, lp, "Mark of Grace", 20);
            if (textPoint != null) {
                // Shadow
                graphics.setColor(Color.BLACK);
                graphics.drawString("Mark of Grace", textPoint.getX() + 1, textPoint.getY() + 1);
                // Text
                graphics.setColor(MARK_TEXT);
                graphics.setFont(new Font("Arial", Font.BOLD, 12));
                graphics.drawString("Mark of Grace", textPoint.getX(), textPoint.getY());
            }

            // Arrow pointing down to the mark
            net.runelite.api.Point p = Perspective.localToCanvas(client, lp,
                client.getTopLevelWorldView().getPlane(), 40);
            if (p != null && flashState) {
                drawArrow(graphics, p.getX(), p.getY());
            }
        }

        return null;
    }

    private void drawArrow(Graphics2D graphics, int x, int y) {
        int s = 8;
        int[] xs = {x, x - s, x + s};
        int[] ys = {y + s, y - s, y - s};
        graphics.setColor(Color.BLACK);
        graphics.fillPolygon(xs, ys, 3);
        int[] xi = {x, x - s + 2, x + s - 2};
        int[] yi = {y + s - 1, y - s + 2, y - s + 2};
        graphics.setColor(MARK_COLOR);
        graphics.fillPolygon(xi, yi, 3);
    }
}
