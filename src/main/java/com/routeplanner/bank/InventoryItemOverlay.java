package com.routeplanner.bank;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Outlines the active step's inventory items (its "Inventory items" highlight), for steps where
 * you use one item on another, e.g. a cocktail shaker for a Fruit Blast. Gated by the step's
 * Highlight toggle like its NPC and object. Highlight only: never clicks.
 */
@Singleton
public class InventoryItemOverlay extends Overlay {
    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final ItemManager itemManager;
    private final BankItemManager bankItemManager;

    @Inject
    public InventoryItemOverlay(Client client, RoutePlannerPlugin plugin, ItemManager itemManager,
                                BankItemManager bankItemManager) {
        this.client = client;
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.bankItemManager = bankItemManager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null || !step.isHighlightEnabled()) return null;
        String list = step.getInventoryHighlightItems();
        if (list == null || list.trim().isEmpty()) return null;

        List<String> wanted = new ArrayList<>();
        for (String entry : list.split("[,/]")) {
            if (!entry.trim().isEmpty()) wanted.add(bankItemManager.parseItemName(entry));
        }

        Widget inv = client.getWidget(InterfaceID.Inventory.ITEMS);
        if (inv == null || inv.isHidden()) return null;
        Widget[] slots = inv.getDynamicChildren();
        if (slots == null) return null;
        for (Widget slot : slots) {
            if (slot == null || slot.isHidden() || slot.getItemId() <= 0) continue;
            String name = itemManager.getItemComposition(slot.getItemId()).getName();
            for (String want : wanted) {
                if (BankItemManager.nameMatches(want, name)) {
                    highlight(graphics, slot.getBounds());
                    break;
                }
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
        graphics.setColor(com.routeplanner.util.HighlightColors.fill(outline));
        graphics.fillRect(b.x, b.y, b.width, b.height);
    }
}
