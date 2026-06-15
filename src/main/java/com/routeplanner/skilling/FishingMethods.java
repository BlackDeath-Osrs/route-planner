package com.routeplanner.skilling;

import java.util.*;

public class FishingMethods {

    public static class Method {
        public final String name;
        public final String gear;     // items to bring (comma separated)
        public final String catches;  // example fish, shown in the picker
        public Method(String name, String gear, String catches) {
            this.name = name; this.gear = gear; this.catches = catches;
        }
        @Override public String toString() { return name + " \u2014 " + catches; }
    }

    public static final List<Method> METHODS = Arrays.asList(
        new Method("Small net",         "Small fishing net",                      "Shrimp, Anchovies, Monkfish, Minnow"),
        new Method("Big net",           "Big fishing net",                        "Mackerel, Cod, Bass"),
        new Method("Bait",              "Fishing rod, Fishing bait",              "Sardine, Herring, Pike"),
        new Method("Fly fishing",       "Fly fishing rod, Feathers",              "Trout, Salmon"),
        new Method("Harpoon",           "Harpoon",                                "Tuna, Swordfish, Shark"),
        new Method("Lobster cage",      "Lobster pot",                            "Lobster"),
        new Method("Barbarian fishing", "Barbarian rod, Feathers / Fishing bait", "Leaping trout/salmon/sturgeon"),
        new Method("Karambwan",         "Karambwan vessel, Raw karambwanji",      "Karambwan"),
        new Method("Oily rod",          "Oily fishing rod, Fishing bait",         "Lava eel"),
        new Method("Anglerfish",        "Fishing rod, Sandworms",                 "Anglerfish"),
        new Method("Infernal eel",      "Oily fishing rod, Hammer, Ice gloves",   "Infernal eel")
    );

    public static Method getByLabel(String label) {
        return METHODS.stream().filter(m -> m.toString().equals(label)).findFirst().orElse(null);
    }
}
