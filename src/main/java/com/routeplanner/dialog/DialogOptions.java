package com.routeplanner.dialog;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Reading the chat options menu ("Select an Option"). Shared by the highlight overlay and the
 * recorder so both count options the same way: option 1 is the first line under the title.
 */
public final class DialogOptions {

    private DialogOptions() {}

    /** The open options menu's container, or null when it isn't showing. */
    public static Widget container(Client client) {
        Widget w = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
        return w == null || w.isHidden() ? null : w;
    }

    /** The option lines in order (title excluded); empty when the menu isn't open. */
    public static List<Widget> options(Client client) {
        List<Widget> out = new ArrayList<>();
        Widget container = container(client);
        if (container == null) return out;
        Widget[] kids = container.getDynamicChildren();
        if (kids == null || kids.length == 0) kids = container.getChildren();
        if (kids == null) return out;
        boolean titleSkipped = false;
        for (Widget w : kids) {
            if (w == null || w.isHidden()) continue;
            String t = w.getText();
            if (t == null || t.trim().isEmpty()) continue;
            // The first line is always the menu's title ("Select an Option", or a question
            // like "Start the quest?"), never something to pick.
            if (!titleSkipped) { titleSkipped = true; continue; }
            out.add(w);
        }
        return out;
    }

    /** The option lines' text, cleaned of colour tags. */
    public static List<String> texts(Client client) {
        List<String> out = new ArrayList<>();
        for (Widget w : options(client)) out.add(clean(w.getText()));
        return out;
    }

    public static String clean(String raw) {
        return raw == null ? "" : Text.removeTags(raw).replace(' ', ' ').trim();
    }
}
