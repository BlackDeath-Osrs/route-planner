package com.routeplanner;

public enum HudTheme {
    OSRS_BROWN("OSRS Brown"),
    QUEST_HELPER("Quest Helper style"),
    CUSTOM("Custom (use options below)");

    private final String label;

    HudTheme(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
