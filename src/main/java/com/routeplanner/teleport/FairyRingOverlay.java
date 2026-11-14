package com.routeplanner.teleport;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import com.routeplanner.util.HighlightColors;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;
import net.runelite.client.ui.overlay.*;
import javax.inject.*;
import java.awt.*;

/**
 * In the fairy ring interface, for a fairy ring step: lights the button that turns the next dial
 * towards the step's code, then the Teleport button once all three show it.
 */
@Singleton
public class FairyRingOverlay extends Overlay {

    private static final int[][] DIAL_BUTTONS = {
        {InterfaceID.Fairyrings._1_CLOCKWISE, InterfaceID.Fairyrings._1_ANTICLOCKWISE},
        {InterfaceID.Fairyrings._2_CLOCKWISE, InterfaceID.Fairyrings._2_ANTICLOCKWISE},
        {InterfaceID.Fairyrings._3_CLOCKWISE, InterfaceID.Fairyrings._3_ANTICLOCKWISE},
    };

    private final Client client;
    private final RoutePlannerPlugin plugin;

    @Inject
    public FairyRingOverlay(Client client, RoutePlannerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (!FairyRings.isFairyRingStep(step)) return null;
        Widget confirm = client.getWidget(InterfaceID.Fairyrings.CONFIRM);
        if (confirm == null || confirm.isHidden()) return null;

        String code = step.getTeleportNexus();
        if (FairyRings.normalize(code).equals(FairyRings.dialledCode(client))) {
            highlight(graphics, confirm);
            return null;
        }
        // A code already in the travel log sets all three dials with one click
        if (highlightLogEntry(graphics, code)) return null;
        for (int dial = 0; dial < 3; dial++) {
            int turns = FairyRings.turns(client, dial, code);
            if (turns == 0) continue;
            // One dial at a time, left to right, so there's only ever one thing to click
            highlight(graphics, client.getWidget(DIAL_BUTTONS[dial][turns > 0 ? 0 : 1]));
            return null;
        }
        highlight(graphics, confirm);
        return null;
    }

    /**
     * Highlights the code's travel log entry, or the log's scrollbar while the entry is scrolled
     * out of view. False when the log doesn't list the code (never visited, or searched away).
     */
    private boolean highlightLogEntry(Graphics2D graphics, String code) {
        int id = FairyRings.logEntry(code);
        if (id < 0) return false;
        if (highlightFavourite(graphics, code)) return true;
        Widget entry = client.getWidget(id);
        Widget list = client.getWidget(InterfaceID.FairyringsLog.CONTENTS);
        if (entry == null || entry.isHidden() || list == null || list.isHidden()) return false;
        Rectangle view = list.getBounds();
        Rectangle b = entry.getBounds();
        if (view == null || b == null || b.width <= 0) return false;
        if (view.contains(b)) {
            highlight(graphics, entry);
        } else {
            highlight(graphics, client.getWidget(InterfaceID.FairyringsLog.SCROLLBAR));
        }
        return true;
    }

    private static final int[] FAVE_ROWS = {
        InterfaceID.FairyringsLog.FAVE_1, InterfaceID.FairyringsLog.FAVE_2, InterfaceID.FairyringsLog.FAVE_3,
        InterfaceID.FairyringsLog.FAVE_4, InterfaceID.FairyringsLog.FAVE_5, InterfaceID.FairyringsLog.FAVE_6,
        InterfaceID.FairyringsLog.FAVE_7, InterfaceID.FairyringsLog.FAVE_8, InterfaceID.FairyringsLog.FAVE_9,
        InterfaceID.FairyringsLog.FAVE_10};
    private static final int[] FAVE_CODES = {
        InterfaceID.FairyringsLog.FAVE_CODE_1, InterfaceID.FairyringsLog.FAVE_CODE_2, InterfaceID.FairyringsLog.FAVE_CODE_3,
        InterfaceID.FairyringsLog.FAVE_CODE_4, InterfaceID.FairyringsLog.FAVE_CODE_5, InterfaceID.FairyringsLog.FAVE_CODE_6,
        InterfaceID.FairyringsLog.FAVE_CODE_7, InterfaceID.FairyringsLog.FAVE_CODE_8, InterfaceID.FairyringsLog.FAVE_CODE_9,
        InterfaceID.FairyringsLog.FAVE_CODE_10};

    /** A favourited code sits in its own row at the top of the travel log, not in the list below. */
    private boolean highlightFavourite(Graphics2D graphics, String code) {
        String want = FairyRings.normalize(code);
        for (int i = 0; i < FAVE_CODES.length; i++) {
            Widget codeText = client.getWidget(FAVE_CODES[i]);
            if (codeText == null || codeText.isHidden() || codeText.getText() == null) continue;
            String shown = Text.removeTags(codeText.getText()).replaceAll("[^A-Za-z]", "").toUpperCase(java.util.Locale.ROOT);
            if (!shown.startsWith(want)) continue;
            Widget row = client.getWidget(FAVE_ROWS[i]);
            highlight(graphics, row != null && !row.isHidden() && row.getWidth() > 0 ? row : codeText);
            return true;
        }
        return false;
    }

    private void highlight(Graphics2D graphics, Widget w) {
        if (w == null || w.isHidden()) return;
        Rectangle b = w.getBounds();
        if (b == null || b.width <= 0) return;
        Color c = plugin.getConfig().npcHighlightColor();
        HighlightColors.paint(graphics, b, c);
    }
}
