package com.routeplanner.skilling;

import com.routeplanner.util.DataFiles;
import java.util.*;

public class ThievingNpcs {

    // List of all pickpocketable NPCs
    public static final List<SkillingNpc> PICKPOCKET_NPCS = DataFiles.load("thieving_npcs.tsv", r -> new SkillingNpc(r[0], DataFiles.num(r[1]), DataFiles.dec(r[2]), r[3]));

    // Names that should do wildcard/partial matching
    public static final Set<String> WILDCARD_NAMES = new HashSet<>(Arrays.asList(
        "Lindir", "Cirdan", "Miriel", "Caranthir", "Mithrellas",
        "Vyre", "TzHaar-Hur", "Guard", "Gnome"
    ));

    public static SkillingNpc getByName(String name) {
        return PICKPOCKET_NPCS.stream()
            .filter(n -> n.getName().equalsIgnoreCase(name))
            .findFirst().orElse(null);
    }
}
