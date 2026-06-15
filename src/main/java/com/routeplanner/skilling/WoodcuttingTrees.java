package com.routeplanner.skilling;

import java.util.*;

public class WoodcuttingTrees {

    public static class Tree {
        public final String name;        // display
        public final int level;
        public final double xp;
        public final String objectName;  // in-game object name to highlight
        public Tree(String name, int level, double xp, String objectName) {
            this.name = name; this.level = level; this.xp = xp;
            this.objectName = objectName;
        }
        @Override public String toString() { return name + " (Lv " + level + ")"; }
    }

    public static final List<Tree> TREES = Arrays.asList(
        new Tree("Tree",      1,  25.0,  "Tree"),
        new Tree("Oak",       15, 37.5,  "Oak"),
        new Tree("Willow",    30, 67.5,  "Willow"),
        new Tree("Teak",      35, 85.0,  "Teak"),
        new Tree("Maple",     45, 100.0, "Maple"),
        new Tree("Mahogany",  50, 125.0, "Mahogany"),
        new Tree("Yew",       60, 175.0, "Yew"),
        new Tree("Magic",     75, 250.0, "Magic"),
        new Tree("Redwood",   90, 380.0, "Redwood")
    );

    public static Tree getByLabel(String label) {
        return TREES.stream().filter(t -> t.toString().equals(label)).findFirst().orElse(null);
    }
}
