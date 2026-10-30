package com.routeplanner.teleport;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import com.routeplanner.util.HighlightColors;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * For a Portal Nexus teleport step: highlights the nexus in the player's house, then the
 * step's destination row once the nexus teleport menu is open. For a house portal step:
 * highlights that portal ("Varrock Portal") in the portal chamber.
 * Highlight only: the plugin never clicks the nexus or picks the destination.
 */
@Singleton
public class NexusTeleportOverlay extends Overlay {

    private final Client client;
    private final RoutePlannerPlugin plugin;
    // The scene search for the nexus or portal, redone once a game tick rather than every frame
    private List<GameObject> found = Collections.emptyList();
    private RouteStep foundFor;
    private int foundTick = -1;

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
        Predicate<String> object = PortalNexus.houseObjectName(step);
        if (object == null) return null;

        Widget menu = client.getWidget(InterfaceID.TelenexusTeleport.UNIVERSE);
        String dest = step.getTeleportNexus();
        if ("NEXUS".equals(step.getTeleportMethod()) && dest != null && !dest.trim().isEmpty()
                && menu != null && !menu.isHidden()) {
            highlightRow(graphics, dest.trim());
        } else {
            highlightObjects(graphics, step, object);
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

    private void highlightObjects(Graphics2D graphics, RouteStep step, Predicate<String> nameMatches) {
        if (step != foundFor || client.getTickCount() != foundTick) {
            found = PortalNexus.findObjects(client, nameMatches);
            foundFor = step;
            foundTick = client.getTickCount();
        }
        for (GameObject obj : found) {
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
