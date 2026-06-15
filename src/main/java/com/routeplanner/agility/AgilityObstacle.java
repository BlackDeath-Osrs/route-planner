package com.routeplanner.agility;

import lombok.Data;
import net.runelite.api.coords.WorldPoint;

@Data
public class AgilityObstacle {
    private final String name;
    private final int[] objectIds;
    private final WorldPoint location; // nullable - used only as hint

    public AgilityObstacle(String name, WorldPoint location, int... objectIds) {
        this.name = name;
        this.location = location;
        this.objectIds = objectIds;
    }

    public boolean matchesId(int id) {
        for (int oid : objectIds) {
            if (oid == id) return true;
        }
        return false;
    }
}
