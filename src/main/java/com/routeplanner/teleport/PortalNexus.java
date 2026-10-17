package com.routeplanner.teleport;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

import java.awt.Rectangle;
import java.util.function.Consumer;

/**
 * Reading the Portal Nexus teleport menu. Each row is a clickable background with its text
 * drawn by a separate widget on the same line, so rows are matched up by position rather than
 * by relying on the menu's internal layout.
 */
public final class PortalNexus {

    private PortalNexus() {}

    public static final int MENU_GROUP = InterfaceID.TelenexusTeleport.UNIVERSE >>> 16;

    /** Menu text looks like "<col=..>1</col> : Varrock"; keep just the destination name. */
    public static String cleanText(String raw) {
        if (raw == null) return null;
        String t = Text.removeTags(raw).trim().replaceFirst("^\\w\\s*[:)]\\s*", "").trim();
        return t.isEmpty() ? null : t;
    }

    /** The destination named on the same line as a clicked menu row, or null. */
    public static String rowText(Client client, Widget clicked) {
        if (clicked == null) return null;
        String own = cleanText(clicked.getText());
        if (own != null) return own;
        Rectangle row = clicked.getBounds();
        if (row == null || row.height <= 0) return null;
        String[] best = {null};
        forEachText(client, w -> {
            Rectangle b = w.getBounds();
            int centerY = b.y + b.height / 2;
            if (centerY < row.y || centerY > row.y + row.height) return;
            String t = cleanText(w.getText());
            // A row can also show its hotkey on its own; the longest text is the name.
            if (t != null && (best[0] == null || t.length() > best[0].length())) best[0] = t;
        });
        return best[0];
    }

    /** Every visible widget with text in the open nexus menu (nothing if it's closed). */
    public static void forEachText(Client client, Consumer<Widget> action) {
        Widget root = client.getWidget(InterfaceID.TelenexusTeleport.UNIVERSE);
        if (root != null && !root.isHidden()) visit(root, action);
    }

    private static void visit(Widget w, Consumer<Widget> action) {
        if (w == null || w.isHidden()) return;
        String text = w.getText();
        if (text != null && !text.isEmpty()) action.accept(w);
        for (Widget[] kids : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()}) {
            if (kids == null) continue;
            for (Widget k : kids) visit(k, action);
        }
    }
}
