package com.routeplanner.skilling;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;

/**
 * Highlights matching game objects (e.g. thieving stalls) for a SKILLING step
 * that defines a target object name.
 */
@Singleton
public class StallHighlightOverlay extends Overlay {

    private final Client client;
    private final RoutePlannerPlugin plugin;

    @Inject
    public StallHighlightOverlay(Client client, RoutePlannerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null || step.getType() != StepType.SKILLING) return null;
        String targetName = step.getSkillingTargetObject();
        if (targetName == null || targetName.trim().isEmpty()) return null;
        String target = targetName.trim().toLowerCase();

        net.runelite.api.coords.WorldPoint playerLoc =
            client.getLocalPlayer() != null ? client.getLocalPlayer().getWorldLocation() : null;
        if (playerLoc == null) return null;

        Scene scene = client.getScene();
        if (scene == null) return null;
        Tile[][][] tiles = scene.getTiles();
        int z = client.getPlane();
        if (z < 0 || z >= tiles.length) return null;

        for (Tile[] row : tiles[z]) {
            if (row == null) continue;
            for (Tile tile : row) {
                if (tile == null) continue;
                if (tile.getWorldLocation() == null
                    || playerLoc.distanceTo(tile.getWorldLocation()) > 20) continue;
                GameObject[] objects = tile.getGameObjects();
                if (objects == null) continue;
                for (GameObject obj : objects) {
                    if (obj == null) continue;
                    // GameObjects span multiple tiles; only draw from the anchor tile
                    if (!obj.getSceneMinLocation().equals(tile.getSceneLocation())) continue;
                    ObjectComposition comp = client.getObjectDefinition(obj.getId());
                    if (comp == null) continue;
                    String name = comp.getName();
                    if (name == null || !name.toLowerCase().startsWith(target)) continue;

                    Shape hull = obj.getConvexHull();
                    if (hull == null) continue;
                    Color base = plugin.getConfig().npcHighlightColor();
                    int outA = base.getAlpha();
                    int fillA = Math.max(0, Math.min(255, (int) (outA * 0.22)));
                    graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), fillA));
                    graphics.fill(hull);
                    graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), outA));
                    graphics.setStroke(new BasicStroke(2));
                    graphics.draw(hull);
                }
            }
        }
        return null;
    }
}
