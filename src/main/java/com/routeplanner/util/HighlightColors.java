package com.routeplanner.util;

import net.runelite.client.util.ColorUtil;
import java.awt.*;

/** Shared look for in-game highlights, all driven by the "NPC/Object Highlight Color" setting. */
public final class HighlightColors {

    private HighlightColors() {}

    /** The translucent fill for a highlight outlined in {@code outline} (same as object highlights). */
    public static Color fill(Color outline) {
        int a = Math.max(0, Math.min(255, (int) (outline.getAlpha() * 0.22)));
        return ColorUtil.colorWithAlpha(outline, a);
    }

    /** Fills a shape with the translucent fill and outlines it. */
    public static void paint(Graphics2D graphics, Shape shape, Color outline) {
        graphics.setColor(fill(outline));
        graphics.fill(shape);
        graphics.setColor(outline);
        graphics.setStroke(new BasicStroke(2));
        graphics.draw(shape);
    }

    /** Outlines an inventory slot, then washes it with the translucent fill. */
    public static void paintSlot(Graphics2D graphics, Rectangle b, Color outline) {
        if (b == null) return;
        graphics.setColor(outline);
        graphics.setStroke(new BasicStroke(2));
        graphics.drawRect(b.x, b.y, b.width, b.height);
        graphics.setColor(fill(outline));
        graphics.fillRect(b.x, b.y, b.width, b.height);
    }

    /** Text made safe to put inside a Swing HTML label. */
    public static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
