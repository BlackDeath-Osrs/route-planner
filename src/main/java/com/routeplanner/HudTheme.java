package com.routeplanner;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum HudTheme {
    OSRS_BROWN("OSRS Brown"),
    QUEST_HELPER("Quest Helper style"),
    CUSTOM("Custom (use options below)");

    private final String label;

    @Override
    public String toString() {
        return label;
    }
}
