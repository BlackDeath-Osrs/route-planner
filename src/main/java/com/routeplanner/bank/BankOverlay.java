package com.routeplanner.bank;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.List;

@Slf4j
@Singleton
public class BankOverlay extends Overlay {

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final BankItemManager bankItemManager;
    private final ItemManager itemManager;

    @Inject
    public BankOverlay(Client client, RoutePlannerPlugin plugin,
                       BankItemManager bankItemManager, ItemManager itemManager) {
        this.client = client;
        this.plugin = plugin;
        this.bankItemManager = bankItemManager;
        this.itemManager = itemManager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null) return null;

        boolean isItem = step.hasItems();
        boolean isHerblore = step.isHighlightEnabled() && step.getHerblorePotion() != null;
        boolean isBankSkill = step.isHighlightEnabled() && step.getBankHighlightItems() != null
            && !step.getBankHighlightItems().trim().isEmpty();
        if (!isItem && !isHerblore && !isBankSkill) return null;

        // Check if the bank is open -- or the Farming Guild seed vault, which lists its items
        // the same way, so the same highlighting works there for tree/farm runs. The seed vault
        // uses the generated gameval id: ComponentID.SEED_VAULT_ITEM_CONTAINER points at an
        // empty sibling (631:17) since the interface was reworked; the items are in 631:18.
        Widget container = visibleWidget(ComponentID.BANK_ITEM_CONTAINER);
        if (container == null) container = visibleWidget(InterfaceID.SeedVault.OBJ_LIST);
        if (container == null) return null;
        Widget[] items = container.getDynamicChildren();
        if (items == null) return null;

        String itemListStr;
        if (isItem) {
            itemListStr = step.getItemList();
        } else if (isHerblore) {
            com.routeplanner.skilling.PotionRecipes.Potion pot =
                com.routeplanner.skilling.PotionRecipes.getByName(step.getHerblorePotion());
            if (pot == null) return null;
            if (inventoryHasItem(pot.unfinished)) {
                itemListStr = pot.secondary;
            } else if (bankHasItem(items, pot.unfinished)) {
                itemListStr = pot.unfinished + ", " + pot.secondary;
            } else {
                itemListStr = pot.herbGrimy + "/" + pot.herbClean + ", Vial of water";
            }
        } else {
            itemListStr = step.getBankHighlightItems();
        }

        List<List<String>> groups = bankItemManager.parseItemList(itemListStr);
        if (groups.isEmpty()) return null;

        // Items scrolled out of view still have canvas positions; keep highlights inside the list.
        Shape savedClip = graphics.getClip();
        graphics.clip(visibleBounds(container));
        for (Widget item : items) {
            if (item == null || item.isHidden() || item.getItemId() <= 0) continue;
            String itemName = itemManager.getItemComposition(item.getItemId()).getName();
            if (itemName == null) continue;

            boolean isRequired = false;
            boolean isMissing = false;

            for (List<String> group : groups) {
                for (String alt : group) {
                    String altName = bankItemManager.parseItemName(alt);
                    if (BankItemManager.nameMatches(altName, itemName)) {
                        isRequired = true;
                        // Check if INVENTORY has enough, not bank
                        long required = bankItemManager.parseQuantityValue(alt);
                        net.runelite.api.ItemContainer inv =
                            client.getItemContainer(net.runelite.api.InventoryID.INVENTORY);
                        long inInventory = 0;
                        if (inv != null) {
                            for (net.runelite.api.Item invItem : inv.getItems()) {
                                if (invItem.getId() <= 0) continue;
                                String invName = itemManager.getItemComposition(invItem.getId()).getName();
                                if (BankItemManager.nameMatches(altName, invName)) {
                                    inInventory += invItem.getQuantity();
                                }
                            }
                        }
                        isMissing = inInventory < required;
                        break;
                    }
                }
                if (isRequired) break;
            }

            if (isRequired) {
                Rectangle bounds = new Rectangle(
                    item.getCanvasLocation().getX(),
                    item.getCanvasLocation().getY(),
                    item.getWidth(),
                    item.getHeight()
                );
                // Green if already have it, orange if still need it
                Color highlight = isMissing
                    ? new Color(255, 140, 0, 120)
                    : new Color(0, 255, 0, 80);
                Color border = isMissing
                    ? new Color(255, 140, 0, 220)
                    : new Color(0, 255, 0, 200);

                graphics.setColor(highlight);
                graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                graphics.setColor(border);
                graphics.setStroke(new BasicStroke(2));
                graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        }
        graphics.setClip(savedClip);

        return null;
    }

    /**
     * The on-screen part of a widget. A scrolled list (like the seed vault's) can be far taller
     * than its window and start above the screen, so intersect with every ancestor's bounds.
     */
    private static Rectangle visibleBounds(Widget w) {
        Rectangle r = w.getBounds();
        for (Widget p = w.getParent(); p != null; p = p.getParent()) {
            r = r.intersection(p.getBounds());
        }
        return r;
    }

    private Widget visibleWidget(int componentId) {
        Widget w = client.getWidget(componentId);
        return w == null || w.isHidden() ? null : w;
    }

    private boolean bankHasItem(Widget[] items, String name) {
        if (name == null) return false;
        for (Widget item : items) {
            if (item == null || item.isHidden() || item.getItemId() <= 0) continue;
            String n = itemManager.getItemComposition(item.getItemId()).getName();
            if (n != null && n.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private boolean inventoryHasItem(String name) {
        if (name == null) return false;
        net.runelite.api.ItemContainer inv =
            client.getItemContainer(net.runelite.api.InventoryID.INVENTORY);
        if (inv == null) return false;
        for (net.runelite.api.Item invItem : inv.getItems()) {
            if (invItem.getId() <= 0) continue;
            String n = itemManager.getItemComposition(invItem.getId()).getName();
            if (n != null && n.equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
