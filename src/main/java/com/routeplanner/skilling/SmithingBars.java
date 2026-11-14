package com.routeplanner.skilling;

import lombok.RequiredArgsConstructor;
import com.routeplanner.util.DataFiles;
import java.util.*;

public class SmithingBars {

    @RequiredArgsConstructor
    public static class Bar {
        public final String name;
        public final int level;       // smelt level
        public final double smeltXp;  // XP per bar smelted
        public final double smithXp;  // XP per bar smithed into items (0 = not anvil-smithed)
        public final String ores;     // ores to highlight when smelting
        @Override public String toString() { return name + " (Lv " + level + ")"; }
    }

    public static final List<Bar> BARS = DataFiles.load("smithing.tsv", r -> new Bar(r[0], DataFiles.num(r[1]), DataFiles.dec(r[2]), DataFiles.dec(r[3]), r[4]));

    public static Bar getByLabel(String label) {
        return BARS.stream().filter(b -> b.toString().equals(label)).findFirst().orElse(null);
    }
}
