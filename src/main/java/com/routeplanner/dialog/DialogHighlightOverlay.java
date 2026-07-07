package com.routeplanner.dialog;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Highlights the chat dialogue option to press for a NOTE step that defines a
 * dialogue sequence (e.g. "3,1"). Reads the standard option widget (group 219).
 */
@Singleton
public class DialogHighlightOverlay extends Overlay {

    private static final int DIALOG_OPTION_GROUP = 219;
    private static final int DIALOG_OPTION_CHILD = 1;

    private final Client client;
    private final RoutePlannerPlugin plugin;

    @Inject
    public DialogHighlightOverlay(Client client, RoutePlannerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null || step.getDialogOptions() == null
            || step.getDialogOptions().trim().isEmpty()) return null;
        String seqStr = step.getDialogOptions();
        if (seqStr == null || seqStr.trim().isEmpty()) return null;

        String[] parts = seqStr.split(",");
        int progress = plugin.getDialogProgress();
        if (progress < 0 || progress >= parts.length) return null;
        int targetOption;
        try {
            targetOption = Integer.parseInt(parts[progress].trim());
        } catch (NumberFormatException e) {
            return null;
        }

        Widget container = client.getWidget(DIALOG_OPTION_GROUP, DIALOG_OPTION_CHILD);
        if (container == null || container.isHidden()) return null;

        List<Widget> options = new ArrayList<>();
        Widget[] kids = container.getDynamicChildren();
        if (kids == null || kids.length == 0) kids = container.getChildren();
        if (kids != null) {
            for (Widget w : kids) {
                if (w == null) continue;
                String t = w.getText();
                if (t == null) continue;
                t = t.trim();
                if (t.isEmpty()) continue;
                if (t.equalsIgnoreCase("Select an Option")) continue;
                options.add(w);
            }
        }
        if (targetOption < 1 || targetOption > options.size()) return null;

        Rectangle b = options.get(targetOption - 1).getBounds();
        if (b == null || b.width <= 0 || b.height <= 0) return null;

        graphics.setColor(new Color(255, 140, 0, 60));
        graphics.fillRect(b.x - 2, b.y - 1, b.width + 4, b.height + 2);
        graphics.setColor(new Color(255, 140, 0, 235));
        graphics.setStroke(new BasicStroke(2));
        graphics.drawRect(b.x - 2, b.y - 1, b.width + 4, b.height + 2);
        return null;
    }
}
