package com.routeplanner;

public enum RouteMode {
    DEVELOPER("Developer (create routes)"),
    PLAYER("Player (import & play)");

    private final String label;

    RouteMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
