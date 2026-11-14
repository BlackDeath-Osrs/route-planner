package com.routeplanner.skilling;

import com.routeplanner.util.SceneObjects;
import com.routeplanner.*;
import com.routeplanner.model.RouteStep;
import com.routeplanner.sailing.Sailing;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.ColorUtil;
import javax.inject.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Highlights matching game objects (e.g. thieving stalls) for a SKILLING step
 * that defines a target object name.
 */
@Singleton
public class StallHighlightOverlay extends Overlay {

    /** How far from the player targets are highlighted on land, in tiles. */
    private static final int HIGHLIGHT_RANGE = 20;

    /** A target meaning "the object with this id" (a farming patch), not a name. */
    private static final String PATCH_PREFIX = "#patch:";

    private final Client client;
    private final RoutePlannerPlugin plugin;

    /** How well an object's name fits the target: the best fit in range is all that's lit. */
    private static final int NO_MATCH = 0, ENDS_WITH = 1, STARTS_WITH = 2, EXACT = 3;
    /** This frame's matches: their outlines and how well each fits. Reused between frames. */
    private final List<Shape> hulls = new ArrayList<>();
    private final List<Integer> fits = new ArrayList<>();

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
        if (step == null || !step.isHighlightEnabled()) return null;
        // A farming patch is lit by its id: its name changes with whatever grows in it
        String target = step.hasFarmPatch() ? PATCH_PREFIX + step.getFarmPatchId()
            : step.getSkillingTargetObject() == null ? null : step.getSkillingTargetObject().trim().toLowerCase();
        if (target == null || target.isEmpty()) return null;

        // The boat's tile while sailing (a shipwreck being salvaged), otherwise the player's
        WorldPoint playerLoc = Sailing.position(client);
        if (playerLoc == null) return null;

