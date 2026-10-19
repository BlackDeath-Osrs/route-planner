package com.routeplanner;

/** What plays when every wreck by the boat has sunk (Sailing). */
public enum WreckAlert {
    BOSUNS_CALL("Bosun's call"),
    SYSTEM("System sound"),
    RUNELITE("RuneLite notification"),
    OFF("Off");

    private final String label;

    WreckAlert(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
