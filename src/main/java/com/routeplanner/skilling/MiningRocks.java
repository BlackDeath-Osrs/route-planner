package com.routeplanner.skilling;

import java.util.*;

public class MiningRocks {

    public static class Rock {
        public final String name;        // display
        public final int level;
        public final double xp;
        public final String objectName;  // in-game object name to highlight
        public Rock(String name, int level, double xp, String objectName) {
            this.name = name; this.level = level; this.xp = xp;
            this.objectName = objectName;
        }
        @Override public String toString() { return name + " (Lv " + level + ")"; }
    }

    public static final List<Rock> ROCKS = Arrays.asList(
        new Rock("Copper",     1,  17.5,  "Copper Rocks"),
        new Rock("Tin",        1,  17.5,  "Tin Rocks"),
        new Rock("Iron",       15, 35.0,  "Iron Rocks"),
        new Rock("Silver",     20, 40.0,  "Silver Rocks"),
        new Rock("Coal",       30, 50.0,  "Coal Rocks"),
        new Rock("Gold",       40, 65.0,  "Gold Rocks"),
        new Rock("Mithril",    55, 80.0,  "Mithril Rocks"),
        new Rock("Adamantite", 70, 95.0,  "Adamantite Rocks"),
        new Rock("Runite",     85, 125.0, "Runite Rocks"),
        new Rock("Gem Rocks",   40, 65.0,  "Gem Rocks"),
        new Rock("Amethyst",   92, 240.0, "Amethyst crystals")
    );

    public static Rock getByLabel(String label) {
        return ROCKS.stream().filter(r -> r.toString().equals(label)).findFirst().orElse(null);
    }
}
