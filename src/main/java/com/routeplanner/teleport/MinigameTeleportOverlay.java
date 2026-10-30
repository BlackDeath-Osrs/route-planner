package com.routeplanner.teleport;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import com.routeplanner.util.HighlightColors;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks the player through a minigame teleport, one click at a time: the magic tab, the
 * Minigame Teleport spell (in every spellbook), then the minigame in the list it opens.
 * Highlight only: never clicks.
 */
@Singleton
public class MinigameTeleportOverlay extends Overlay {
    /** The magic tab's stone in each window layout (fixed, resizable classic, resizable modern, OSM). */
    private static final int[] MAGIC_TAB_STONES = {
        InterfaceID.Toplevel.STONE6, InterfaceID.ToplevelOsrsStretch.STONE6,
        InterfaceID.ToplevelPreEoc.STONE6, InterfaceID.ToplevelOsm.STONE6,
    };
    /** The Minigame Teleport spell, one per spellbook. */
    private static final int[] MINIGAME_SPELLS = {
        InterfaceID.MagicSpellbook.TELEPORT_MINIGAME_STANDARD, InterfaceID.MagicSpellbook.TELEPORT_MINIGAME_ANCIENT,
        InterfaceID.MagicSpellbook.TELEPORT_MINIGAME_LUNAR, InterfaceID.MagicSpellbook.TELEPORT_MINIGAME_ARCEUUS,
    };

    private final Client client;
    private final RoutePlannerPlugin plugin;

    @Inject
    public MinigameTeleportOverlay(Client client, RoutePlannerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null || step.getTeleportSpell() == null) return null;
        TeleportSpell spell = TeleportSpells.getByName(step.getTeleportSpell());
        String game = TeleportSpells.minigameName(spell);
        if (game == null || alreadyThere(step, spell)) return null;

        Widget target = nextClick(game);
        if (target == null) return null;
        Rectangle b = target.getBounds();
        if (b == null) return null;

        Color outline = plugin.getConfig().npcHighlightColor();
        graphics.setColor(outline);
        graphics.setStroke(new BasicStroke(2));
        graphics.drawRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2);
        graphics.setColor(HighlightColors.fill(outline));
        graphics.fillRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2);
        return null;
    }

    /** The widget the player should click next, or null if there is nothing to show. */
    private Widget nextClick(String game) {
        Widget list = visible(InterfaceID.Minigames.MINIGAMES);
        if (list != null) {
            Widget row = findRow(list, game);
            if (row == null) return null;   // every row exists even scrolled away, so it isn't unlocked
            // Scrolled out of view (its middle isn't in the list): point at the scrollbar instead.
            Rectangle r = row.getBounds();
            if (list.getBounds().contains(r.getCenterX(), r.getCenterY())) return row;
            return visible(InterfaceID.Minigames.SCROLLBAR);
        }
        for (int id : MINIGAME_SPELLS) {
            Widget spell = visible(id);
            if (spell != null) return spell;
        }
        for (int id : MAGIC_TAB_STONES) {
            Widget stone = visible(id);
            if (stone != null) return stone;
        }
        return null;
    }

    /**
     * Whether the teleport has been taken, or isn't needed: the player is by the landing spot, or
     * already nearer the step's tile than the teleport would put them (e.g. walking from Nightmare
     * Zone to the Dragon Inn).
     */
    private boolean alreadyThere(RouteStep step, TeleportSpell spell) {
        WorldPoint dest = spell.getDestination();
        WorldPoint goal = step.getWorldPoint() != null ? step.getWorldPoint() : dest;
        if (client.getLocalPlayer() == null || goal == null) return false;
        WorldPoint at = client.getLocalPlayer().getWorldLocation();
        if (at.getPlane() == dest.getPlane() && at.distanceTo2D(dest) <= LANDING) return true;
        // A goal through a door or stairs (inside the Blast Furnace) sits thousands of tiles off on
        // the map, which would make anywhere "nearer" than the landing; so only count it close by.
        int fromGoal = at.distanceTo2D(goal);
        return at.getPlane() == goal.getPlane() && fromGoal <= dest.distanceTo2D(goal) + NEAR && fromGoal <= MAX_WALK;
    }

    /** Tiles of slack around the teleport's landing spot. */
    private static final int NEAR = 15;
    /** Around the landing spot, the teleport counts as taken (walking on to the stairs, say). */
    private static final int LANDING = 30;
    /** Further from the step's tile than this, it's never "already there". */
    private static final int MAX_WALK = 300;

    private Widget visible(int componentId) {
        Widget w = client.getWidget(componentId);
        return w == null || w.isHidden() ? null : w;
    }

    private static boolean sameGame(String text, String game) {
        if (text == null) return false;
        // Rows read "Castle Wars<br><col=808080>West of Yanille": the name is the first line
        int br = text.indexOf("<br>");
        String t = Text.removeTags(br < 0 ? text : text.substring(0, br)).trim().replaceFirst("^\\w\\s*[:)]\\s*", "").trim();
        return t.equalsIgnoreCase(game);
    }

    /** The list row (a direct child of the list) holding the minigame's name. */
    private static Widget findRow(Widget list, String game) {
        for (Widget row : children(list)) {
            if (!row.isHidden() && mentions(row, game)) return row;
        }
        return null;
    }

    /** Whether this widget or anything under it shows the minigame's name (as text or as its name). */
    private static boolean mentions(Widget w, String game) {
        if (sameGame(w.getText(), game) || sameGame(w.getName(), game)) return true;
        for (Widget child : children(w)) {
            if (mentions(child, game)) return true;
        }
        return false;
    }

    private static List<Widget> children(Widget w) {
        List<Widget> out = new ArrayList<>();
        for (Widget[] kids : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()}) {
            if (kids == null) continue;
            for (Widget k : kids) if (k != null) out.add(k);
        }
        return out;
    }
}
