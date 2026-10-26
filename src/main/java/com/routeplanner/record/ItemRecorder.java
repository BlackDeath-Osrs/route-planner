package com.routeplanner.record;

import com.routeplanner.model.ItemMode;
import com.routeplanner.bank.BankItemManager;
import com.routeplanner.model.RouteStep;
import com.routeplanner.util.LocationNames;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.ItemVariationMapping;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The item half of Record mode: turns inventory changes into item steps.
 *
 *  - Bank / seed vault: inventory when it opens vs when it closes. Items you end up holding
 *    more of become a BANK step; emptying your inventory becomes a DEPOSIT_ALL step.
 *  - Shop: items gained become a SHOP step, items given away a SELL step (currency ignored).
 *  - Ground items: a "Take" click followed by that item arriving becomes a PICKUP step.
 *
 * Quantities follow how each mode completes: BANK checks your inventory total, so it records
 * what you ended up holding; SHOP and PICKUP check what you gained, so they record that.
 * Read-only like the rest of the recorder: it only looks at the inventory and interfaces.
 */
class ItemRecorder {

    /** A "Take" click counts for the item that arrives within this many ticks (walking to it). */
    private static final int PICKUP_WINDOW_TICKS = 50;
    private static final int SHOP_GROUP = 300;
    private static final int SHOP_STOCK_CHILD = 16;
    /** Shop currencies: spent when buying, received when selling, never a step's item. */
    private static final Set<String> CURRENCIES = new HashSet<>(Arrays.asList(
        "coins", "tokkul", "trading sticks", "platinum token", "numulite", "golden nugget",
        "mark of grace", "stardust", "warrior guild token", "castle wars ticket"));
    private static final Pattern CHARGED = Pattern.compile("^(.+)\\((\\d+)\\)$");

    private final Client client;
    private final ItemManager itemManager;
    private final RouteRecorder host;

    private boolean bankOpen;
    private boolean shopOpen;
    /** Inventory when the current bank/shop session started (or last emptied). */
    private Inventory sessionStart;
    /** Inventory at the last change, for spotting what a pickup added. */
    private Inventory lastInv;
    /** Lowercased item name -> tick its "Take" was clicked. */
    private final Map<String, Integer> pendingTakes = new HashMap<>();
    /** Running totals for a PICKUP draft, so repeated pickups at one spot become one step. */
    private RouteStep pickupStep;
    private final Map<String, Long> pickupCounts = new LinkedHashMap<>();

    /** Inventory contents by recorded name (noted items count as their normal form). */
    private static final class Inventory {
        final Map<String, Long> qty = new LinkedHashMap<>();
        boolean isEmpty() { return qty.isEmpty(); }
        long get(String name) { return qty.getOrDefault(name, 0L); }
    }

    ItemRecorder(Client client, ItemManager itemManager, RouteRecorder host) {
        this.client = client;
        this.itemManager = itemManager;
        this.host = host;
    }

    void start() {
        bankOpen = false;
        shopOpen = false;
        sessionStart = null;
        lastInv = snapshot();
        pendingTakes.clear();
        pickupStep = null;
        pickupCounts.clear();
    }

    /** A ground item's "Take" was clicked. */
    void onTake(int itemId, int tick) {
        String name = keyOf(itemId);
        if (name != null) pendingTakes.put(name.toLowerCase(), tick);
    }

    /** Called every tick with the player's tile (null inside instances). */
    void onTick(WorldPoint pos) {
        boolean bankNow = visible(ComponentID.BANK_ITEM_CONTAINER) || visible(InterfaceID.SeedVault.OBJ_LIST);
        if (bankNow && !bankOpen) sessionStart = snapshot();
        if (bankNow && sessionStart != null && !sessionStart.isEmpty()) {
            // Emptied the inventory: that's its own step; what's withdrawn after counts from empty.
            Inventory now = snapshot();
            if (now.isEmpty()) {
                RouteStep step = host.stepAt(pos, "Deposit all" + LocationNames.at(pos));
                step.setItemMode(ItemMode.DEPOSIT_ALL);
                sessionStart = now;
            }
        }
        if (!bankNow && bankOpen) finishBank(pos);
        bankOpen = bankNow;

        boolean shopNow = visible(SHOP_GROUP, SHOP_STOCK_CHILD);
        if (shopNow && !shopOpen) sessionStart = snapshot();
        if (!shopNow && shopOpen) finishShop(pos);
        shopOpen = shopNow;
    }

    /** The inventory changed; outside a bank or shop, look for picked-up items. */
    void onInventoryChanged(WorldPoint pos, int tick) {
        Inventory now = snapshot();
        if (!bankOpen && !shopOpen && lastInv != null) {
            Map<String, Long> other = new LinkedHashMap<>();
            for (Map.Entry<String, Long> e : now.qty.entrySet()) {
                long gained = e.getValue() - lastInv.get(e.getKey());
                if (gained <= 0) continue;
                Integer took = pendingTakes.get(e.getKey().toLowerCase());
                if (took == null || tick - took > PICKUP_WINDOW_TICKS) other.put(e.getKey(), gained);
                else addPickup(pos, e.getKey(), gained);
            }
            if (!other.isEmpty()) host.onItemsGained(other, tick);
        }
        lastInv = now;
    }

