package com.routeplanner.bank;

import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import com.routeplanner.util.Herbs;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;
import javax.inject.*;
import java.util.*;
import java.util.regex.*;

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

    /**
     * True when an item-list name (without quantity) refers to the actual item name. Besides plain
     * names: "Amulet of glory(*)" is any charge, "*" is a wildcard ("Grimy *" is every grimy herb)
     * and "Any herb" is any grimy or clean herb.
     */
    public static boolean nameMatches(String listName, String actual) {
        if (listName == null || actual == null) return false;
        String want = listName.trim().toLowerCase();
        String have = actual.trim().toLowerCase();
        if (want.equals(Herbs.ANY_HERB.toLowerCase())) return Herbs.isHerb(have);
        if (want.endsWith(ANY_CHARGE)) {
            String base = want.substring(0, want.length() - ANY_CHARGE.length()).trim();
            return have.startsWith(base) && CHARGE_SUFFIX.matcher(have.substring(base.length()).trim()).matches();
        }
        if (want.contains("*")) return have.matches(globToRegex(want));
        return want.equals(have);
    }

    private static String globToRegex(String glob) {
        StringBuilder re = new StringBuilder();
        for (String part : glob.split("\\*", -1)) {
            if (re.length() > 0) re.append(".*");
            re.append(Pattern.quote(part));
        }
        return re.toString();
    }

    /** Whether an item-list name stands for several items, so counts have to be summed. */
    private static boolean isPattern(String listName) {
        String n = listName.trim();
        return n.contains("*") || n.equalsIgnoreCase(Herbs.ANY_HERB);
    }

    /** How many of an item-list name are in a name -> quantity map, summing every match for a pattern. */
    public static long countOf(Map<String, Long> qtys, String listName) {
        if (!isPattern(listName)) return qtys.getOrDefault(listName.trim().toLowerCase(), 0L);
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

        if (client.getItemContainer(InventoryID.INVENTORY) == null) return false;
        // Worn items count too: an amulet to bring is often put straight on
        Map<String, Long> inventoryQtys = carriedQtys(true);

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

    /** Item name (lower-cased) -> total quantity in the inventory, plus what's worn when {@code worn}. */
    private Map<String, Long> carriedQtys(boolean worn) {
        Map<String, Long> qtys = new HashMap<>();
        addQtys(qtys, null, InventoryID.INVENTORY);
        if (worn) addQtys(qtys, null, InventoryID.EQUIPMENT);
        return qtys;
    }

    /** Adds a container's items to {@code qtys}, and their first item id per name to {@code ids} if given. */
    private void addQtys(Map<String, Long> qtys, Map<String, Integer> ids, InventoryID container) {
        ItemContainer c = client.getItemContainer(container);
        if (c == null) return;
        for (Item item : c.getItems()) {
            if (item.getId() <= 0) continue;
            String name = itemManager.getItemComposition(item.getId()).getName();
            if (name == null) continue;
            qtys.merge(name.toLowerCase(), (long) item.getQuantity(), Long::sum);
            if (ids != null) ids.putIfAbsent(name.toLowerCase(), item.getId());
        }
    }

    // PICKUP: snapshot current counts of every item referenced in the list (baseline at step start)

    // PICKUP: true once enough of each group's item has been gained since the baseline

    // PICKUP: how many gained so far (summed across groups, best alternative each), for the HUD

    /**
     * Counts of the items referenced in itemListStr, carried or worn, when the step starts.
     * Worn items count on both sides, so putting on an item that was picked up keeps it gained.
     */
    public java.util.Map<String, Long> snapshotInventory(String itemListStr) {
        java.util.Map<String, Long> snapshot = new java.util.HashMap<>();
        if (client.getItemContainer(InventoryID.INVENTORY) == null || itemListStr == null) return snapshot;
        Map<String, Long> carried = carriedQtys(true);
        for (java.util.List<String> group : parseItemList(itemListStr)) {
            for (String alt : group) {
                String name = parseItemName(alt).toLowerCase();
                snapshot.put(name, countOf(carried, name));
            }
        }
        return snapshot;
    }

    /** True when enough of each item group has been gained ABOVE the baseline snapshot. */
    public boolean hasAllItemsAboveBaseline(String itemListStr, java.util.Map<String, Long> baseline) {
        if (baseline == null) return hasAllItems(itemListStr);
        if (client.getItemContainer(InventoryID.INVENTORY) == null) return false;
        Map<String, Long> current = carriedQtys(true);
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

    /** Names of the unnoted items in the inventory, lowercased. */
    private Set<String> unnotedInventoryNames() {
        Set<String> names = new HashSet<>();
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) return names;
        for (Item item : inventory.getItems()) {
            if (item.getId() <= 0) continue;
            net.runelite.api.ItemComposition comp = itemManager.getItemComposition(item.getId());
            if (comp.getNote() != -1) continue; // noted
            if (comp.getName() != null) names.add(comp.getName().toLowerCase());
        }
        return names;
    }

    // USE_UP: true when none of the listed items are left unnoted in the inventory
    public boolean hasNoneUnnoted(String itemListStr) {
        if (client.getItemContainer(InventoryID.INVENTORY) == null) return false;
        Set<String> names = unnotedInventoryNames();
        for (List<String> group : parseItemList(itemListStr)) {
            for (String alt : group) {
                if (anyMatches(names, parseItemName(alt))) return false;
            }
        }
        return true;
    }

    // USE_UP: [displayText, "DONE" or "LEFT"] per list entry
    public List<String[]> getUseUpStatusList(String itemListStr) {
        Set<String> names = unnotedInventoryNames();
        List<String[]> result = new ArrayList<>();
        for (List<String> group : parseItemList(itemListStr)) {
            boolean left = false;
            for (String alt : group) left |= anyMatches(names, parseItemName(alt));
            result.add(new String[]{String.join("/", group), left ? "LEFT" : "DONE"});
        }
        return result;
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

        Map<String, Long> invQtys = carriedQtys(true);

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
        Map<String, Long> invQtys = carriedQtys(true);
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
    @RequiredArgsConstructor
    public static final class ItemProgress {
        public final String name;
        public final long have;
        public final long need;
        public final int itemId; // -1 when the item couldn't be identified

    }

    /**
     * Have/need counts per item-list entry, carried and worn, counting only items gained since
     * {@code baseline} when one is given. For
     * alternatives ("Coins/Platinum token") the closest one is shown. Call on the client thread.
     */
    public List<ItemProgress> getItemProgress(String itemListStr, Map<String, Long> baseline) {
        List<ItemProgress> out = new ArrayList<>();
        List<List<String>> groups = parseItemList(itemListStr);
        if (groups.isEmpty()) return out;
        Map<String, Long> invQtys = new HashMap<>();
        Map<String, Integer> invIds = new HashMap<>();
        addQtys(invQtys, invIds, InventoryID.INVENTORY);
        addQtys(invQtys, invIds, InventoryID.EQUIPMENT);
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

    private final Map<String, Integer> idCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** The first listed item's id, for a step's icon; -1 when it can't be identified. */
    public int firstItemId(String itemListStr) {
        List<List<String>> groups = parseItemList(itemListStr);
        if (groups.isEmpty()) return -1;
        String name = parseItemName(groups.get(0).get(0));
        int id = idFor(name, Collections.emptyMap());
        if (id <= 0) idCache.remove(name.trim().toLowerCase()); // the item list may not have loaded yet
        return id;
    }

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

}
