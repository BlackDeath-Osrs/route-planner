package com.routeplanner.quest;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import net.runelite.api.Client;
import net.runelite.api.Quest;
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

/**
 * On a step that completes with a whole quest, highlights that quest in the open quest list,
 * where Quest Helper's right-click "Start Quest Helper" loads it. Quest Helper takes no messages
 * from other plugins, so pointing at its menu option is as far as this can go.
 * Highlight only: never clicks.
 */
@Singleton
public class QuestListOverlay extends Overlay {
    private final Client client;
    private final RoutePlannerPlugin plugin;

    @Inject
    public QuestListOverlay(Client client, RoutePlannerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null || !step.hasQuest() || !step.isQuestComplete()) return null;
        String quest = questName(step.getQuestName());
        if (quest == null) return null;

        Widget target = questRow(quest);
        if (target == null) return null;
        Rectangle b = target.getBounds();
        if (b == null) return null;

        Color outline = plugin.getConfig().npcHighlightColor();
        graphics.setColor(outline);
        graphics.setStroke(new BasicStroke(2));
        graphics.drawRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2);
        graphics.setColor(com.routeplanner.util.HighlightColors.fill(outline));
        graphics.fillRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2);
        return null;
    }

    /** The quest's name as the quest list shows it, from its Quest enum name. */
    private static String questName(String enumName) {
        try {
            return Quest.valueOf(enumName).getName();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The quest's row in the open quest list, the scrollbar if it's scrolled out of view, or null. */
    private Widget questRow(String quest) {
        Widget list = visible(InterfaceID.Questlist.LIST);
        Widget view = visible(InterfaceID.Questlist.LIST_CONTAINER);
        if (list == null || view == null) return null;
        Widget[] rows = list.getDynamicChildren();
        if (rows == null) return null;
        for (Widget row : rows) {
            if (row == null || row.isHidden() || row.getText() == null) continue;
            if (!Text.removeTags(row.getText()).trim().equalsIgnoreCase(quest)) continue;
            Rectangle r = row.getBounds();
            if (view.getBounds().contains(r.getCenterX(), r.getCenterY())) return row;
            return visible(InterfaceID.Questlist.SCROLLBAR);
        }
        return null;
    }

    private Widget visible(int componentId) {
        Widget w = client.getWidget(componentId);
        return w == null || w.isHidden() ? null : w;
    }
}
