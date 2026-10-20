package com.routeplanner;

import lombok.extern.slf4j.Slf4j;

import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import com.routeplanner.model.RouteStep;
import com.routeplanner.transport.Transport;
import net.runelite.api.GameObject;
import java.awt.Color;
import javax.inject.Inject;
import java.awt.*;

@Slf4j
public class RouteOverlay extends Overlay {

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final RoutePlannerConfig config;
    private final ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    public RouteOverlay(Client client, RoutePlannerPlugin plugin, RoutePlannerConfig config, ModelOutlineRenderer modelOutlineRenderer) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;

        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null) return null;
        // A step that only has you board a boat has no tile of its own, just the gangplank.
        if (step.getWorldPoint() == null && !plugin.getPathfinderOverlay().isBoardingPending(step)) return null;
        if (step.isLocationReached() && plugin.getArrivalHoldTicksRemaining() <= 0) return null;

        // Highlight target tile
        if (step.getWorldPoint() != null) {
            highlightTile(graphics, step.getWorldPoint(), config.tileHighlightColor());
        }

        // If the pathfinder has a plane transition active, highlight the transition
        // object hull (if object ID is set) or the transition point tile (fallback).
        Transport transition = plugin.getPathfinderOverlay().getActiveTransition();
        RouteStep activeStep = plugin.getActiveRoute() == null ? null : plugin.getActiveRoute().getActiveStep();

        // Show transition highlight whenever the active step has a transitionPoint set,
        // regardless of whether the plane-transition logic is active.
        net.runelite.api.coords.WorldPoint transOrigin = activeStep != null ? activeStep.getTransitionPoint() : null;
        // A teleport has nothing in the world to point at (the HUD names it instead)
        if (transOrigin == null && transition != null && !transition.teleport) transOrigin = transition.origin;

        if (transOrigin != null && activeStep != null) {
            WorldPoint player = client.getLocalPlayer() == null ? null
                : com.routeplanner.sailing.Sailing.position(client);
            // Use raw Chebyshev distance ignoring plane so underground transitions still highlight.
            int distToTransition = player == null ? 999 :
                Math.max(Math.abs(player.getX() - transOrigin.getX()),
                         Math.abs(player.getY() - transOrigin.getY()));
            if (player != null && distToTransition <= 15) {
                long ms = System.currentTimeMillis();
                int alpha = 120 + (int)(80 * Math.sin(ms / 400.0));
                Color base = config.npcHighlightColor();
                Color pulse = new Color(base.getRed(), base.getGreen(), base.getBlue(),
                    Math.max(0, Math.min(255, alpha)));
                // Stairs set on the step win; otherwise the ones the route itself climbs.
                int objId = activeStep.getTransitionPoint() != null ? activeStep.getTransitionObjectId()
                    : transition != null ? transition.objectId : -1;
                boolean drewHull = false;
                // Gangplanks: when the object isn't known (a dock the route picked, or a step made in
                // the editor), light up whatever gangplank is beside the dock.
                if (objId <= 0 && transition != null && transition.actionText != null
                        && transition.actionText.startsWith(com.routeplanner.sailing.SailingDocks.BOARD)) {
                    drewHull = highlightNamedNear(graphics, transOrigin, "Gangplank", 6, pulse);
                }
                if (objId > 0) {
                    // Highlight all tiles occupied by the transition object by scanning the scene
                    // and drawing filled polygons on matching tiles. Works with HD plugin unlike
                    // ModelOutlineRenderer which requires the standard renderer.
                    net.runelite.api.Tile[][][] tiles = client.getTopLevelWorldView().getScene().getTiles();
                    int plane = client.getTopLevelWorldView().getPlane();
                    for (net.runelite.api.Tile[] row : tiles[plane]) {
                        for (net.runelite.api.Tile t : row) {
                            if (t == null) continue;
                            // Build a list of all renderable objects on this tile
                            java.util.List<net.runelite.api.TileObject> candidates = new java.util.ArrayList<>();
                            for (GameObject obj : t.getGameObjects()) { if (obj != null) candidates.add(obj); }
                            if (t.getWallObject() != null) candidates.add(t.getWallObject());
                            if (t.getDecorativeObject() != null) candidates.add(t.getDecorativeObject());
                            if (t.getGroundObject() != null) candidates.add(t.getGroundObject());
                            for (net.runelite.api.TileObject obj : candidates) {
                                if (obj == null || obj.getId() != objId) continue;
                                // Use the object's clickbox (convex hull of the 3D model)
                                // which gives the actual shape rather than a tile square.
                                java.awt.Shape clickbox = obj.getClickbox();
                                if (clickbox != null) {
                                    graphics.setColor(new Color(pulse.getRed(), pulse.getGreen(), pulse.getBlue(), pulse.getAlpha() / 3));
                                    graphics.fill(clickbox);
                                    graphics.setColor(pulse);
                                    graphics.setStroke(new java.awt.BasicStroke(2));
                                    graphics.draw(clickbox);
                                    drewHull = true;
                                } else {
                                    // Fallback to tile poly if no clickbox
                                    LocalPoint lp = obj.getLocalLocation();
                                    if (lp == null) continue;
                                    Polygon poly = Perspective.getCanvasTilePoly(client, lp);
                                    if (poly == null) continue;
                                    graphics.setColor(new Color(pulse.getRed(), pulse.getGreen(), pulse.getBlue(), pulse.getAlpha() / 3));
                                    graphics.fillPolygon(poly);
                                    graphics.setColor(pulse);
                                    graphics.setStroke(new java.awt.BasicStroke(2));
                                    graphics.drawPolygon(poly);
                                    drewHull = true;
                                }
                            }
                        }
                    }
                }
                if (!drewHull) {
                    highlightTile(graphics, transOrigin, pulse);
                }
            }
        }



        return null;
    }

    /** Highlights every object called {@code name} within {@code radius} tiles of {@code center}. */
    private boolean highlightNamedNear(Graphics2D graphics, WorldPoint center, String name, int radius, Color pulse) {
        net.runelite.api.WorldView wv = client.getTopLevelWorldView();
        LocalPoint c = LocalPoint.fromWorld(wv, center);
        if (c == null) return false;
        int cx = c.getSceneX(), cy = c.getSceneY();
        net.runelite.api.Tile[][] tiles = wv.getScene().getTiles()[wv.getPlane()];
        boolean drew = false;
        for (int x = Math.max(0, cx - radius); x <= Math.min(tiles.length - 1, cx + radius); x++) {
            for (int y = Math.max(0, cy - radius); y <= Math.min(tiles[x].length - 1, cy + radius); y++) {
                net.runelite.api.Tile t = tiles[x][y];
                if (t == null) continue;
                java.util.List<net.runelite.api.TileObject> objs = new java.util.ArrayList<>();
                for (GameObject obj : t.getGameObjects()) { if (obj != null) objs.add(obj); }
                if (t.getGroundObject() != null) objs.add(t.getGroundObject());
                if (t.getDecorativeObject() != null) objs.add(t.getDecorativeObject());
                for (net.runelite.api.TileObject obj : objs) {
                    net.runelite.api.ObjectComposition def = client.getObjectDefinition(obj.getId());
                    if (def != null && def.getImpostorIds() != null) def = def.getImpostor();
                    if (def == null || !name.equalsIgnoreCase(def.getName())) continue;
                    java.awt.Shape clickbox = obj.getClickbox();
                    if (clickbox == null) continue;
                    graphics.setColor(new Color(pulse.getRed(), pulse.getGreen(), pulse.getBlue(), pulse.getAlpha() / 3));
                    graphics.fill(clickbox);
                    graphics.setColor(pulse);
                    graphics.setStroke(new java.awt.BasicStroke(2));
                    graphics.draw(clickbox);
                    drew = true;
                }
            }
        }
        return drew;
    }

    private void highlightTile(Graphics2D graphics, WorldPoint worldPoint, Color color) {
        LocalPoint lp = LocalPoint.fromWorld(client, worldPoint);
        if (lp == null) return;

        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly == null) return;

        // Fill
        graphics.setColor(color);
        graphics.fillPolygon(poly);

        // Border
        graphics.setColor(color.darker());
        graphics.setStroke(new BasicStroke(2));
        graphics.drawPolygon(poly);
    }
}
