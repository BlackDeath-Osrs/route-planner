package com.routeplanner.bank;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import com.routeplanner.skilling.PotionRecipes;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class BankOverlay extends Overlay {

    private static final Color MISSING_FILL = new Color(255, 140, 0, 120);
    private static final Color MISSING_BORDER = new Color(255, 140, 0, 220);
    private static final Color HAVE_FILL = new Color(0, 255, 0, 80);
    private static final Color HAVE_BORDER = new Color(0, 255, 0, 200);
    private static final Stroke BORDER_STROKE = new BasicStroke(2);

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
            PotionRecipes.Potion pot =
                PotionRecipes.getByName(step.getHerblorePotion());
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

        // Parse the list and count the inventory once per frame, not once per bank item.
        List<List<String>> groups = bankItemManager.parseItemList(itemListStr);
        if (groups.isEmpty()) return null;
        List<String[]> wanted = new ArrayList<>(); // {name, alt as written} in list order
        for (List<String> group : groups) {
            for (String alt : group) wanted.add(new String[]{bankItemManager.parseItemName(alt), alt});
        }
        Map<String, Long> carried = inventoryCounts();

        // Items scrolled out of view still have canvas positions; keep highlights inside the list.
        Shape savedClip = graphics.getClip();
        graphics.clip(visibleBounds(container));
        graphics.setStroke(BORDER_STROKE);
        for (Widget item : items) {
            if (item == null || item.isHidden() || item.getItemId() <= 0) continue;
            String itemName = itemManager.getItemComposition(item.getItemId()).getName();
            if (itemName == null) continue;

            String[] match = null;
            for (String[] w : wanted) {
                if (BankItemManager.nameMatches(w[0], itemName)) { match = w; break; }
            }
            if (match == null) continue;

            // Orange while the INVENTORY (not the bank) is still short of it, green once it's carried.
            boolean missing = BankItemManager.countOf(carried, match[0]) < bankItemManager.parseQuantityValue(match[1]);
            Rectangle bounds = new Rectangle(item.getCanvasLocation().getX(), item.getCanvasLocation().getY(),
                item.getWidth(), item.getHeight());
            graphics.setColor(missing ? MISSING_FILL : HAVE_FILL);
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            graphics.setColor(missing ? MISSING_BORDER : HAVE_BORDER);
            graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
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

    /** Lower-case item name -> how many are in the inventory. */
    private Map<String, Long> inventoryCounts() {
        Map<String, Long> counts = new HashMap<>();
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return counts;
        for (Item invItem : inv.getItems()) {
            if (invItem.getId() <= 0) continue;
            String n = itemManager.getItemComposition(invItem.getId()).getName();
            if (n != null) counts.merge(n.toLowerCase(), (long) invItem.getQuantity(), Long::sum);
        }
        return counts;
    }

    private boolean inventoryHasItem(String name) {
        if (name == null) return false;
        ItemContainer inv =
            client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return false;
        for (Item invItem : inv.getItems()) {
            if (invItem.getId() <= 0) continue;
            String n = itemManager.getItemComposition(invItem.getId()).getName();
            if (n != null && n.equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
