package com.routeplanner.bank;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

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

    public String parseItemName(String input) {
        input = input.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("^[\\d.]+\\s*(?:k|m|mil|b)?\\s+(.+)$", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(input);
        if (m.matches()) return m.group(1).trim();
        return input;
    }

    public long parseQuantityValue(String input) {
        input = input.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("^([\\d.]+)\\s*(k|m|mil|b)?\\s+.+$", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(input);
        if (m.matches()) {
            try {
                double num = Double.parseDouble(m.group(1));
                String suffix = m.group(2) == null ? "" : m.group(2).toLowerCase();
                return suffixMultiply(num, suffix);
            } catch (Exception ignored) {}
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
                long have = inventoryQtys.getOrDefault(itemName, 0L);
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

    // PICKUP: how many gained so far (summed across groups, best alternative each) -- for HUD

    // PICKUP: total required across groups (largest alternative each) -- for HUD
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
        net.runelite.api.ItemContainer inv = client.getItemContainer(net.runelite.api.InventoryID.INVENTORY);
        if (inv == null || itemListStr == null) return snapshot;
        for (java.util.List<String> group : parseItemList(itemListStr)) {
            for (String alt : group) {
                String name = parseItemName(alt).toLowerCase();
                long count = 0;
                for (net.runelite.api.Item item : inv.getItems()) {
                    if (item.getId() <= 0) continue;
                    String n = itemManager.getItemComposition(item.getId()).getName();
                    if (n != null && n.toLowerCase().equals(name)) count += item.getQuantity();
                }
                snapshot.put(name, count);
            }
        }
        return snapshot;
    }

    /** True when enough of each item group has been gained ABOVE the baseline snapshot. */
    public boolean hasAllItemsAboveBaseline(String itemListStr, java.util.Map<String, Long> baseline) {
        if (baseline == null) return hasAllItems(itemListStr);
        net.runelite.api.ItemContainer inv = client.getItemContainer(net.runelite.api.InventoryID.INVENTORY);
        if (inv == null) return false;
        // Count current inventory
        java.util.Map<String, Long> current = new java.util.HashMap<>();
        for (net.runelite.api.Item item : inv.getItems()) {
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
                long gained = Math.max(0, current.getOrDefault(name, 0L) - base);
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
                if (invNames.contains(itemName)) return false; // still holding one
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
                if (invNames.contains(parseItemName(alt).toLowerCase())) holding = true;
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
                if (invQtys.getOrDefault(itemName, 0L) >= required) {
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
                long gained = Math.max(0, invQtys.getOrDefault(itemName, 0L) - base);
                if (gained >= required) have = true;
            }
            result.add(new String[]{disp.toString(), have ? "HAVE" : "NEED"});
        }
        return result;
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
                long have = inventoryQtys.getOrDefault(itemName, 0L);
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
