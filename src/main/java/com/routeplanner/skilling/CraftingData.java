package com.routeplanner.skilling;

import java.util.*;

public class CraftingData {

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

    // ---- Jewellery: cut gems (chisel) + gold/silver bar + mould (+ cut gem) at a furnace ----
    public static final List<Item> JEWELRY = Arrays.asList(
        // Gold (no gem)
        new Item("Gold ring",          5,  15.0,  "Gold bar, Ring mould"),
        new Item("Gold necklace",      6,  22.5,  "Gold bar, Necklace mould"),
        new Item("Gold bracelet",      7,  25.0,  "Gold bar, Bracelet mould"),
        new Item("Gold amulet",        8,  30.0,  "Gold bar, Amulet mould"),
        // Opal (silver)
        new Item("Cut opal",           1,  15.0,  "Uncut opal, Chisel"),
        new Item("Opal ring",          1,  10.0,  "Silver bar, Ring mould, Opal"),
        new Item("Opal necklace",      16, 35.0,  "Silver bar, Necklace mould, Opal"),
        new Item("Opal bracelet",      22, 45.0,  "Silver bar, Bracelet mould, Opal"),
        new Item("Opal amulet",        27, 55.0,  "Silver bar, Amulet mould, Opal"),
        // Jade (silver)
        new Item("Cut jade",           13, 20.0,  "Uncut jade, Chisel"),
        new Item("Jade ring",          13, 32.0,  "Silver bar, Ring mould, Jade"),
        new Item("Jade necklace",      25, 54.0,  "Silver bar, Necklace mould, Jade"),
        new Item("Jade bracelet",      29, 60.0,  "Silver bar, Bracelet mould, Jade"),
        new Item("Jade amulet",        34, 70.0,  "Silver bar, Amulet mould, Jade"),
        // Red topaz (silver)
        new Item("Cut red topaz",      16, 25.0,  "Uncut red topaz, Chisel"),
        new Item("Topaz ring",         16, 35.0,  "Silver bar, Ring mould, Red topaz"),
        new Item("Topaz necklace",     32, 70.0,  "Silver bar, Necklace mould, Red topaz"),
        new Item("Topaz bracelet",     38, 75.0,  "Silver bar, Bracelet mould, Red topaz"),
        new Item("Topaz amulet",       45, 80.0,  "Silver bar, Amulet mould, Red topaz"),
        // Sapphire (gold)
        new Item("Cut sapphire",       20, 50.0,  "Uncut sapphire, Chisel"),
        new Item("Sapphire ring",      20, 40.0,  "Gold bar, Ring mould, Sapphire"),
        new Item("Sapphire necklace",  22, 55.0,  "Gold bar, Necklace mould, Sapphire"),
        new Item("Sapphire bracelet",  23, 60.0,  "Gold bar, Bracelet mould, Sapphire"),
        new Item("Sapphire amulet",    24, 65.0,  "Gold bar, Amulet mould, Sapphire"),
        // Emerald (gold)
        new Item("Cut emerald",        27, 67.5,  "Uncut emerald, Chisel"),
        new Item("Emerald ring",       27, 55.0,  "Gold bar, Ring mould, Emerald"),
        new Item("Emerald necklace",   29, 60.0,  "Gold bar, Necklace mould, Emerald"),
        new Item("Emerald bracelet",   30, 65.0,  "Gold bar, Bracelet mould, Emerald"),
        new Item("Emerald amulet",     31, 70.0,  "Gold bar, Amulet mould, Emerald"),
        // Ruby (gold)
        new Item("Cut ruby",           34, 85.0,  "Uncut ruby, Chisel"),
        new Item("Ruby ring",          34, 70.0,  "Gold bar, Ring mould, Ruby"),
        new Item("Ruby necklace",      40, 75.0,  "Gold bar, Necklace mould, Ruby"),
        new Item("Ruby bracelet",      42, 80.0,  "Gold bar, Bracelet mould, Ruby"),
        new Item("Ruby amulet",        50, 85.0,  "Gold bar, Amulet mould, Ruby"),
        // Diamond (gold)
        new Item("Cut diamond",        43, 107.5, "Uncut diamond, Chisel"),
        new Item("Diamond ring",       43, 85.0,  "Gold bar, Ring mould, Diamond"),
        new Item("Diamond necklace",   56, 90.0,  "Gold bar, Necklace mould, Diamond"),
        new Item("Diamond bracelet",   58, 95.0,  "Gold bar, Bracelet mould, Diamond"),
        new Item("Diamond amulet",     70, 100.0, "Gold bar, Amulet mould, Diamond"),
        // Dragonstone (gold)
        new Item("Cut dragonstone",    55, 137.5, "Uncut dragonstone, Chisel"),
        new Item("Dragonstone ring",   55, 100.0, "Gold bar, Ring mould, Dragonstone"),
        new Item("Dragon necklace",    72, 105.0, "Gold bar, Necklace mould, Dragonstone"),
        new Item("Dragonstone bracelet",74,110.0, "Gold bar, Bracelet mould, Dragonstone"),
        new Item("Dragonstone amulet", 80, 150.0, "Gold bar, Amulet mould, Dragonstone"),
        // Onyx (gold)
        new Item("Cut onyx",           67, 167.5, "Uncut onyx, Chisel"),
        new Item("Onyx ring",          67, 115.0, "Gold bar, Ring mould, Onyx"),
        new Item("Onyx necklace",      82, 120.0, "Gold bar, Necklace mould, Onyx"),
        new Item("Onyx bracelet",      84, 125.0, "Gold bar, Bracelet mould, Onyx"),
        new Item("Onyx amulet",        90, 165.0, "Gold bar, Amulet mould, Onyx"),
        // Zenyte (gold)
        new Item("Cut zenyte",         89, 50.0,  "Uncut zenyte, Chisel"),
        new Item("Zenyte ring",        89, 150.0, "Gold bar, Ring mould, Zenyte"),
        new Item("Zenyte necklace",    92, 165.0, "Gold bar, Necklace mould, Zenyte"),
        new Item("Zenyte bracelet",    95, 180.0, "Gold bar, Bracelet mould, Zenyte"),
        new Item("Zenyte amulet",      98, 200.0, "Gold bar, Amulet mould, Zenyte"),
        // Silver misc
        new Item("Unstrung symbol",    16, 50.0,  "Silver bar, Holy mould"),
        new Item("Tiara",              23, 52.5,  "Silver bar, Tiara mould")
    );

