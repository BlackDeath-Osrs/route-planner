package com.routeplanner.skilling;

import java.util.*;

public class ConstructionMethods {

    public static class Method {
        public final String name;
        public final String gear;
        public Method(String name, String gear) {
            this.name = name; this.gear = gear;
        }
        @Override public String toString() { return name; }
    }

    public static final List<Method> METHODS = Arrays.asList(
        new Method("Regular planks",  "Hammer, Saw, Plank sack, Planks"),
        new Method("Oak planks",      "Hammer, Saw, Plank sack, Oak planks"),
        new Method("Teak planks",     "Hammer, Saw, Plank sack, Teak planks"),
        new Method("Mahogany planks", "Hammer, Saw, Plank sack, Mahogany planks"),
        new Method("Mahogany Homes",  "Hammer, Saw, Plank sack, planks for contract tier, Steel bars")
    );

    public static Method getByLabel(String label) {
        return METHODS.stream().filter(m -> m.name.equals(label)).findFirst().orElse(null);
    }
}
