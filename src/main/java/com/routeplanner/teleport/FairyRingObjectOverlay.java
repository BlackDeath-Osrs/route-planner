package com.routeplanner.teleport;

import com.routeplanner.*;
import com.routeplanner.model.RouteStep;
import com.routeplanner.util.*;
import net.runelite.api.*;
import net.runelite.client.ui.overlay.*;
import javax.inject.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * On a fairy ring or spirit tree step inside an instance (a player-owned house), highlights
 * every ring or tree there, in any of its forms: the plain ring, the spirit tree, and the
 * spiritual fairy tree that is both. Outside instances the path already leads to, and lights,
 * the nearest one in the world.
 */
@Singleton
public class FairyRingObjectOverlay extends Overlay {

    private final Client client;
    private final RoutePlannerPlugin plugin;

    /** The rings found in the scene, looked for again once a tick. */
    private final List<GameObject> rings = new ArrayList<>();
    private int scannedTick = -1;
    private boolean scannedForRing;

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
        if (step == null || !step.isHighlightEnabled() || wv == null || !wv.isInstance()) return null;
        boolean ring = FairyRings.isFairyRingStep(step);
        if (!ring && !SpiritTrees.isSpiritTreeStep(step)) return null;

        if (client.getTickCount() != scannedTick || ring != scannedForRing) {
            scannedTick = client.getTickCount();
            scannedForRing = ring;
            scan(wv, ring);
        }
        for (GameObject obj : rings) draw(graphics, obj.getConvexHull());
        return null;
    }

    private void scan(WorldView wv, boolean ring) {
        rings.clear();
        if (wv.getScene() == null) return;
        for (Tile[] column : wv.getScene().getTiles()[wv.getPlane()]) {
            for (Tile tile : column) {
                if (tile == null || tile.getGameObjects() == null) continue;
                for (GameObject obj : tile.getGameObjects()) {
                    if (!SceneObjects.isObject(obj)) continue;
                    // A big object is listed on each of its tiles: keep it once, from its corner
                    if (!tile.getSceneLocation().equals(obj.getSceneMinLocation())) continue;
                    String name = name(obj.getId());
                    if (ring ? FairyRings.isRingName(name) : SpiritTrees.isTreeName(name)) rings.add(obj);
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
        HighlightColors.paint(graphics, hull, plugin.getConfig().npcHighlightColor());
        HighlightIcon.drawAbove(graphics, plugin, hull);
    }
}