    // ---- Glass: glassblowing pipe on molten glass ----
    public static final List<Item> GLASS = Arrays.asList(
        new Item("Beer glass",      1,  17.5, "Molten glass, Glassblowing pipe"),
        new Item("Candle lantern",  4,  19.0, "Molten glass, Glassblowing pipe"),
        new Item("Oil lamp",        12, 25.0, "Molten glass, Glassblowing pipe"),
        new Item("Vial",            33, 35.0, "Molten glass, Glassblowing pipe"),
        new Item("Empty fishbowl",  42, 42.5, "Molten glass, Glassblowing pipe"),
        new Item("Unpowered orb",   46, 52.5, "Molten glass, Glassblowing pipe"),
        new Item("Lantern lens",    49, 55.0, "Molten glass, Glassblowing pipe"),
        new Item("Empty light orb", 87, 70.0, "Molten glass, Glassblowing pipe")
    );

    // ---- Armour/weapons: needle + thread + leather/dragonhide ----
    public static final List<Item> LEATHER = Arrays.asList(
        new Item("Leather gloves",        1,  13.8,  "Needle, Thread, Leather"),
        new Item("Leather boots",         7,  16.25, "Needle, Thread, Leather"),
        new Item("Leather cowl",          9,  18.5,  "Needle, Thread, Leather"),
        new Item("Leather vambraces",     11, 22.0,  "Needle, Thread, Leather"),
        new Item("Leather body",          14, 25.0,  "Needle, Thread, Leather"),
        new Item("Leather chaps",         18, 27.0,  "Needle, Thread, Leather"),
        new Item("Hardleather body",      28, 35.0,  "Needle, Thread, Hard leather"),
        new Item("Coif",                  38, 37.0,  "Needle, Thread, Leather"),
        new Item("Green d'hide vambraces",57, 62.0,  "Needle, Thread, Green dragon leather"),
        new Item("Green d'hide chaps",    60, 124.0, "Needle, Thread, Green dragon leather"),
        new Item("Green d'hide body",     63, 186.0, "Needle, Thread, Green dragon leather"),
        new Item("Blue d'hide vambraces", 66, 70.0,  "Needle, Thread, Blue dragon leather"),
        new Item("Blue d'hide chaps",     68, 140.0, "Needle, Thread, Blue dragon leather"),
        new Item("Blue d'hide body",      71, 210.0, "Needle, Thread, Blue dragon leather"),
        new Item("Red d'hide vambraces",  73, 78.0,  "Needle, Thread, Red dragon leather"),
        new Item("Red d'hide chaps",      75, 156.0, "Needle, Thread, Red dragon leather"),
        new Item("Red d'hide body",       77, 234.0, "Needle, Thread, Red dragon leather"),
        new Item("Black d'hide vambraces",79, 86.0,  "Needle, Thread, Black dragon leather"),
        new Item("Black d'hide chaps",    82, 172.0, "Needle, Thread, Black dragon leather"),
        new Item("Black d'hide body",     84, 258.0, "Needle, Thread, Black dragon leather")
    );

    public static Item getByLabel(List<Item> list, String label) {
        return list.stream().filter(i -> i.toString().equals(label)).findFirst().orElse(null);
    }
}
