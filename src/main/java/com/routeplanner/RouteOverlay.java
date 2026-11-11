package com.routeplanner;

import com.routeplanner.util.SceneObjects;
import com.routeplanner.teleport.SpiritTrees;
import com.routeplanner.teleport.TeleportItems;
import com.routeplanner.teleport.FairyRings;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import com.routeplanner.model.TileArea;
import com.routeplanner.sailing.Sailing;
import com.routeplanner.sailing.SailingDocks;
import com.routeplanner.transport.Transport;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

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
        // While an area is being painted only the brush's tiles show, so the step's own tile or
        // area can't be mistaken for painted ones.
        if (plugin.getAreaDrawer().isActive()) return null;
        // An area stays up until the whole step is done (the step stops being active)
        TileArea area = plugin.areaOf(step);
        if (area != null) {
            AreaGraphics.drawGround(graphics, client, area, config.tileHighlightColor());
        }
        if (step.isLocationReached() && plugin.getArrivalHoldTicksRemaining() <= 0) return null;

        // Highlight the step's tile
        if (area == null && step.getWorldPoint() != null) {
            highlightTile(graphics, step.getWorldPoint(), config.tileHighlightColor());
        }

        // If the pathfinder has a plane transition active, highlight the transition
        // object hull (if object ID is set) or the transition point tile (fallback).
        Transport transition = plugin.getPathfinderOverlay().getActiveTransition();
        RouteStep activeStep = plugin.getActiveRoute() == null ? null : plugin.getActiveRoute().getActiveStep();

        // Show transition highlight whenever the active step has a transitionPoint set,
        // regardless of whether the plane-transition logic is active.
        WorldPoint transOrigin = activeStep != null ? activeStep.getTransitionPoint() : null;
        // A teleport has nothing in the world to point at (the HUD names it instead)
        if (transOrigin == null && transition != null && !transition.teleport) transOrigin = transition.origin;

        if (transOrigin != null && activeStep != null) {
            WorldPoint player = client.getLocalPlayer() == null ? null
                : Sailing.position(client);
            // Use raw Chebyshev distance ignoring plane so underground transitions still highlight.
            int distToTransition = player == null ? 999 :
                Math.max(Math.abs(player.getX() - transOrigin.getX()),
                         Math.abs(player.getY() - transOrigin.getY()));
            if (player != null && distToTransition <= 15) {
                long ms = System.currentTimeMillis();
                int alpha = 120 + (int)(80 * Math.sin(ms / 400.0));
                Color base = config.npcHighlightColor();
                Color pulse = ColorUtil.colorWithAlpha(base, Math.max(0, Math.min(255, alpha)));
                // Stairs set on the step win; otherwise the ones the route itself climbs.
                int objId = activeStep.getTransitionPoint() != null ? activeStep.getTransitionObjectId()
                    : transition != null ? transition.objectId : -1;
                boolean drewHull = false;
                // Gangplanks: when the object isn't known (a dock the route picked, or a step made in
                // the editor), light up whatever gangplank is beside the dock.
                if (objId <= 0 && transition != null && transition.actionText != null
                        && transition.actionText.startsWith(SailingDocks.BOARD)) {
                    drewHull = highlightNamedNear(graphics, transOrigin, "Gangplank", 6, pulse);
                }
                // Spirit trees: the data's object ids don't all match the game's (the Grand
                // Exchange tree is 1295), so they're found by name
                if (!drewHull && transition != null && transition.actionText != null
                        && transition.actionText.startsWith(SpiritTrees.TRANSITION_PREFIX)) {
                    drewHull = highlightNamedNear(graphics, transOrigin, "Spirit tree", 6, pulse);
                    objId = -1;
                }
                if (objId > 0) {
                    // Highlight the transition object by drawing its clickbox. Works with HD plugin
                    // unlike ModelOutlineRenderer which requires the standard renderer. Only the
                    // tiles around the climb are searched: this runs every frame.
                    WorldView wv = client.getTopLevelWorldView();
                    Tile[][] tiles = wv.getScene().getTiles()[wv.getPlane()];
                    int cx = transOrigin.getX() - wv.getBaseX(), cy = transOrigin.getY() - wv.getBaseY();
                    for (int x = Math.max(0, cx - OBJECT_RADIUS); x <= Math.min(tiles.length - 1, cx + OBJECT_RADIUS); x++) {
                        for (int y = Math.max(0, cy - OBJECT_RADIUS); y <= Math.min(tiles[x].length - 1, cy + OBJECT_RADIUS); y++) {
                            Tile t = tiles[x][y];
                            if (t == null) continue;
                            // Build a list of all renderable objects on this tile
                            List<TileObject> candidates = new ArrayList<>();
                            for (GameObject obj : t.getGameObjects()) { if (SceneObjects.isObject(obj)) candidates.add(obj); }
                            if (t.getWallObject() != null) candidates.add(t.getWallObject());
                            if (t.getDecorativeObject() != null) candidates.add(t.getDecorativeObject());
                            if (t.getGroundObject() != null) candidates.add(t.getGroundObject());
                            for (TileObject obj : candidates) {
                                if (obj == null || obj.getId() != objId) continue;
                                // Use the object's clickbox (convex hull of the 3D model)
                                // which gives the actual shape rather than a tile square.
                                Shape clickbox = obj.getClickbox();
                                if (clickbox != null) {
                                    graphics.setColor(ColorUtil.colorWithAlpha(pulse, pulse.getAlpha() / 3));
                                    graphics.fill(clickbox);
                                    graphics.setColor(pulse);
                                    graphics.setStroke(new java.awt.BasicStroke(2));
                                    graphics.draw(clickbox);
                                    HighlightIcon.drawAbove(graphics, plugin, clickbox);
                                    drewHull = true;
                                } else {
                                    // Fallback to tile poly if no clickbox
                                    LocalPoint lp = obj.getLocalLocation();
                                    if (lp == null) continue;
                                    Polygon poly = Perspective.getCanvasTilePoly(client, lp);
                                    if (poly == null) continue;
                                    graphics.setColor(ColorUtil.colorWithAlpha(pulse, pulse.getAlpha() / 3));
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

        highlightTeleportNetwork(graphics, activeStep);
        return null;
    }

    /** How near the player a spirit tree or fairy ring has to be to light up. */
    private static final int NETWORK_HIGHLIGHT_RANGE = 15;

    /**
     * On a spirit tree or fairy ring step not yet taken: lights the nearest public tree or ring,
     * found by name beside the tile the path leads to. The house's ones have their own overlay.
     */
    private void highlightTeleportNetwork(Graphics2D graphics, RouteStep step) {
        WorldView wv = client.getTopLevelWorldView();
        if (step == null || wv == null || wv.isInstance()) return;
        boolean tree = SpiritTrees.isSpiritTreeStep(step);
        if (!tree && !FairyRings.isFairyRingStep(step)) return;
        WorldPoint player = Sailing.position(client);
        if (player == null) return;
        WorldPoint dest = tree ? SpiritTrees.destination(step.getTeleportNexus()) : FairyRings.destination(step.getTeleportNexus());
        if (dest != null && !TeleportItems.isHouse(dest) && dest.getPlane() == player.getPlane()
                && dest.distanceTo2D(player) <= 25) return; // already there

        WorldPoint at;
        String name;
        if (tree) {
            SpiritTrees.Tree t = SpiritTrees.nearest(player);
            at = t == null ? null : t.tileNearest(player);
            name = "Spirit tree";
        } else {
            FairyRings.Ring r = FairyRings.nearest(player);
            at = r == null ? null : r.tile;
            name = "Fairy ring";
        }
        if (at == null || at.getPlane() != player.getPlane() || at.distanceTo2D(player) > NETWORK_HIGHLIGHT_RANGE) return;
        long ms = System.currentTimeMillis();
        int alpha = 120 + (int) (80 * Math.sin(ms / 400.0));
        Color pulse = ColorUtil.colorWithAlpha(config.npcHighlightColor(), Math.max(0, Math.min(255, alpha)));
        highlightNamedNear(graphics, at, name, tree ? 6 : 2, pulse);
    }

    /** How far from the climb tile its stairs or ladder can stand (big staircases span 3x3). */
    private static final int OBJECT_RADIUS = 4;

    /** Highlights every object called {@code name} within {@code radius} tiles of {@code center}. */
    private boolean highlightNamedNear(Graphics2D graphics, WorldPoint center, String name, int radius, Color pulse) {
        WorldView wv = client.getTopLevelWorldView();
        LocalPoint c = LocalPoint.fromWorld(wv, center);
        if (c == null) return false;
        int cx = c.getSceneX(), cy = c.getSceneY();
        Tile[][] tiles = wv.getScene().getTiles()[wv.getPlane()];
        boolean drew = false;
        for (int x = Math.max(0, cx - radius); x <= Math.min(tiles.length - 1, cx + radius); x++) {
            for (int y = Math.max(0, cy - radius); y <= Math.min(tiles[x].length - 1, cy + radius); y++) {
                Tile t = tiles[x][y];
                if (t == null) continue;
                List<TileObject> objs = new ArrayList<>();
                for (GameObject obj : t.getGameObjects()) { if (SceneObjects.isObject(obj)) objs.add(obj); }
                if (t.getGroundObject() != null) objs.add(t.getGroundObject());
                if (t.getDecorativeObject() != null) objs.add(t.getDecorativeObject());
                for (TileObject obj : objs) {
                    ObjectComposition def = client.getObjectDefinition(obj.getId());
                    if (def != null && def.getImpostorIds() != null) def = def.getImpostor();
                    if (def == null || !name.equalsIgnoreCase(def.getName())) continue;
                    Shape clickbox = obj.getClickbox();
                    if (clickbox == null) continue;
                    graphics.setColor(ColorUtil.colorWithAlpha(pulse, pulse.getAlpha() / 3));
                    graphics.fill(clickbox);
                    graphics.setColor(pulse);
                    graphics.setStroke(new java.awt.BasicStroke(2));
                    graphics.draw(clickbox);
                    HighlightIcon.drawAbove(graphics, plugin, clickbox);
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
