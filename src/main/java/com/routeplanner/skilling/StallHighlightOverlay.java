package com.routeplanner.skilling;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import com.routeplanner.sailing.Sailing;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
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

/**
 * Highlights matching game objects (e.g. thieving stalls) for a SKILLING step
 * that defines a target object name.
 */
@Singleton
public class StallHighlightOverlay extends Overlay {

    /** How far from the player targets are highlighted on land, in tiles. */
    private static final int HIGHLIGHT_RANGE = 20;

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
        if (step == null || !step.isHighlightEnabled() || step.getSkillingTargetObject() == null
            || step.getSkillingTargetObject().trim().isEmpty()) return null;
        String targetName = step.getSkillingTargetObject();
        if (targetName == null || targetName.trim().isEmpty()) return null;
        String target = targetName.trim().toLowerCase();

        // The boat's tile while sailing (a shipwreck being salvaged), otherwise the player's
        WorldPoint playerLoc = Sailing.position(client);
        if (playerLoc == null) return null;

        WorldView top = client.getTopLevelWorldView();
        if (top == null || top.getScene() == null) return null;
        // Out at sea everything is further apart: show targets as far as the client draws the scene
        int range = HIGHLIGHT_RANGE;
        boolean sailing = Sailing.isSailing(client);
        if (sailing) range = Math.max(range, top.getScene().getDrawDistance());
        highlightIn(graphics, top, playerLoc, range, target);
        // The boat is a little world of its own: its facilities (a salvaging station) are in its
        // scene, measured from where the player stands on deck.
        if (sailing) {
            WorldView deck = client.getLocalPlayer().getWorldView();
            if (deck != null && deck.getScene() != null) {
                highlightIn(graphics, deck, client.getLocalPlayer().getWorldLocation(),
                    HIGHLIGHT_RANGE, target);
            }
        }
        return null;
    }

    /** Highlights the step's target objects in {@code scene} within {@code range} tiles of {@code origin}. */
    private void highlightIn(Graphics2D graphics, WorldView wv, WorldPoint origin,
                             int range, String target) {
        Scene scene = wv.getScene();
        Tile[][][] tiles = scene == null ? null : scene.getTiles();
        int z = wv.getPlane();
        if (tiles == null || z < 0 || z >= tiles.length || tiles[z] == null) return;

        // Only the tiles within range of the origin, not the whole scene every frame
        Tile[][] plane = tiles[z];
        int ox = origin.getX() - wv.getBaseX(), oy = origin.getY() - wv.getBaseY();
        for (int sx = Math.max(0, ox - range); sx <= Math.min(plane.length - 1, ox + range); sx++) {
            Tile[] row = plane[sx];
            if (row == null) continue;
            for (int sy = Math.max(0, oy - range); sy <= Math.min(row.length - 1, oy + range); sy++) {
                Tile tile = row[sy];
                if (tile == null) continue;
                if (tile.getWorldLocation() == null
                    || origin.distanceTo(tile.getWorldLocation()) > range) continue;
                GameObject[] objects = tile.getGameObjects();
                if (objects != null) {
                    for (GameObject obj : objects) {
                        if (obj == null) continue;
                        // GameObjects span multiple tiles; only draw from the anchor tile
                        if (!obj.getSceneMinLocation().equals(tile.getSceneLocation())) continue;
                        if (matches(obj.getId(), target)) draw(graphics, obj.getConvexHull());
                    }
                }
                // Drains, levers on walls, wall decorations and floor objects are other kinds of object
                WallObject wall = tile.getWallObject();
                if (wall != null && matches(wall.getId(), target)) draw(graphics, wall.getConvexHull());
                DecorativeObject deco = tile.getDecorativeObject();
                if (deco != null && matches(deco.getId(), target)) draw(graphics, deco.getConvexHull());
                GroundObject ground = tile.getGroundObject();
                if (ground != null && matches(ground.getId(), target)) draw(graphics, ground.getConvexHull());
            }
        }
    }

    /** Whether the object is the step's target: "Baker's stall" by its start, a kind by its end
     *  ("shipwreck" lights any of the eight). */
    private boolean matches(int id, String target) {
        ObjectComposition comp = client.getObjectDefinition(id);
        if (comp != null && comp.getImpostorIds() != null) comp = comp.getImpostor();
        if (comp == null) return false;
        String lower = comp.getName() == null ? null : comp.getName().toLowerCase();
        if (lower == null || !(lower.startsWith(target) || lower.endsWith(" " + target))) return false;
        return !isSunkenShipwreck(lower, comp);
    }

    private void draw(Graphics2D graphics, Shape hull) {
        if (hull == null) return;
        Color base = plugin.getConfig().npcHighlightColor();
        int outA = base.getAlpha();
        int fillA = Math.max(0, Math.min(255, (int) (outA * 0.22)));
        graphics.setColor(ColorUtil.colorWithAlpha(base, fillA));
        graphics.fill(hull);
        graphics.setColor(ColorUtil.colorWithAlpha(base, outA));
        graphics.setStroke(new BasicStroke(2));
        graphics.draw(hull);
    }

    /**
     * A shipwreck that can't be salvaged right now. Like a tree turning into a stump, but a sunken
     * wreck keeps its name ("Large shipwreck"); it just loses its only option, Inspect.
     */
    private static boolean isSunkenShipwreck(String lowerName, ObjectComposition comp) {
        if (!lowerName.endsWith("shipwreck")) return false;
        String[] actions = comp.getActions();
        if (actions != null) for (String a : actions) if (a != null && !a.isEmpty()) return false;
        return true;
    }
}
