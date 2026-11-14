package com.routeplanner.teleport;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import com.routeplanner.sailing.Sailing;
import com.routeplanner.util.HighlightColors;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.Text;
import javax.inject.*;
import java.awt.*;
import java.util.Locale;

/**
 * On a quetzal step: the whistle in the inventory (whistle steps), then the step's landing site on
 * the quetzal map, which Renu and the whistle both open. Renu himself is lit by the NPC highlighter.
 */
@Singleton
public class QuetzalOverlay extends Overlay {

    private static final int[] MAP_ICONS = {InterfaceID.QuetzalMenu.ICONS, InterfaceID.QuetzalwhistleMenu.ICONS};
    private static final int INVENTORY = InterfaceID.Inventory.ITEMS;

    private final Client client;
    private final RoutePlannerPlugin plugin;

    @Inject
    public QuetzalOverlay(Client client, RoutePlannerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (!Quetzals.isQuetzalStep(step) || Quetzals.arrived(step, Sailing.position(client))) return null;

        String site = Quetzals.site(step.getTeleportNexus()).toLowerCase(Locale.ROOT);
        for (int id : MAP_ICONS) {
            Widget icons = client.getWidget(id);
            if (icons == null || icons.isHidden()) continue;
            Widget icon = find(icons, site);
            if (icon != null) highlight(graphics, icon.getBounds());
            return null; // the map is open: the site is the only thing to click
        }

        if (Quetzals.isWhistleStep(step)) {
            Widget inv = client.getWidget(INVENTORY);
            if (inv != null && !inv.isHidden() && inv.getDynamicChildren() != null) {
                for (Widget item : inv.getDynamicChildren()) {
                    if (item != null && !item.isHidden() && Quetzals.isWhistle(item.getItemId())) {
                        highlight(graphics, item.getBounds());
                    }
                }
            }
        }
        return null;
    }

    /** The map icon named after the site (its name or one of its options), searching nested icons too. */
    private static Widget find(Widget parent, String site) {
        for (Widget[] kids : new Widget[][]{parent.getDynamicChildren(), parent.getStaticChildren(), parent.getNestedChildren()}) {
            if (kids == null) continue;
            for (Widget w : kids) {
                if (w == null || w.isHidden()) continue;
                if (names(w, site)) return w;
                Widget deeper = find(w, site);
                if (deeper != null) return deeper;
            }
        }
        return null;
    }

    private static boolean names(Widget w, String site) {
        if (w.getName() != null && Text.removeTags(w.getName()).toLowerCase(Locale.ROOT).contains(site)) return true;
        String[] actions = w.getActions();
        if (actions != null) {
            for (String a : actions) {
                if (a != null && Text.removeTags(a).toLowerCase(Locale.ROOT).contains(site)) return true;
            }
        }
        return false;
    }

    private void highlight(Graphics2D graphics, Rectangle b) {
        if (b == null || b.width <= 0) return;
        Color c = plugin.getConfig().npcHighlightColor();
        HighlightColors.paint(graphics, b, c);
    }
}
