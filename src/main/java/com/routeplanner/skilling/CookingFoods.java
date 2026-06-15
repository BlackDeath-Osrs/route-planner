package com.routeplanner.skilling;

import java.util.*;

public class CookingFoods {

    public static class Food {
        public final String name;
        public final int level;
        public final double xp;
        public final String rawItem;  // bank item to highlight
        public Food(String name, int level, double xp, String rawItem) {
            this.name = name; this.level = level; this.xp = xp; this.rawItem = rawItem;
        }
        @Override public String toString() { return name + " (Lv " + level + ")"; }
    }

    public static final List<Food> FOODS = Arrays.asList(
        new Food("Shrimps",    1,  30.0,  "Raw shrimps"),
        new Food("Chicken",    1,  30.0,  "Raw chicken"),
        new Food("Meat",       1,  30.0,  "Raw beef"),
        new Food("Sardine",    1,  40.0,  "Raw sardine"),
        new Food("Herring",    5,  50.0,  "Raw herring"),
        new Food("Trout",      15, 70.0,  "Raw trout"),
        new Food("Pike",       20, 80.0,  "Raw pike"),
        new Food("Salmon",     25, 90.0,  "Raw salmon"),
        new Food("Tuna",       30, 100.0, "Raw tuna"),
        new Food("Karambwan",  30, 190.0, "Raw karambwan"),
        new Food("Lobster",    40, 120.0, "Raw lobster"),
        new Food("Swordfish",  45, 140.0, "Raw swordfish"),
        new Food("Monkfish",   62, 150.0, "Raw monkfish"),
        new Food("Shark",      80, 210.0, "Raw shark"),
        new Food("Anglerfish", 84, 230.0, "Raw anglerfish"),
        // Hunter meats (Varlamore) - require Hunters' Rumours to cook
        new Food("Graahk",             41, 124.0, "Raw graahk"),
        new Food("Kyatt",              51, 143.0, "Raw kyatt"),
        new Food("Pyre fox",           59, 154.0, "Raw pyre fox"),
        new Food("Sunlight antelope",  68, 175.0, "Raw sunlight antelope"),
        new Food("Dashing kebbit",     82, 215.0, "Raw dashing kebbit"),
        new Food("Moonlight antelope", 92, 220.0, "Raw moonlight antelope"),
        // Sailing fish (deep sea trawling)
        new Food("Haddock",   73, 180.0,   "Raw haddock"),
        new Food("Yellowfin", 79, 200.0,   "Raw yellowfin"),
        new Food("Halibut",   83, 212.5,   "Raw halibut"),
        new Food("Bluefin",   87, 215.0,   "Raw bluefin"),
        new Food("Marlin",    90, 225.0,   "Raw marlin")
    );

    public static Food getByLabel(String label) {
        return FOODS.stream().filter(f -> f.toString().equals(label)).findFirst().orElse(null);
    }
}
