package com.routeplanner.bank;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.List;

@Slf4j
@Singleton
public class ShopOverlay extends Overlay {

    // Shop stock container: group 300, child 16
    private static final int SHOP_GROUP = 300;
    private static final int SHOP_STOCK_CHILD = 16;

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final BankItemManager bankItemManager;
    private final ItemManager itemManager;

    @Inject
    public ShopOverlay(Client client, RoutePlannerPlugin plugin,
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
        if (step == null || step.getType() != StepType.ITEM) return null;
        String mode = step.getItemMode();
        boolean isSell = "SELL".equals(mode);
        boolean isShop = "SHOP".equals(mode);
        if (!isShop && !isSell) return null;
        if (step.getItemList() == null || step.getItemList().trim().isEmpty()) return null;

        // SHOP highlights the shop stock (300,16); SELL highlights the inventory panel (301,0)
        Widget container = isSell
            ? client.getWidget(301, 0)
            : client.getWidget(SHOP_GROUP, SHOP_STOCK_CHILD);
        if (container == null || container.isHidden()) return null;

        List<List<String>> groups = bankItemManager.parseItemList(step.getItemList());
        if (groups.isEmpty()) return null;

        Widget[] items = container.getDynamicChildren();
        if (items == null) return null;

        for (Widget item : items) {
            if (item == null || item.isHidden() || item.getItemId() <= 0) continue;
            String itemName = itemManager.getItemComposition(item.getItemId()).getName();
            if (itemName == null) continue;

            boolean isRequired = false;
            boolean isMissing = false;

            for (List<String> group : groups) {
                for (String alt : group) {
                    String altName = bankItemManager.parseItemName(alt);
                    if (itemName.equalsIgnoreCase(altName)) {
                        isRequired = true;
                        if (isSell) {
                            // Item present in inventory = still needs selling -> orange
                            isMissing = true;
                        } else {
                            long required = bankItemManager.parseQuantityValue(alt);
                            long inInventory = countInInventory(altName);
                            isMissing = inInventory < required;
                        }
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

        return null;
    }

    private long countInInventory(String itemName) {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return 0;
        long total = 0;
        for (Item invItem : inv.getItems()) {
            if (invItem.getId() <= 0) continue;
            String name = itemManager.getItemComposition(invItem.getId()).getName();
            if (name != null && name.equalsIgnoreCase(itemName)) {
                total += invItem.getQuantity();
            }
        }
        return total;
    }
}
