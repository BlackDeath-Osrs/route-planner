package com.routeplanner.bank;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class BankItemManager {

    @Inject private Client client;
    @Inject private ItemManager itemManager;

    // Parse a quantity prefix like "1k", "1m", "1mil", "500k", "2.5m" from item name
    // Returns [quantity, itemName] or [1, fullString] if no quantity found
    private long suffixMultiply(double num, String suffix) {
        if (suffix == null) return (long) num;
        switch (suffix.toLowerCase()) {
            case "k": return (long)(num * 1_000);
            case "m": case "mil": return (long)(num * 1_000_000);
            case "b": return (long)(num * 1_000_000_000);
            default: return (long) num;
        }
    }

    /** "2.5k Coins": the amount (1), its suffix (2) and the name (3). Compiled once; it runs per frame. */
    private static final Pattern QUANTITY_PREFIX = Pattern
        .compile("^([\\d.]+)\\s*(k|m|mil|b)?\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARGE_SUFFIX = Pattern.compile("\\(\\d+\\)");

    /** Item-list names ending in "(*)" mean any charge: "Amulet of glory(*)" matches glory(1)-(6). */
    public static final String ANY_CHARGE = "(*)";

    /** True when an item-list name (without quantity) refers to the actual item name. */
    public static boolean nameMatches(String listName, String actual) {
        if (listName == null || actual == null) return false;
        String want = listName.trim().toLowerCase();
        String have = actual.trim().toLowerCase();
        if (!want.endsWith(ANY_CHARGE)) return want.equals(have);
        String base = want.substring(0, want.length() - ANY_CHARGE.length()).trim();
        return have.startsWith(base) && CHARGE_SUFFIX.matcher(have.substring(base.length()).trim()).matches();
    }

    /** How many of an item-list name are in a name -> quantity map, summing every charge for "(*)". */
    public static long countOf(Map<String, Long> qtys, String listName) {
        if (!listName.trim().endsWith(ANY_CHARGE)) return qtys.getOrDefault(listName.trim().toLowerCase(), 0L);
        long total = 0;
        for (Map.Entry<String, Long> e : qtys.entrySet()) {
            if (nameMatches(listName, e.getKey())) total += e.getValue();
        }
        return total;
    }

    private static boolean anyMatches(Set<String> names, String listName) {
        for (String n : names) if (nameMatches(listName, n)) return true;
        return false;
    }

    public String parseItemName(String input) {
        input = input.trim();
        Matcher m = QUANTITY_PREFIX.matcher(input);
        if (m.matches()) return m.group(3).trim();
        return input;
    }

    public long parseQuantityValue(String input) {
        input = input.trim();
        Matcher m = QUANTITY_PREFIX.matcher(input);
        if (m.matches()) {
            try {
                double num = Double.parseDouble(m.group(1));
                String suffix = m.group(2) == null ? "" : m.group(2).toLowerCase();
                return suffixMultiply(num, suffix);
            } catch (Exception e) {
                log.debug("Bad item quantity in '{}', counting 1: {}", input, e.toString());
            }
        }
        return 1;
    }

    // Parse item list string into groups (each group is a list of alternatives)
    // e.g. "Spade,1mil Coins/Platinum token" ->
    //      [["Spade"], ["1mil Coins", "Platinum token"]]
    public List<List<String>> parseItemList(String itemListStr) {
        if (itemListStr == null || itemListStr.trim().isEmpty()) return Collections.emptyList();
        List<List<String>> groups = new ArrayList<>();
        for (String group : itemListStr.split(",")) {
            group = group.trim();
            if (group.isEmpty()) continue;
            List<String> alternatives = new ArrayList<>();
            for (String alt : group.split("/")) {
                alt = alt.trim();
                if (!alt.isEmpty()) alternatives.add(alt);
            }
            if (!alternatives.isEmpty()) groups.add(alternatives);
        }
        return groups;
    }

    // Check if player has all required items in inventory with quantity
    public boolean hasAllItems(String itemListStr) {
        List<List<String>> groups = parseItemList(itemListStr);
        if (groups.isEmpty()) return true;

        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) return false;

        // Build map of item name -> total quantity in inventory
        Map<String, Long> inventoryQtys = new HashMap<>();
        for (Item item : inventory.getItems()) {
            if (item.getId() <= 0) continue;
            String name = itemManager.getItemComposition(item.getId()).getName();
            if (name != null) {
                inventoryQtys.merge(name.toLowerCase(), (long) item.getQuantity(), Long::sum);
            }
        }

        for (List<String> group : groups) {
            boolean groupSatisfied = false;
            for (String alt : group) {
                String itemName = parseItemName(alt).toLowerCase();
                long required = parseQuantityValue(alt);
                long have = countOf(inventoryQtys, itemName);
                if (have >= required) {
                    groupSatisfied = true;
                    break;
                }
            }
            if (!groupSatisfied) return false;
        }
        return true;
    }

    // Current inventory quantities (item name lowercased -> total qty across stacks)
    private Map<String, Long> inventoryQtyMap() {
        Map<String, Long> qtys = new HashMap<>();
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) return qtys;
        for (Item item : inventory.getItems()) {
            if (item.getId() <= 0) continue;
            String name = itemManager.getItemComposition(item.getId()).getName();
            if (name != null) qtys.merge(name.toLowerCase(), (long) item.getQuantity(), Long::sum);
        }
        return qtys;
    }

    // PICKUP: snapshot current counts of every item referenced in the list (baseline at step start)

    // PICKUP: true once enough of each group's item has been gained since the baseline

    // PICKUP: how many gained so far (summed across groups, best alternative each), for the HUD

    // PICKUP: total required across groups (largest alternative each), for the HUD
    public long pickupRequired(String itemListStr) {
        long total = 0;
        for (List<String> group : parseItemList(itemListStr)) {
            long req = 0;
            for (String alt : group) req = Math.max(req, parseQuantityValue(alt));
            total += req;
        }
        return total;
    }

    /** Snapshot current inventory counts for items referenced in itemListStr. */
    public java.util.Map<String, Long> snapshotInventory(String itemListStr) {
        java.util.Map<String, Long> snapshot = new java.util.HashMap<>();
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null || itemListStr == null) return snapshot;
        for (java.util.List<String> group : parseItemList(itemListStr)) {
            for (String alt : group) {
                String name = parseItemName(alt).toLowerCase();
                long count = 0;
                for (Item item : inv.getItems()) {
                    if (item.getId() <= 0) continue;
                    String n = itemManager.getItemComposition(item.getId()).getName();
                    if (nameMatches(name, n)) count += item.getQuantity();
                }
                snapshot.put(name, count);
            }
        }
        return snapshot;
    }

    /** True when enough of each item group has been gained ABOVE the baseline snapshot. */
    public boolean hasAllItemsAboveBaseline(String itemListStr, java.util.Map<String, Long> baseline) {
        if (baseline == null) return hasAllItems(itemListStr);
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return false;
        // Count current inventory
        java.util.Map<String, Long> current = new java.util.HashMap<>();
        for (Item item : inv.getItems()) {
            if (item.getId() <= 0) continue;
            String n = itemManager.getItemComposition(item.getId()).getName();
            if (n == null) continue;
            current.merge(n.toLowerCase(), (long) item.getQuantity(), Long::sum);
        }
        for (java.util.List<String> group : parseItemList(itemListStr)) {
            long required = 0;
            for (String alt : group) required = Math.max(required, parseQuantityValue(alt));
            // Check if any alternative in the group satisfies the required amount above baseline
            boolean groupSatisfied = false;
            for (String alt : group) {
                String name = parseItemName(alt).toLowerCase();
                long base = baseline.getOrDefault(name, 0L);
                long gained = Math.max(0, countOf(current, name) - base);
                if (gained >= required) { groupSatisfied = true; break; }
            }
            if (!groupSatisfied) return false;
        }
        return true;
    }

    // SELL: returns true when none of the listed items remain in the inventory (all sold)
    public boolean hasSoldAll(String itemListStr) {
        List<List<String>> groups = parseItemList(itemListStr);
        if (groups.isEmpty()) return true;

        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) return true;

        Set<String> invNames = new HashSet<>();
        for (Item item : inventory.getItems()) {
            if (item.getId() <= 0) continue;
            String name = itemManager.getItemComposition(item.getId()).getName();
            if (name != null) invNames.add(name.toLowerCase());
        }

        for (List<String> group : groups) {
            for (String alt : group) {
                String itemName = parseItemName(alt).toLowerCase();
                if (anyMatches(invNames, itemName)) return false; // still holding one
            }
        }
        return true;
    }

    // SELL: returns [displayText, status] where status is "SOLD" or "HOLDING"
    public List<String[]> getSellStatusList(String itemListStr) {
        List<List<String>> groups = parseItemList(itemListStr);
        List<String[]> result = new ArrayList<>();
        if (groups.isEmpty()) return result;

        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        Set<String> invNames = new HashSet<>();
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                if (item.getId() <= 0) continue;
                String name = itemManager.getItemComposition(item.getId()).getName();
                if (name != null) invNames.add(name.toLowerCase());
            }
        }

        for (List<String> group : groups) {
            boolean holding = false;
            StringBuilder disp = new StringBuilder();
            for (int i = 0; i < group.size(); i++) {
                String alt = group.get(i);
                if (i > 0) disp.append("/");
                disp.append(alt);
                if (anyMatches(invNames, parseItemName(alt))) holding = true;
            }
            result.add(new String[]{disp.toString(), holding ? "HOLDING" : "SOLD"});
        }
        return result;
    }

    // Returns list of [displayText, status] where status is "HAVE" or "NEED"
    // Used by the HUD to show the item checklist on screen.
    public List<String[]> getItemStatusList(String itemListStr) {
        List<List<String>> groups = parseItemList(itemListStr);
        List<String[]> result = new ArrayList<>();
        if (groups.isEmpty()) return result;

        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        Map<String, Long> invQtys = new HashMap<>();
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                if (item.getId() <= 0) continue;
                String name = itemManager.getItemComposition(item.getId()).getName();
                if (name != null) {
                    invQtys.merge(name.toLowerCase(), (long) item.getQuantity(), Long::sum);
                }
            }
        }

        for (List<String> group : groups) {
            boolean have = false;
            StringBuilder disp = new StringBuilder();
            for (int i = 0; i < group.size(); i++) {
                String alt = group.get(i);
                if (i > 0) disp.append("/");
                disp.append(alt);
                String itemName = parseItemName(alt).toLowerCase();
                long required = parseQuantityValue(alt);
                if (countOf(invQtys, itemName) >= required) {
                    have = true;
                }
            }
            result.add(new String[]{disp.toString(), have ? "HAVE" : "NEED"});
        }
        return result;
    }

    /** Same as getItemStatusList but subtracts baseline counts for PICKUP steps. */
    public List<String[]> getItemStatusList(String itemListStr, java.util.Map<String, Long> baseline) {
        if (baseline == null) return getItemStatusList(itemListStr);
        List<List<String>> groups = parseItemList(itemListStr);
        List<String[]> result = new ArrayList<>();
        if (groups.isEmpty()) return result;
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        Map<String, Long> invQtys = new HashMap<>();
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                if (item.getId() <= 0) continue;
                String name = itemManager.getItemComposition(item.getId()).getName();
                if (name != null) invQtys.merge(name.toLowerCase(), (long) item.getQuantity(), Long::sum);
            }
        }
        for (List<String> group : groups) {
            boolean have = false;
            StringBuilder disp = new StringBuilder();
            for (int i = 0; i < group.size(); i++) {
                String alt = group.get(i);
                if (i > 0) disp.append("/");
                disp.append(alt);
                String itemName = parseItemName(alt).toLowerCase();
                long required = parseQuantityValue(alt);
                long base = baseline.getOrDefault(itemName, 0L);
                long gained = Math.max(0, countOf(invQtys, itemName) - base);
                if (gained >= required) have = true;
            }
            result.add(new String[]{disp.toString(), have ? "HAVE" : "NEED"});
        }
        return result;
    }

    /** One line of an item list with live counts, for the side panel's step card. */
    public static final class ItemProgress {
        public final String name;
        public final long have;
        public final long need;
        public final int itemId; // -1 when the item couldn't be identified

        ItemProgress(String name, long have, long need, int itemId) {
            this.name = name;
            this.have = have;
            this.need = need;
            this.itemId = itemId;
        }
    }

    /**
     * Have/need counts per item-list entry, counting only items gained since {@code baseline}
     * when one is given. For alternatives ("Coins/Platinum token") the closest one is shown.
     * Call on the client thread.
     */
    public List<ItemProgress> getItemProgress(String itemListStr, Map<String, Long> baseline) {
        List<ItemProgress> out = new ArrayList<>();
        List<List<String>> groups = parseItemList(itemListStr);
        if (groups.isEmpty()) return out;
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        Map<String, Long> invQtys = new HashMap<>();
        Map<String, Integer> invIds = new HashMap<>();
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                if (item.getId() <= 0) continue;
                String name = itemManager.getItemComposition(item.getId()).getName();
                if (name == null) continue;
                invQtys.merge(name.toLowerCase(), (long) item.getQuantity(), Long::sum);
                invIds.putIfAbsent(name.toLowerCase(), item.getId());
            }
        }
        for (List<String> group : groups) {
            ItemProgress best = null;
            for (String alt : group) {
                String name = parseItemName(alt);
                long need = parseQuantityValue(alt);
                long count = countOf(invQtys, name);
                if (baseline != null) count = Math.max(0, count - baseline.getOrDefault(name.toLowerCase(), 0L));
                long have = Math.min(count, need);
                if (best == null || (double) have / need > (double) best.have / best.need) {
                    best = new ItemProgress(name, have, need, idFor(name, invIds));
                }
            }
            if (best != null) out.add(best);
        }
        return out;
    }

    /** A Sell-step line: 1/1 once the item has left the inventory. */
    public ItemProgress soldLine(String name, boolean sold) {
        return new ItemProgress(name, sold ? 1 : 0, 1, idFor(parseItemName(name.split("/")[0]), new HashMap<>()));
    }

    private final Map<String, Integer> idCache = new HashMap<>();

    /** An item id for a list name, for its icon: from the inventory, else the item search. */
    private int idFor(String listName, Map<String, Integer> invIds) {
        for (Map.Entry<String, Integer> e : invIds.entrySet()) {
            if (nameMatches(listName, e.getKey())) return e.getValue();
        }
        String key = listName.trim().toLowerCase();
        Integer cached = idCache.get(key);
        if (cached != null) return cached;
        int id = -1;
        String search = key.endsWith(ANY_CHARGE) ? key.substring(0, key.length() - ANY_CHARGE.length()).trim() : key;
        for (ItemPrice p : itemManager.search(search)) {
            if (nameMatches(listName, p.getName()) || p.getName().equalsIgnoreCase(search)) { id = p.getId(); break; }
        }
        idCache.put(key, id);
        return id;
    }

    // Get list of missing items with quantities
    public List<String> getMissingItems(String itemListStr) {
        List<List<String>> groups = parseItemList(itemListStr);
        List<String> missing = new ArrayList<>();
        if (groups.isEmpty()) return missing;

        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        Map<String, Long> inventoryQtys = new HashMap<>();
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                if (item.getId() <= 0) continue;
                String name = itemManager.getItemComposition(item.getId()).getName();
                if (name != null) {
                    inventoryQtys.merge(name.toLowerCase(), (long) item.getQuantity(), Long::sum);
                }
            }
        }

        for (List<String> group : groups) {
            boolean found = false;
            for (String alt : group) {
                String itemName = parseItemName(alt).toLowerCase();
                long required = parseQuantityValue(alt);
                long have = countOf(inventoryQtys, itemName);
                if (have >= required) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                missing.add(String.join("/", group));
            }
        }
        return missing;
    }
}
