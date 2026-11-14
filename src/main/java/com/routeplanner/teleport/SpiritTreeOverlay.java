package com.routeplanner.teleport;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import com.routeplanner.util.HighlightColors;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.*;
import javax.inject.*;
import java.awt.*;

/**
 * In a spirit tree's destination menu, for a spirit tree step: highlights the step's destination
 * ("4: Grand Exchange"). The trees use the game's general list menu, in its old or new form.
 */
@Singleton
public class SpiritTreeOverlay extends Overlay {

    /** The list menu's roots: the older one and the newer one. */
    private static final int[] MENUS = {InterfaceID.Menu.LJ_LAYER2, InterfaceID.MenuNew.INFINITE};

    private final Client client;
    private final RoutePlannerPlugin plugin;

    @Inject
    public SpiritTreeOverlay(Client client, RoutePlannerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (!SpiritTrees.isSpiritTreeStep(step)) return null;
        String want = SpiritTrees.destinationName(step.getTeleportNexus());
        for (int id : MENUS) {
            Widget root = client.getWidget(id);
            if (root == null || root.isHidden()) continue;
            Widget row = find(root, want);
            if (row != null) {
                highlight(graphics, row.getBounds());
                return null;
            }
        }
        return null;
    }

    /** The menu line naming the destination, its key ("4:") and colour tags aside. */
    private static Widget find(Widget w, String want) {
        if (w == null || w.isHidden()) return null;
        String text = PortalNexus.cleanText(w.getText());
        if (text != null && text.equalsIgnoreCase(want)) return w;
        for (Widget[] kids : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()}) {
            if (kids == null) continue;
            for (Widget k : kids) {
                Widget found = find(k, want);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void highlight(Graphics2D graphics, Rectangle b) {
        if (b == null || b.width <= 0) return;
        Color c = plugin.getConfig().npcHighlightColor();
        HighlightColors.paint(graphics, b, c);
    }
}
