package com.routeplanner.teleport;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

/**
 * A jewellery/item teleport destination. Unlike spells, a single physical item
 * (e.g. Amulet of glory) has multiple charge states, any of which can teleport;
 * chargeIds lists every item id that should be highlighted in the inventory.
 * iconId is the id used to render the picker icon (highest charge, the "full" look).
 * Highlight only: the plugin never clicks or operates the item.
 */
@Getter
public class TeleportItem {
    private final String displayName;   // e.g. "Amulet of glory: Edgeville"
    private final String itemName;      // e.g. "Amulet of glory" (base item, for grouping)
    private final int[] chargeIds;      // all item ids to match in inventory (any charge)
    private final int iconId;           // item id to render as the picker icon
    private final WorldPoint destination;

    public TeleportItem(String displayName, String itemName, int[] chargeIds, int iconId, WorldPoint destination) {
        this.displayName = displayName;
        this.itemName = itemName;
        this.chargeIds = chargeIds;
        this.iconId = iconId;
        this.destination = destination;
    }

    /** True if the given inventory item id is one of this teleport's charge states. */
    public boolean matchesItemId(int id) {
        for (int c : chargeIds) if (c == id) return true;
        return false;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
