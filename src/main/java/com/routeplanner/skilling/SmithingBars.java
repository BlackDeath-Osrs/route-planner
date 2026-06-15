package com.routeplanner.skilling;

import java.util.*;

public class SmithingBars {

    public static class Bar {
        public final String name;
        public final int level;       // smelt level
        public final double smeltXp;  // XP per bar smelted
        public final double smithXp;  // XP per bar smithed into items (0 = not anvil-smithed)
        public final String ores;     // ores to highlight when smelting
        public Bar(String name, int level, double smeltXp, double smithXp, String ores) {
            this.name = name; this.level = level;
            this.smeltXp = smeltXp; this.smithXp = smithXp; this.ores = ores;
        }
        @Override public String toString() { return name + " (Lv " + level + ")"; }
    }

    public static final List<Bar> BARS = Arrays.asList(
        new Bar("Bronze bar",     1,  6.2,  12.5, "Copper ore, Tin ore"),
        new Bar("Iron bar",       15, 12.5, 25.0, "Iron ore"),
        new Bar("Silver bar",     20, 13.7, 0.0,  "Silver ore"),
        new Bar("Steel bar",      30, 17.5, 37.5, "Iron ore, Coal"),
        new Bar("Gold bar",       40, 22.5, 0.0,  "Gold ore"),
        new Bar("Mithril bar",    50, 30.0, 50.0, "Mithril ore, Coal"),
        new Bar("Adamantite bar", 70, 37.5, 62.5, "Adamantite ore, Coal"),
        new Bar("Runite bar",     85, 50.0, 75.0, "Runite ore, Coal")
    );

    public static Bar getByLabel(String label) {
        return BARS.stream().filter(b -> b.toString().equals(label)).findFirst().orElse(null);
    }
}