        WorldView top = client.getTopLevelWorldView();
        if (top == null || top.getScene() == null) return null;
        // Out at sea everything is further apart: show targets as far as the client draws the scene
        int range = HIGHLIGHT_RANGE;
        boolean sailing = Sailing.isSailing(client);
        if (sailing) range = Math.max(range, top.getScene().getDrawDistance());
        // On land, only objects near the step's own tile: the one by the step, not every one in
        // view. Steps that keep highlights to a drawn area go by the area instead. Instance tiles
        // don't match a step's saved tile, and at sea the wrecks are spread out, so neither is limited.
        WorldPoint near = sailing || top.isInstance() || step.getWorldPoint() == null
            || (step.isHighlightInAreaOnly() && plugin.areaOf(step) != null) ? null : step.getWorldPoint();
        highlightIn(graphics, top, playerLoc, range, target, step, near);
        // The boat is a little world of its own: its facilities (a salvaging station) are in its
        // scene, measured from where the player stands on deck.
        if (sailing) {
            WorldView deck = client.getLocalPlayer().getWorldView();
            if (deck != null && deck.getScene() != null) {
                highlightIn(graphics, deck, client.getLocalPlayer().getWorldLocation(),
                    HIGHLIGHT_RANGE, target, null, null);
            }
        }
        return null;
    }

    /**
     * Highlights the step's target objects in {@code scene} within {@code range} tiles of {@code origin};
     * with {@code areaStep}, only those inside that step's area when it keeps highlights to it; with
     * {@code near}, only those within the configured radius of that tile.
     */
    private void highlightIn(Graphics2D graphics, WorldView wv, WorldPoint origin,
                             int range, String target, RouteStep areaStep, WorldPoint near) {
        int radius = Math.max(1, plugin.getConfig().objectHighlightRadius());
        Scene scene = wv.getScene();
        Tile[][][] tiles = scene == null ? null : scene.getTiles();
        int z = wv.getPlane();
        if (tiles == null || z < 0 || z >= tiles.length || tiles[z] == null) return;

        // Only the tiles within range of the origin, not the whole scene every frame
        hulls.clear();
        fits.clear();
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
                        if (!SceneObjects.isObject(obj)) continue;
                        // GameObjects span multiple tiles; only draw from the anchor tile
                        if (!obj.getSceneMinLocation().equals(tile.getSceneLocation())) continue;
                        int fit = fit(obj.getId(), target);
                        if (fit == NO_MATCH) continue;
                        int w = obj.getSceneMaxLocation().getX() - obj.getSceneMinLocation().getX() + 1;
                        int h = obj.getSceneMaxLocation().getY() - obj.getSceneMinLocation().getY() + 1;
                        if ((areaStep == null || plugin.highlightAllowed(areaStep, tile.getWorldLocation(), w, h))
                            && within(near, radius, tile.getWorldLocation(), w, h)) {
                            collect(obj.getConvexHull(), fit);
                        }
                    }
                }
                // Drains, levers on walls, wall decorations and floor objects are other kinds of object
                if (areaStep != null && !plugin.highlightAllowed(areaStep, tile.getWorldLocation())) continue;
                if (!within(near, radius, tile.getWorldLocation(), 1, 1)) continue;
                WallObject wall = tile.getWallObject();
                if (wall != null) collect(wall.getConvexHull(), fit(wall.getId(), target));
                DecorativeObject deco = tile.getDecorativeObject();
                if (deco != null) collect(deco.getConvexHull(), fit(deco.getId(), target));
                GroundObject ground = tile.getGroundObject();
                if (ground != null) collect(ground.getConvexHull(), fit(ground.getId(), target));
            }
        }
        // Only the closest kind of match: with a "Wall" in range, an "Odd looking wall" isn't the target.
        int best = NO_MATCH;
        for (int f : fits) best = Math.max(best, f);
        for (int i = 0; i < hulls.size(); i++) {
            if (fits.get(i) == best) draw(graphics, hulls.get(i));
        }
    }

    /** Whether something covering w x h tiles from {@code sw} is within {@code radius} of {@code near} (always, without one). */
    private static boolean within(WorldPoint near, int radius, WorldPoint sw, int w, int h) {
        if (near == null) return true;
        if (sw.getPlane() != near.getPlane()) return false;
        int dx = Math.max(0, Math.max(sw.getX() - near.getX(), near.getX() - (sw.getX() + w - 1)));
        int dy = Math.max(0, Math.max(sw.getY() - near.getY(), near.getY() - (sw.getY() + h - 1)));
        return Math.max(dx, dy) <= radius;
    }

    private void collect(Shape hull, int fit) {
        if (fit == NO_MATCH || hull == null) return;
        hulls.add(hull);
        fits.add(fit);
    }

    /**
     * How well the object fits the step's target: its exact name, "Baker's stall" by its start,
     * or a kind by its end ("shipwreck" lights any of the eight).
     */
    private int fit(int id, String target) {
        if (target.startsWith(PATCH_PREFIX)) return target.equals(PATCH_PREFIX + id) ? EXACT : NO_MATCH;
        ObjectComposition comp = client.getObjectDefinition(id);
        if (comp != null && comp.getImpostorIds() != null) comp = comp.getImpostor();
        if (comp == null || comp.getName() == null) return NO_MATCH;
        String lower = comp.getName().toLowerCase();
        int fit = lower.equals(target) ? EXACT
            : lower.startsWith(target) ? STARTS_WITH
            : lower.endsWith(" " + target) && !isOtherTree(lower, target) ? ENDS_WITH
            : NO_MATCH;
        return fit == NO_MATCH || isSunkenShipwreck(lower, comp) ? NO_MATCH : fit;
    }

    /** A tree of another woodcutting tier: "tree" means regular trees, not "Oak tree" or "Willow tree". */
    private static boolean isOtherTree(String lowerName, String target) {
        for (Recipes.Recipe t : Recipes.WOODCUTTING) {
            String tier = t.target.toLowerCase();
            if (!tier.equals("tree") && lowerName.startsWith(tier + " ") && !target.startsWith(tier)) return true;
        }
        return false;
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
        HighlightIcon.drawAbove(graphics, plugin, hull);
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
