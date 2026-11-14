package com.routeplanner.skilling;

import lombok.RequiredArgsConstructor;
import com.routeplanner.util.DataFiles;
import java.util.*;

/**
 * The step editor's recipes for the skills that need only a name, level, XP and one target:
 * the bank items to take out (comma separated), or for gathering skills the object to click.
 */
public final class Recipes {

    @RequiredArgsConstructor
    public static final class Recipe {
        public final String name;
        public final int level;
        public final double xp;
        public final String target;

        @Override
        public String toString() {
            return name + " (Lv " + level + ")";
        }
    }

    public static final List<Recipe> COOKING = load("cooking.tsv");
    public static final List<Recipe> FIREMAKING = load("firemaking.tsv");
    public static final List<Recipe> MINING = load("mining.tsv");
    public static final List<Recipe> WOODCUTTING = load("woodcutting.tsv");
    public static final List<Recipe> CRAFTING = load("crafting_jewellery.tsv", "crafting_glass.tsv", "crafting_leather.tsv");
    public static final List<Recipe> FLETCHING = load("fletching_ammo.tsv", "fletching_bows.tsv");

    private Recipes() {}

    private static List<Recipe> load(String... files) {
        List<Recipe> out = new ArrayList<>();
        for (String f : files) {
            out.addAll(DataFiles.load(f, r -> new Recipe(r[0], DataFiles.num(r[1]), DataFiles.dec(r[2]), r[3])));
        }
        return Collections.unmodifiableList(out);
    }

    /** The recipe a picker label ("Oak (Lv 15)") names, or null. */
    public static Recipe byLabel(List<Recipe> list, String label) {
        for (Recipe r : list) if (r.toString().equals(label)) return r;
        return null;
    }
}
