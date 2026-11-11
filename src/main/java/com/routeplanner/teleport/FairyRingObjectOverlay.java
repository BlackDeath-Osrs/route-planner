package com.routeplanner.teleport;

import com.routeplanner.HighlightIcon;
import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import com.routeplanner.util.SceneObjects;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

/**
 * On a fairy ring step inside an instance (a player-owned house), highlights every fairy ring
 * there, in any of its forms: the plain ring and the spiritual fairy tree. Outside instances the
 * path already leads to, and lights, the nearest ring in the world.
 */
@Singleton
public class FairyRingObjectOverlay extends Overlay {

    private final Client client;
    private final RoutePlannerPlugin plugin;

    /** The rings found in the scene, looked for again once a tick. */
    private final List<GameObject> rings = new ArrayList<>();
    private int scannedTick = -1;

    @Inject
    public FairyRingObjectOverlay(Client client, RoutePlannerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        WorldView wv = client.getTopLevelWorldView();
        if (!FairyRings.isFairyRingStep(step) || !step.isHighlightEnabled() || wv == null || !wv.isInstance()) return null;

        if (client.getTickCount() != scannedTick) {
            scannedTick = client.getTickCount();
            scan(wv);
        }
        for (GameObject ring : rings) draw(graphics, ring.getConvexHull());
        return null;
    }

    private void scan(WorldView wv) {
        rings.clear();
        if (wv.getScene() == null) return;
        for (Tile[] column : wv.getScene().getTiles()[wv.getPlane()]) {
            for (Tile tile : column) {
                if (tile == null || tile.getGameObjects() == null) continue;
                for (GameObject obj : tile.getGameObjects()) {
                    if (!SceneObjects.isObject(obj)) continue;
                    // A big object is listed on each of its tiles: keep it once, from its corner
                    if (!tile.getSceneLocation().equals(obj.getSceneMinLocation())) continue;
                    if (FairyRings.isRingName(name(obj.getId()))) rings.add(obj);
                }
            }
        }
    }

    private String name(int id) {
        ObjectComposition comp = client.getObjectDefinition(id);
        if (comp != null && comp.getImpostorIds() != null) comp = comp.getImpostor();
        return comp == null || comp.getName() == null ? null : comp.getName().toLowerCase();
    }

    private void draw(Graphics2D graphics, Shape hull) {
        if (hull == null) return;
        Color base = plugin.getConfig().npcHighlightColor();
        graphics.setColor(ColorUtil.colorWithAlpha(base, Math.max(0, Math.min(255, (int) (base.getAlpha() * 0.22)))));
        graphics.fill(hull);
        graphics.setColor(base);
        graphics.setStroke(new BasicStroke(2));
        graphics.draw(hull);
        HighlightIcon.drawAbove(graphics, plugin, hull);
    }
}
