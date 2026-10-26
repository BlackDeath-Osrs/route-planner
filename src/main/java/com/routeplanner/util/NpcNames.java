package com.routeplanner.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Matching NPCs against a step's target text: comma-separated NPCs, each with / for alternatives,
 * e.g. "Rommik,Shop assistant/Shop keeper". Case doesn't matter, and a name that starts with a
 * target followed by a space or "(" matches too, so "Guard" also matches "Guard captain".
 */
public final class NpcNames {

    private NpcNames() {
    }

    /** The target's names, trimmed and lower-cased; parse once and reuse when matching many NPCs. */
    public static List<String> parse(String target) {
        List<String> names = new ArrayList<>();
        if (target == null) return names;
        for (String entry : target.split(",")) {
            for (String alt : entry.split("/")) {
                String t = alt.trim().toLowerCase();
                if (!t.isEmpty()) names.add(t);
            }
        }
        return names;
    }

    /** Whether an NPC's name matches one of the parsed target names. */
    public static boolean matches(List<String> targets, String npcName) {
        if (npcName == null) return false;
        String lower = npcName.toLowerCase();
        for (String t : targets) {
            if (lower.equals(t) || lower.startsWith(t + " ") || lower.startsWith(t + "(")) return true;
        }
        return false;
    }

    public static boolean matches(String target, String npcName) {
        return matches(parse(target), npcName);
    }
}
