package com.routeplanner.skilling;

import lombok.RequiredArgsConstructor;
import com.routeplanner.util.DataFiles;
import java.util.*;

public class PotionRecipes {

    @RequiredArgsConstructor
    public static class Potion {
        public final String name;
        public final int level;
        public final double xp;
        public final String herbGrimy;
        public final String herbClean;
        public final String unfinished;
        public final String secondary;
        @Override public String toString() { return name + " (Lv " + level + ")"; }
    }

    public static final List<Potion> POTIONS = DataFiles.load("potions.tsv", r -> new Potion(r[0], DataFiles.num(r[1]), DataFiles.dec(r[2]), r[3], r[4], r[5], r[6]));

    public static Potion getByLabel(String label) {
        return POTIONS.stream().filter(p -> p.toString().equals(label)).findFirst().orElse(null);
    }

    public static Potion getByName(String name) {
        return POTIONS.stream().filter(p -> p.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
