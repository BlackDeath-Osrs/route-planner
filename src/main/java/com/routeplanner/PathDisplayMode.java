package com.routeplanner;

/** How the pathfinder route is drawn. */
public enum PathDisplayMode {
    LINE("Line"),
    TILES("Tiles"),
    BOTH("Both");

    private final String label;
    PathDisplayMode(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
