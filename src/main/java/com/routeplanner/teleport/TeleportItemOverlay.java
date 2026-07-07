package com.routeplanner.teleport;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Highlights a teleport item in the inventory for the active step, when that step is an
 * item-teleport. Matches ANY charge state the player is holding (e.g. glory(3) or glory(6)).
 * Highlight only: the plugin never clicks or operates the item.
 */
@Singleton
public class TeleportItemOverlay extends Overlay {
    // Inventory widget group; children carry per-slot item ids and bounds.
    private static final int INVENTORY_GROUP = 149;

    private final Client client;
    private final RoutePlannerPlugin plugin;

    @Inject
    public TeleportItemOverlay(Client client, RoutePlannerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null) return null;

        String itemTele = step.getTeleportItem();
        if (itemTele == null || itemTele.trim().isEmpty()) return null;

        TeleportItem tele = TeleportItems.getByDisplayName(itemTele);
        if (tele == null) return null;

        Widget inv = client.getWidget(INVENTORY_GROUP, 0);
        if (inv == null || inv.isHidden()) return null;

        boolean drewAny = false;
        for (Widget item : inventoryItemWidgets(inv)) {
            if (item == null || item.isHidden()) continue;
            if (tele.matchesItemId(item.getItemId())) {
                Rectangle b = item.getBounds();
                if (b == null) continue;
                graphics.setColor(new Color(0, 255, 255, 220));
                graphics.setStroke(new BasicStroke(2));
                graphics.drawRect(b.x, b.y, b.width, b.height);
                graphics.setColor(new Color(0, 255, 255, 45));
                graphics.fillRect(b.x, b.y, b.width, b.height);
                drewAny = true;
            }
        }
        return null;
    }

    /** Inventory item slots live as dynamic children of the inventory widget. */
    private Widget[] inventoryItemWidgets(Widget inv) {
        Widget[] dyn = inv.getDynamicChildren();
        if (dyn != null && dyn.length > 0) return dyn;
        Widget[] child = inv.getChildren();
        return child == null ? new Widget[0] : child;
    }
}
