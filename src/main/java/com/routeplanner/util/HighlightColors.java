package com.routeplanner.util;

import java.awt.Color;

/** Shared look for in-game highlights, all driven by the "NPC/Object Highlight Color" setting. */
public final class HighlightColors {

    private HighlightColors() {}

    /** The translucent fill for a highlight outlined in {@code outline} (same as object highlights). */
    public static Color fill(Color outline) {
        int a = Math.max(0, Math.min(255, (int) (outline.getAlpha() * 0.22)));
        return new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), a);
    }
}
