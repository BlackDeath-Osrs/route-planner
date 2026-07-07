package com.routeplanner.teleport;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Highlights the teleport spell in the open spellbook for the active step,
 * when that step is a spell-teleport. Highlight only: never casts or clicks.
 * Spell is matched by its sprite id (from TeleportSpells) against spellbook widgets,
 * which is robust to spellbook child-id shifts.
 */
@Singleton
public class SpellbookOverlay extends Overlay {
    private static final int SPELLBOOK_GROUP = 218;

    private final Client client;
    private final RoutePlannerPlugin plugin;

    @Inject
    public SpellbookOverlay(Client client, RoutePlannerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null) return null;

        String spellName = step.getTeleportSpell();
        if (spellName == null || spellName.trim().isEmpty()) return null;

        TeleportSpell spell = TeleportSpells.getByName(spellName);
        if (spell == null) return null;
        int wantSprite = spell.getSpriteId();

        // spellbook must be open
        Widget book = client.getWidget(SPELLBOOK_GROUP, 0);
        if (book == null || book.isHidden()) return null;

        Widget match = findBySprite(book, wantSprite);
        if (match == null || match.isHidden()) return null;

        Rectangle b = match.getBounds();
        if (b == null) return null;

        graphics.setColor(new Color(0, 255, 255, 200));
        graphics.setStroke(new BasicStroke(2));
        graphics.drawRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2);
        graphics.setColor(new Color(0, 255, 255, 40));
        graphics.fillRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2);
        return null;
    }

    /** Depth-first search the spellbook widget tree for a child whose sprite id matches. */
    private Widget findBySprite(Widget root, int spriteId) {
        if (root == null) return null;
        if (root.getSpriteId() == spriteId && !root.isHidden()) return root;
        for (Widget child : allChildren(root)) {
            Widget found = findBySprite(child, spriteId);
            if (found != null) return found;
        }
        return null;
    }

    private Widget[] merge(Widget[] a, Widget[] b) {
        if (a == null) return b == null ? new Widget[0] : b;
        if (b == null) return a;
        Widget[] out = new Widget[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private Widget[] allChildren(Widget w) {
        Widget[] out = merge(w.getStaticChildren(), w.getDynamicChildren());
        out = merge(out, w.getNestedChildren());
        return out == null ? new Widget[0] : out;
    }
}
