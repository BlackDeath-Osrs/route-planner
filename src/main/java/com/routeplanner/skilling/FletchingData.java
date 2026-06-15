package com.routeplanner.skilling;

import java.util.*;

public class FletchingData {

    public static class Item {
        public final String name;
        public final int level;
        public final double xp;
        public final String materials; // bank items to highlight
        public Item(String name, int level, double xp, String materials) {
            this.name = name; this.level = level; this.xp = xp; this.materials = materials;
        }
        @Override public String toString() { return name + " (Lv " + level + ")"; }
    }

    // ---- Arrows, darts & bolts: attach heads/feathers (XP per item) ----
    public static final List<Item> AMMO = Arrays.asList(
        new Item("Headless arrow", 1,  1.0,  "Arrow shaft, Feather"),
        new Item("Bronze arrow",   1,  1.3,  "Headless arrow, Bronze arrowtips"),
        new Item("Iron arrow",     15, 2.5,  "Headless arrow, Iron arrowtips"),
        new Item("Steel arrow",    30, 5.0,  "Headless arrow, Steel arrowtips"),
        new Item("Mithril arrow",  45, 7.5,  "Headless arrow, Mithril arrowtips"),
        new Item("Broad arrow",    52, 10.0, "Headless arrow, Broad arrowheads"),
        new Item("Adamant arrow",  60, 10.0, "Headless arrow, Adamant arrowtips"),
        new Item("Rune arrow",     75, 12.5, "Headless arrow, Rune arrowtips"),
        new Item("Amethyst arrow", 82, 13.5, "Headless arrow, Amethyst arrowtips"),
        new Item("Dragon arrow",   90, 15.0, "Headless arrow, Dragon arrowtips"),
        new Item("Bronze dart",    1,  1.8,  "Bronze dart tip, Feather"),
        new Item("Iron dart",      22, 3.8,  "Iron dart tip, Feather"),
        new Item("Steel dart",     37, 7.5,  "Steel dart tip, Feather"),
        new Item("Mithril dart",   52, 11.2, "Mithril dart tip, Feather"),
        new Item("Adamant dart",   67, 15.0, "Adamant dart tip, Feather"),
        new Item("Rune dart",      81, 18.8, "Rune dart tip, Feather"),
        new Item("Dragon dart",    95, 25.0, "Dragon dart tip, Feather"),
        new Item("Broad bolts",    55, 3.0,  "Broad bolts (unf), Feather")
    );

    // ---- Bows (cut/string), crossbow stocks, wooden shields ----
    public static final List<Item> BOWS = Arrays.asList(
        // Cut unstrung bow (knife on log)
        new Item("Shortbow (u)",        5,  5.0,  "Logs, Knife/Fletching knife"),
        new Item("Longbow (u)",         10, 10.0, "Logs, Knife/Fletching knife"),
        new Item("Oak shortbow (u)",    20, 16.5, "Oak logs, Knife/Fletching knife"),
        new Item("Oak longbow (u)",     25, 25.0, "Oak logs, Knife/Fletching knife"),
        new Item("Willow shortbow (u)", 35, 33.3, "Willow logs, Knife/Fletching knife"),
        new Item("Willow longbow (u)",  40, 41.5, "Willow logs, Knife/Fletching knife"),
        new Item("Maple shortbow (u)",  50, 50.0, "Maple logs, Knife/Fletching knife"),
        new Item("Maple longbow (u)",   55, 58.3, "Maple logs, Knife/Fletching knife"),
        new Item("Yew shortbow (u)",    65, 67.5, "Yew logs, Knife/Fletching knife"),
        new Item("Yew longbow (u)",     70, 75.0, "Yew logs, Knife/Fletching knife"),
        new Item("Magic shortbow (u)",  80, 83.3, "Magic logs, Knife/Fletching knife"),
        new Item("Magic longbow (u)",   85, 91.5, "Magic logs, Knife/Fletching knife"),
        // String bow (bow string on unstrung)
        new Item("Shortbow",        5,  5.0,  "Shortbow (u), Bow string"),
        new Item("Longbow",         10, 10.0, "Longbow (u), Bow string"),
        new Item("Oak shortbow",    20, 16.5, "Oak shortbow (u), Bow string"),
        new Item("Oak longbow",     25, 25.0, "Oak longbow (u), Bow string"),
        new Item("Willow shortbow", 35, 33.3, "Willow shortbow (u), Bow string"),
        new Item("Willow longbow",  40, 41.5, "Willow longbow (u), Bow string"),
        new Item("Maple shortbow",  50, 50.0, "Maple shortbow (u), Bow string"),
        new Item("Maple longbow",   55, 58.3, "Maple longbow (u), Bow string"),
        new Item("Yew shortbow",    65, 67.5, "Yew shortbow (u), Bow string"),
        new Item("Yew longbow",     70, 75.0, "Yew longbow (u), Bow string"),
        new Item("Magic shortbow",  80, 83.3, "Magic shortbow (u), Bow string"),
        new Item("Magic longbow",   85, 91.5, "Magic longbow (u), Bow string"),
        // Crossbow stocks (knife on log) - stock XP, verify if you train these
        new Item("Wooden stock",   9,  6.0,  "Logs, Knife/Fletching knife"),
        new Item("Oak stock",      24, 16.0, "Oak logs, Knife/Fletching knife"),
        new Item("Willow stock",   39, 22.0, "Willow logs, Knife/Fletching knife"),
        new Item("Teak stock",     46, 27.0, "Teak logs, Knife/Fletching knife"),
        new Item("Maple stock",    54, 32.0, "Maple logs, Knife/Fletching knife"),
        new Item("Mahogany stock", 61, 41.0, "Mahogany logs, Knife/Fletching knife"),
        new Item("Yew stock",      69, 46.0, "Yew logs, Knife/Fletching knife"),
        // Wooden shields (knife on 2 logs) - XP = full longbow of that wood
        new Item("Oak shield",     27, 50.0,  "Oak logs, Knife/Fletching knife"),
        new Item("Willow shield",  42, 83.0,  "Willow logs, Knife/Fletching knife"),
        new Item("Maple shield",   57, 116.6, "Maple logs, Knife/Fletching knife"),
        new Item("Yew shield",     72, 150.0, "Yew logs, Knife/Fletching knife"),
        new Item("Magic shield",   87, 183.0, "Magic logs, Knife/Fletching knife"),
        new Item("Redwood shield", 90, 216.0, "Redwood logs, Knife/Fletching knife")
    );

    public static Item getByLabel(List<Item> list, String label) {
        return list.stream().filter(i -> i.toString().equals(label)).findFirst().orElse(null);
    }
}
