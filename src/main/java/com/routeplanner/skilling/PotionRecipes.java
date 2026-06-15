package com.routeplanner.skilling;

import java.util.*;

public class PotionRecipes {

    public static class Potion {
        public final String name;
        public final int level;
        public final double xp;
        public final String herbGrimy;
        public final String herbClean;
        public final String unfinished;
        public final String secondary;
        public Potion(String name, int level, double xp, String herbGrimy,
                      String herbClean, String unfinished, String secondary) {
            this.name = name; this.level = level; this.xp = xp;
            this.herbGrimy = herbGrimy; this.herbClean = herbClean;
            this.unfinished = unfinished; this.secondary = secondary;
        }
        @Override public String toString() { return name + " (Lv " + level + ")"; }
    }

    public static final List<Potion> POTIONS = Arrays.asList(
        new Potion("Attack potion",     3,  25.0,  "Grimy guam leaf",   "Guam leaf",   "Guam potion (unf)",       "Eye of newt"),
        new Potion("Antipoison",        5,  37.5,  "Grimy marrentill",  "Marrentill",  "Marrentill potion (unf)", "Unicorn horn dust"),
        new Potion("Strength potion",   12, 50.0,  "Grimy tarromin",    "Tarromin",    "Tarromin potion (unf)",   "Limpwurt root"),
        new Potion("Restore potion",    22, 62.5,  "Grimy harralander", "Harralander", "Harralander potion (unf)","Red spiders' eggs"),
        new Potion("Energy potion",     26, 67.5,  "Grimy harralander", "Harralander", "Harralander potion (unf)","Chocolate dust"),
        new Potion("Defence potion",    30, 75.0,  "Grimy ranarr weed", "Ranarr weed", "Ranarr potion (unf)",     "White berries"),
        new Potion("Agility potion",    34, 80.0,  "Grimy toadflax",    "Toadflax",    "Toadflax potion (unf)",   "Toad's legs"),
        new Potion("Combat potion",     36, 84.0,  "Grimy harralander", "Harralander", "Harralander potion (unf)","Goat horn dust"),
        new Potion("Prayer potion",     38, 87.5,  "Grimy ranarr weed", "Ranarr weed", "Ranarr potion (unf)",     "Snape grass"),
        new Potion("Super attack",      45, 100.0, "Grimy irit leaf",   "Irit leaf",   "Irit potion (unf)",       "Eye of newt"),
        new Potion("Super antipoison",  48, 106.5, "Grimy irit leaf",   "Irit leaf",   "Irit potion (unf)",       "Unicorn horn dust"),
        new Potion("Fishing potion",    50, 112.5, "Grimy avantoe",     "Avantoe",     "Avantoe potion (unf)",    "Snape grass"),
        new Potion("Super energy",      52, 117.5, "Grimy avantoe",     "Avantoe",     "Avantoe potion (unf)",    "Mort myre fungus"),
        new Potion("Hunter potion",     53, 120.0, "Grimy avantoe",     "Avantoe",     "Avantoe potion (unf)",    "Kebbit teeth dust"),
        new Potion("Super strength",    55, 125.0, "Grimy kwuarm",      "Kwuarm",      "Kwuarm potion (unf)",     "Limpwurt root"),
        new Potion("Super restore",     63, 142.5, "Grimy snapdragon",  "Snapdragon",  "Snapdragon potion (unf)", "Red spiders' eggs"),
        new Potion("Super defence",     66, 150.0, "Grimy cadantine",   "Cadantine",   "Cadantine potion (unf)",  "White berries"),
        new Potion("Antifire potion",   69, 157.5, "Grimy lantadyme",   "Lantadyme",   "Lantadyme potion (unf)",  "Dragon scale dust"),
        new Potion("Ranging potion",    72, 162.5, "Grimy dwarf weed",  "Dwarf weed",  "Dwarf weed potion (unf)", "Wine of zamorak"),
        new Potion("Magic potion",      76, 172.5, "Grimy lantadyme",   "Lantadyme",   "Lantadyme potion (unf)",  "Potato cactus"),
        new Potion("Zamorak brew",      78, 175.0, "Grimy torstol",     "Torstol",     "Torstol potion (unf)",    "Jangerberries")
    );

    public static Potion getByLabel(String label) {
        return POTIONS.stream().filter(p -> p.toString().equals(label)).findFirst().orElse(null);
    }

    public static Potion getByName(String name) {
        return POTIONS.stream().filter(p -> p.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
