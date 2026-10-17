package com.routeplanner.teleport;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import com.routeplanner.util.HighlightColors;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;

/**
 * For a Portal Nexus teleport step: highlights the nexus in the player's house, then the
 * step's destination row once the nexus teleport menu is open.
 * Highlight only: the plugin never clicks the nexus or picks the destination.
 */
@Singleton
public class NexusTeleportOverlay extends Overlay {

    private static final String NEXUS_NAME = "portal nexus";

    private final Client client;
    private final RoutePlannerPlugin plugin;

    @Inject
    public NexusTeleportOverlay(Client client, RoutePlannerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null || !"NEXUS".equals(step.getTeleportMethod())) return null;
        String dest = step.getTeleportNexus();
        if (dest == null || dest.trim().isEmpty()) return null;

        Widget menu = client.getWidget(InterfaceID.TelenexusTeleport.UNIVERSE);
        if (menu != null && !menu.isHidden()) {
            highlightRow(graphics, dest.trim());
        } else {
            highlightNexus(graphics);
        }
        return null;
    }

    /** Outline the whole line (text plus its clickable background) that names the destination. */
    private void highlightRow(Graphics2D graphics, String dest) {
        Rectangle[] line = {null};
        PortalNexus.forEachText(client, w -> {
            if (dest.equalsIgnoreCase(PortalNexus.cleanText(w.getText()))) {
                line[0] = line[0] == null ? w.getBounds() : line[0].union(w.getBounds());
            }
        });
        if (line[0] == null) return;
        // Stretch to the menu list's width so it reads as a row highlight, not just the words.
        Widget rows = client.getWidget(InterfaceID.TelenexusTeleport.ROWS1);
        Rectangle b = line[0];
        if (rows != null && !rows.isHidden()) {
            Rectangle r = rows.getBounds();
            b = new Rectangle(r.x, b.y, r.width, b.height);
        }
        graphics.setColor(HighlightColors.fill(plugin.getConfig().npcHighlightColor()));
        graphics.fill(b);
        graphics.setColor(plugin.getConfig().npcHighlightColor());
        graphics.setStroke(new BasicStroke(2));
        graphics.draw(b);
    }

    private void highlightNexus(Graphics2D graphics) {
        Scene scene = client.getScene();
        if (scene == null) return;
        Tile[][][] tiles = scene.getTiles();
        int z = client.getPlane();
        if (z < 0 || z >= tiles.length) return;
        for (Tile[] row : tiles[z]) {
            if (row == null) continue;
            for (Tile tile : row) {
                if (tile == null) continue;
                GameObject[] objects = tile.getGameObjects();
                if (objects == null) continue;
                for (GameObject obj : objects) {
                    if (obj == null) continue;
                    // GameObjects span multiple tiles; only draw from the anchor tile
                    if (!obj.getSceneMinLocation().equals(tile.getSceneLocation())) continue;
                    ObjectComposition comp = client.getObjectDefinition(obj.getId());
                    if (comp == null) continue;
                    // Built furniture often uses an impostor (varbit-driven) definition.
                    if (comp.getImpostorIds() != null) {
                        ObjectComposition imp = comp.getImpostor();
                        if (imp != null) comp = imp;
                    }
                    String name = comp.getName();
                    if (name == null || !name.toLowerCase().startsWith(NEXUS_NAME)) continue;
                    Shape hull = obj.getConvexHull();
                    if (hull == null) continue;
                    graphics.setColor(HighlightColors.fill(plugin.getConfig().npcHighlightColor()));
                    graphics.fill(hull);
                    graphics.setColor(plugin.getConfig().npcHighlightColor());
                    graphics.setStroke(new BasicStroke(2));
                    graphics.draw(hull);
                }
            }
        }
    }
}
