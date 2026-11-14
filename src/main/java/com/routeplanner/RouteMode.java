package com.routeplanner;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum RouteMode {
    DEVELOPER("Developer (create routes)"),
    PLAYER("Player (import & play)");

    private final String label;

    @Override
    public String toString() {
        return label;
    }
}
