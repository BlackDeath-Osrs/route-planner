package com.routeplanner;

import lombok.RequiredArgsConstructor;

/** What plays when every wreck by the boat has sunk (Sailing). */
@RequiredArgsConstructor
public enum WreckAlert {
    BOSUNS_CALL("Bosun's call"),
    SYSTEM("System sound"),
    RUNELITE("RuneLite notification"),
    OFF("Off");

    private final String label;

    @Override
    public String toString() {
        return label;
    }
}
