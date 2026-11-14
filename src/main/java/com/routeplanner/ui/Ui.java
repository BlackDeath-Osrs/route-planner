package com.routeplanner.ui;

import net.runelite.client.ui.FontManager;

import javax.swing.*;
import java.awt.*;

/** Shorthands for the side panel's and step editor's usual component styling; each returns the component. */
public final class Ui {

    private Ui() {}

    /** Not painting its own background, so the parent's shows through. */
    public static <T extends JComponent> T bare(T c) {
        c.setOpaque(false);
        return c;
    }

    /** Lined up on the left edge of a vertical box. */
    public static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    public static <T extends JComponent> T bareLeft(T c) {
        return left(bare(c));
    }

    /** RuneScape small font in this colour. */
    public static <T extends JComponent> T smallText(T c, Color fg) {
        c.setFont(FontManager.getRunescapeSmallFont());
        c.setForeground(fg);
        return c;
    }

    /** RuneScape bold font in this colour. */
    public static <T extends JComponent> T boldText(T c, Color fg) {
        c.setFont(FontManager.getRunescapeBoldFont());
        c.setForeground(fg);
        return c;
    }
}
