package com.routeplanner;

import lombok.RequiredArgsConstructor;

/** How the pathfinder route is drawn. */
@RequiredArgsConstructor
public enum PathDisplayMode {
    LINE("Line"),
    TILES("Tiles"),
    BOTH("Both");

    private final String label;
    @Override public String toString() { return label; }
}
