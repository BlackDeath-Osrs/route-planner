package com.routeplanner.util;

import java.util.*;

/** Herb names, so a step can say "Any herb" instead of naming the one that was grown. */
public final class Herbs {

    /** What an item list writes for any grimy or clean herb. */
    public static final String ANY_HERB = "Any herb";

    private static final Set<String> CLEAN = new HashSet<>(Arrays.asList(
        "guam leaf", "marrentill", "tarromin", "harralander", "ranarr weed", "toadflax", "irit leaf",
        "avantoe", "kwuarm", "huasca", "snapdragon", "cadantine", "lantadyme", "dwarf weed", "torstol"));

    private Herbs() {}

    /** True for a grimy or clean herb ("Grimy torstol", "Torstol"); any case. */
    public static boolean isHerb(String name) {
        if (name == null) return false;
        String n = name.trim().toLowerCase();
        if (n.startsWith("grimy ")) n = n.substring("grimy ".length());
        return CLEAN.contains(n);
    }

    public static boolean isGrimy(String name) {
        return name != null && name.trim().toLowerCase().startsWith("grimy ") && isHerb(name);
    }
}
