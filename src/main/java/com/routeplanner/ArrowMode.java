package com.routeplanner;

/** Direction-arrow placement on the waypoint line. */
public enum ArrowMode {
    NONE("None"),
    ALONG("Along line"),
    END("End only");

    private final String label;
    ArrowMode(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
