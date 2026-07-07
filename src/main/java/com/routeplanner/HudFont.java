package com.routeplanner;

import java.awt.Font;
import net.runelite.client.ui.FontManager;

/** Font family choices for the HUD. RS fonts come from RuneLite's FontManager; others are system fonts. */
public enum HudFont {
    RUNESCAPE("RuneScape"),
    RUNESCAPE_BOLD("RuneScape Bold"),
    RUNESCAPE_SMALL("RuneScape Small"),
    ARIAL("Arial"),
    SANS_SERIF("Sans Serif"),
    SERIF("Serif"),
    MONOSPACED("Monospaced");

    private final String label;

    HudFont(String label) {
        this.label = label;
    }

    /** Resolve to an actual Font at the given size/style. RS fonts ignore size/style (fixed bitmap fonts). */
    public Font toFont(int size, int style) {
        switch (this) {
            case RUNESCAPE:       return FontManager.getRunescapeFont();
            case RUNESCAPE_BOLD:  return FontManager.getRunescapeBoldFont();
            case RUNESCAPE_SMALL: return FontManager.getRunescapeSmallFont();
            case ARIAL:           return new Font("Arial", style, size);
            case SANS_SERIF:      return new Font(Font.SANS_SERIF, style, size);
            case SERIF:           return new Font(Font.SERIF, style, size);
            case MONOSPACED:      return new Font(Font.MONOSPACED, style, size);
            default:              return new Font("Arial", style, size);
        }
    }

    @Override
    public String toString() {
        return label;
    }
}
