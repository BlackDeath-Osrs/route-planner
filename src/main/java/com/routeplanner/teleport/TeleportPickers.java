package com.routeplanner.teleport;

import com.routeplanner.RoutePlannerPlugin;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

/** The teleport choosers (spell, item, Portal Nexus), used by the step editor. */
public final class TeleportPickers {

    private static final Map<Integer, BufferedImage> SPRITES = new HashMap<>();

    private TeleportPickers() {}

    /** A spell's spellbook icon at 20px; null until it loads, then {@code onLoaded} runs. */
    public static Icon spellIcon(RoutePlannerPlugin plugin, TeleportSpell sp, Runnable onLoaded) {
        Integer key = sp.getSpriteId();
        BufferedImage cached = SPRITES.get(key);
        if (cached != null) return new ImageIcon(cached.getScaledInstance(20, 20, Image.SCALE_SMOOTH));
        try {
            plugin.getSpriteManager().getSpriteAsync(sp.getSpriteId(), 0, img -> {
                if (img != null) {
                    SPRITES.put(key, img);
                    SwingUtilities.invokeLater(onLoaded);
                }
            });
        } catch (RuntimeException ignored) {
            // no icon is fine; the name still shows
        }
        return null;
    }

    /** A teleport item's inventory icon at 20px, or null. */
    public static Icon itemIcon(RoutePlannerPlugin plugin, TeleportItem it) {
        try {
            BufferedImage img = plugin.getItemManager().getImage(it.getIconId());
            return img != null ? new ImageIcon(img.getScaledInstance(20, 20, Image.SCALE_SMOOTH)) : null;
        } catch (Exception ex) {
            return null;
        }
    }

}
