package com.routeplanner.skilling;

import com.routeplanner.util.DataFiles;
import java.util.*;

public class ThievingStalls {

    // Stall name must match the in-game object name (full stall, not the looted variant)
    public static final List<SkillingNpc> STALLS = DataFiles.load("thieving_stalls.tsv", r -> new SkillingNpc(r[0], DataFiles.num(r[1]), DataFiles.dec(r[2]), r[3]));

    public static SkillingNpc getByName(String name) {
        return STALLS.stream()
            .filter(s -> s.getName().equalsIgnoreCase(name))
            .findFirst().orElse(null);
    }
}
