package com.routeplanner;

import lombok.RequiredArgsConstructor;

/** Which pathfinder builds the waypoint path. */
@RequiredArgsConstructor
public enum PathfinderMode {
    BUILT_IN("Built-in"),
    SHORTEST_PATH("Shortest Path");

    private final String label;
    @Override public String toString() { return label; }
}
