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
        if (step == null || step.getWorldPoint() == null || step.isLocationReached()) return null;

        // Highlight target tile
        if (step.getWorldPoint() != null) {
            highlightTile(graphics, step.getWorldPoint(), config.tileHighlightColor());
        }

        // If the pathfinder has a plane transition active, highlight the transition
        // object hull (if object ID is set) or the transition point tile (fallback).
        Transport transition = plugin.getPathfinderOverlay().getActiveTransition();
        RouteStep activeStep = plugin.getActiveRoute() == null ? null : plugin.getActiveRoute().getActiveStep();

        if (transition != null && transition.origin != null && activeStep != null) {
            WorldPoint player = client.getLocalPlayer() == null ? null
                : client.getLocalPlayer().getWorldLocation();
            int distToTransition = player == null ? -999 : player.distanceTo(transition.origin);
            if (player != null && distToTransition >= 0 && distToTransition <= 15) {
                long ms = System.currentTimeMillis();
                int alpha = 120 + (int)(80 * Math.sin(ms / 400.0));
                Color base = config.npcHighlightColor();
                Color pulse = new Color(base.getRed(), base.getGreen(), base.getBlue(),
                    Math.max(0, Math.min(255, alpha)));
                int objId = activeStep.getTransitionObjectId();
                boolean drewHull = false;
                if (objId > 0) {
                    // Highlight all tiles occupied by the transition object by scanning the scene
                    // and drawing filled polygons on matching tiles. Works with HD plugin unlike
                    // ModelOutlineRenderer which requires the standard renderer.
                    net.runelite.api.Tile[][][] tiles = client.getTopLevelWorldView().getScene().getTiles();
                    int plane = transition.origin.getPlane();
                    for (net.runelite.api.Tile[] row : tiles[plane]) {
                        for (net.runelite.api.Tile t : row) {
                            if (t == null) continue;
                            for (GameObject obj : t.getGameObjects()) {
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
                    highlightTile(graphics, transition.origin, pulse);
                }
            }
        }



        return null;
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
