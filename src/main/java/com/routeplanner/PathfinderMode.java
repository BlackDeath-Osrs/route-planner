package com.routeplanner;

/** Which pathfinder builds the waypoint path. */
public enum PathfinderMode {
    BUILT_IN("Built-in"),
    SHORTEST_PATH("Shortest Path");

    private final String label;
    PathfinderMode(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
