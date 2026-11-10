package com.routeplanner;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;

/**
 * The plugin's icon floating above a highlighted NPC or object, the way Quest Helper marks the
 * things a quest step wants you to use.
 */
public final class HighlightIcon {

    /** Drawn size of the icon, in pixels. */
    private static final int SIZE = 18;
    /** Space between the top of the highlight and the icon. */
    private static final int GAP = 4;

    private static BufferedImage source;
    private static BufferedImage scaled;

    private HighlightIcon() {}

    /** Draws the icon centred above {@code hull} (an NPC's or object's outline), if the setting is on. */
    public static void drawAbove(Graphics2D g, RoutePlannerPlugin plugin, Shape hull) {
        if (hull == null || !plugin.getConfig().highlightIcon()) return;
        BufferedImage img = sized(plugin.getRouteIcon());
        if (img == null) return;
        Rectangle b = hull.getBounds();
        g.drawImage(img, b.x + (b.width - img.getWidth()) / 2, b.y - img.getHeight() - GAP, null);
    }

    /** The icon at {@link #SIZE}, rescaled only when the plugin's icon changes (the game's globe loading in). */
    private static synchronized BufferedImage sized(BufferedImage icon) {
        if (icon == null) return null;
        if (icon == source) return scaled;
        source = icon;
        if (icon.getWidth() <= SIZE && icon.getHeight() <= SIZE) {
            scaled = icon;
            return scaled;
        }
        int w = Math.max(1, icon.getWidth() * SIZE / Math.max(icon.getWidth(), icon.getHeight()));
        int h = Math.max(1, icon.getHeight() * SIZE / Math.max(icon.getWidth(), icon.getHeight()));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(icon, 0, 0, w, h, null);
        g.dispose();
        scaled = out;
        return scaled;
    }
}
