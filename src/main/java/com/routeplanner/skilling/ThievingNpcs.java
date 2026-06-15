package com.routeplanner.skilling;

import java.util.*;

public class ThievingNpcs {

    // List of all pickpocketable NPCs
    public static final List<SkillingNpc> PICKPOCKET_NPCS = Arrays.asList(
        new SkillingNpc("Man",                    1,   8.0,   "Common in most cities"),
        new SkillingNpc("Woman",                  1,   8.0,   "Common in most cities"),
        new SkillingNpc("Farmer",                 10,  14.5,  "Draynor area"),
        new SkillingNpc("Al-Kharid Warrior",      25,  26.0,  "Al Kharid palace"),
        new SkillingNpc("Rogue",                  32,  35.5,  "Ice Mountain dungeon"),
        new SkillingNpc("Cave Goblin",            36,  40.0,  "Dorgesh-Kaan"),
        new SkillingNpc("Master Farmer",          38,  43.0,  "Draynor Village, Varrock"),
        new SkillingNpc("Guard",                  40,  46.5,  "Ardougne, Falador"),
        new SkillingNpc("Fremennik Citizen",      45,  65.0,  "Rellekka"),
        new SkillingNpc("Bearded Pollnivnian Bandit", 45, 65.0, "Pollnivneach"),
        new SkillingNpc("Wealthy Citizen",        50,  80.0,  "Civitas illa Fortis"),
        new SkillingNpc("Desert Bandit",          53,  79.5,  "Kharidian Desert"),
        new SkillingNpc("Knight of Ardougne",     55,  84.3,  "East Ardougne"),
        new SkillingNpc("Pollnivnian Bandit",     55,  84.3,  "Pollnivneach"),
        new SkillingNpc("Watchman",               65,  137.5, "Watchtower area"),
        new SkillingNpc("Paladin",                70,  151.75,"East Ardougne"),
        new SkillingNpc("Gnome",                  75,  198.5, "Tree Gnome Stronghold"),
        new SkillingNpc("Hero",                   80,  163.75,"East Ardougne"),
        new SkillingNpc("Vyre",                   82,  306.9, "Darkmeyer"),
        // Prifddinas elves - individual names
        new SkillingNpc("Lindir",                 85,  353.3, "Prifddinas - near POH portal"),
        new SkillingNpc("Cirdan",                 85,  353.3, "Prifddinas - south-east bank"),
        new SkillingNpc("Miriel",                 85,  353.3, "Prifddinas - Hefin Inn"),
        new SkillingNpc("Caranthir",              85,  353.3, "Prifddinas - Cadarn teleport"),
        new SkillingNpc("Mithrellas",             85,  353.3, "Prifddinas - west of POH"),
        new SkillingNpc("TzHaar-Hur",             90,  103.4, "TzHaar City")
    );

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