    private void finishBank(WorldPoint pos) {
        Inventory end = snapshot();
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, Long> e : end.qty.entrySet()) {
            if (e.getValue() > sessionStart.get(e.getKey())) {
                entries.add(itemEntry(e.getKey(), e.getValue()));
            }
        }
        if (!entries.isEmpty()) {
            RouteStep step = host.stepAt(pos, "Withdraw from bank" + LocationNames.at(pos));
            step.setItemMode(ItemMode.BANK);
            step.setItemList(String.join(", ", entries));
        }
        sessionStart = null;
    }

    private void finishShop(WorldPoint pos) {
        Inventory end = snapshot();
        List<String> sold = new ArrayList<>();
        List<String> bought = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>(sessionStart.qty.keySet());
        names.addAll(end.qty.keySet());
        for (String name : names) {
            if (CURRENCIES.contains(name.toLowerCase())) continue;
            long delta = end.get(name) - sessionStart.get(name);
            // SELL completes once none are left, so it lists names only.
            if (delta < 0) sold.add(itemEntry(name, 1));
            else if (delta > 0) bought.add(itemEntry(name, delta));
        }
        if (!sold.isEmpty()) {
            RouteStep step = host.stepAt(pos, "Sell to shop" + LocationNames.at(pos));
            step.setItemMode(ItemMode.SELL);
            step.setItemList(String.join(", ", sold));
        }
        if (!bought.isEmpty()) {
            RouteStep step = host.stepAt(pos, "Buy from shop" + LocationNames.at(pos));
            step.setItemMode(ItemMode.SHOP);
            step.setItemList(String.join(", ", bought));
        }
        sessionStart = null;
    }

    private void addPickup(WorldPoint pos, String name, long gained) {
        if (host.runningCourse(pos)) {
            // Marks of grace mid-lap: a bonus, not something the route should wait for.
            host.trace("PICKUP " + gained + " " + name + " ignored (agility lap)");
            return;
        }
        host.trace("PICKUP " + gained + " " + name);
        // Keep adding to the current pickup step while it's still the latest step nearby.
        // Loot from a kill step joins that step, so "kill and loot" stays one step.
        boolean renamable = true;
        if (pickupStep == null || host.lastDraft() != pickupStep || !host.isNear(pickupStep, pos)) {
            RouteStep last = host.lastDraft();
            if (last != null && last.getNpcKillCount() > 0 && !last.hasItems()
                && host.isNear(last, pos, 15)) {
                pickupStep = last;
            } else {
                pickupStep = host.stepAt(pos, "Pick up items" + LocationNames.at(pos));
            }
            pickupStep.setItemMode(ItemMode.PICKUP);
            pickupCounts.clear();
        }
        if (pickupStep.getNpcKillCount() > 0) renamable = false;
        pickupCounts.merge(name, gained, Long::sum);
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, Long> e : pickupCounts.entrySet()) {
            entries.add(itemEntry(e.getKey(), e.getValue()));
        }
        pickupStep.setItemList(String.join(", ", entries));
        if (renamable) pickupStep.setName(pickupCounts.size() == 1
            ? "Pick up " + name + LocationNames.at(pos)
            : "Pick up items" + LocationNames.at(pos));
    }

    /** One item-list entry, e.g. "28 Yew logs" or "3 Amulet of glory(*)". */
    private static String itemEntry(String name, long qty) {
        return qty > 1 ? qty + " " + name : name;
    }

    /**
     * The name an item is recorded under. Charged jewellery and gear (glory(4), games
     * necklace(8)) becomes "Amulet of glory(*)" so any charge counts and they add up; doses
     * (potions) keep their exact name, since a 1-dose potion isn't a full one.
     */
    private String keyOf(int itemId) {
        int id = itemManager.canonicalize(itemId);
        String name = nameOf(id);
        if (name == null) return null;
        Matcher m = CHARGED.matcher(name);
        if (!m.matches()) return name;
        ItemStats stats = itemManager.getItemStats(id);
        if (stats == null || !stats.isEquipable()) return name;
        int charges = 0;
        for (int v : ItemVariationMapping.getVariations(ItemVariationMapping.map(id))) {
            String n = nameOf(v);
            Matcher vm = n == null ? null : CHARGED.matcher(n);
            if (vm != null && vm.matches() && vm.group(1).equals(m.group(1))) charges++;
        }
        return charges >= 2 ? m.group(1) + BankItemManager.ANY_CHARGE : name;
    }

    private Inventory snapshot() {
        Inventory inv = new Inventory();
        ItemContainer c = client.getItemContainer(InventoryID.INVENTORY);
        if (c == null) return inv;
        for (Item it : c.getItems()) {
            if (it.getId() <= 0) continue;
            String name = keyOf(it.getId());
            if (name == null) continue;
            inv.qty.merge(name, (long) it.getQuantity(), Long::sum);
        }
        return inv;
    }

    private String nameOf(int itemId) {
        String n = itemManager.getItemComposition(itemManager.canonicalize(itemId)).getName();
        return n == null || n.isEmpty() || n.equalsIgnoreCase("null") ? null : n;
    }

    private boolean visible(int componentId) {
        Widget w = client.getWidget(componentId);
        return w != null && !w.isHidden();
    }

    private boolean visible(int group, int child) {
        Widget w = client.getWidget(group, child);
        return w != null && !w.isHidden();
    }

}
