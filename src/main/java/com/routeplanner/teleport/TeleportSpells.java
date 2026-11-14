package com.routeplanner.teleport;

import com.routeplanner.util.DataFiles;
import java.util.List;

public class TeleportSpells {

    /** The "book" of the Minigame Teleport spell's destinations (the spell is in every spellbook). */
    public static final String MINIGAME = "MINIGAME";
    private static final String MINIGAME_SUFFIX = " Minigame Teleport";

    public static final List<TeleportSpell> ALL = DataFiles.load("teleport_spells.tsv", r -> new TeleportSpell(r[0], DataFiles.point(r[1]), r[2], DataFiles.num(r[3]), r[4]));

    public static TeleportSpell getByName(String name) {
        return ALL.stream()
            .filter(t -> t.getName().equals(name))
            .findFirst().orElse(null);
    }

    /** The name the Minigame Teleport list shows, e.g. "Castle Wars"; null if not a minigame teleport. */
    public static String minigameName(TeleportSpell spell) {
        if (spell == null || !MINIGAME.equals(spell.getBook())) return null;
        String n = spell.getName();
        return n.endsWith(MINIGAME_SUFFIX) ? n.substring(0, n.length() - MINIGAME_SUFFIX.length()) : n;
    }
}
