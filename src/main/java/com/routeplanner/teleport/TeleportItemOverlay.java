package com.routeplanner.teleport;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import com.routeplanner.util.HighlightColors;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
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
 * Highlights a teleport item in the inventory, or in the equipment tab when it's worn (e.g. a
 * construction cape), for the active step, when that step is an item-teleport. Matches ANY
 * charge state the player is holding (e.g. glory(3) or glory(6)).
 * Highlight only: the plugin never clicks or operates the item.
 */
@Singleton
public class TeleportItemOverlay extends Overlay {
    // Inventory widget group; children carry per-slot item ids and bounds.
    private static final int INVENTORY_GROUP = 149;

    /** Equipment tab slot widgets, indexed by EquipmentInventorySlot index (gaps are unused). */
    private static final int[] WORN_SLOT_WIDGETS = new int[14];
    static {
        WORN_SLOT_WIDGETS[0] = InterfaceID.Wornitems.SLOT0;    // head
        WORN_SLOT_WIDGETS[1] = InterfaceID.Wornitems.SLOT1;    // cape
        WORN_SLOT_WIDGETS[2] = InterfaceID.Wornitems.SLOT2;    // amulet
        WORN_SLOT_WIDGETS[3] = InterfaceID.Wornitems.SLOT3;    // weapon
        WORN_SLOT_WIDGETS[4] = InterfaceID.Wornitems.SLOT4;    // body
        WORN_SLOT_WIDGETS[5] = InterfaceID.Wornitems.SLOT5;    // shield
        WORN_SLOT_WIDGETS[7] = InterfaceID.Wornitems.SLOT7;    // legs
        WORN_SLOT_WIDGETS[9] = InterfaceID.Wornitems.SLOT9;    // gloves
        WORN_SLOT_WIDGETS[10] = InterfaceID.Wornitems.SLOT10;  // boots
        WORN_SLOT_WIDGETS[12] = InterfaceID.Wornitems.SLOT12;  // ring
        WORN_SLOT_WIDGETS[13] = InterfaceID.Wornitems.SLOT13;  // ammo
    }

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
        if (inv != null && !inv.isHidden()) {
            for (Widget item : inventoryItemWidgets(inv)) {
                if (item == null || item.isHidden()) continue;
                if (tele.matchesItemId(item.getItemId())) highlight(graphics, item.getBounds());
            }
        }

        // Worn items: the equipment tab's slot widgets don't carry item ids, so match the
        // worn-items container and highlight that slot's widget (only drawn while the tab is open).
        ItemContainer worn = client.getItemContainer(InventoryID.WORN);
        if (worn != null) {
            Item[] items = worn.getItems();
            for (int slot = 0; slot < items.length && slot < WORN_SLOT_WIDGETS.length; slot++) {
                if (WORN_SLOT_WIDGETS[slot] == 0 || !tele.matchesItemId(items[slot].getId())) continue;
                Widget w = client.getWidget(WORN_SLOT_WIDGETS[slot]);
                if (w != null && !w.isHidden()) highlight(graphics, w.getBounds());
            }
        }
        return null;
    }

    private void highlight(Graphics2D graphics, Rectangle b) {
        if (b == null) return;
        Color outline = plugin.getConfig().npcHighlightColor();
        graphics.setColor(outline);
        graphics.setStroke(new BasicStroke(2));
        graphics.drawRect(b.x, b.y, b.width, b.height);
        graphics.setColor(HighlightColors.fill(outline));
        graphics.fillRect(b.x, b.y, b.width, b.height);
    }

    /** Inventory item slots live as dynamic children of the inventory widget. */
    private Widget[] inventoryItemWidgets(Widget inv) {
        Widget[] dyn = inv.getDynamicChildren();
        if (dyn != null && dyn.length > 0) return dyn;
        Widget[] child = inv.getChildren();
        return child == null ? new Widget[0] : child;
    }
}
