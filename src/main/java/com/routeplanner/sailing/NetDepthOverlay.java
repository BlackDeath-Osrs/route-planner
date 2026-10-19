package com.routeplanner.sailing;

import com.routeplanner.RoutePlannerConfig;
import net.runelite.api.Client;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldEntityConfig;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;

/**
 * While trawling, lights up "Lower net" or "Raise net" in the Sailing side panel for each net in the
 * water at a different depth from the shoal (the wrong depth halves the catch). Button positions as
 * in Maewi's Deep Sea Trawling plugin.
 */
public class NetDepthOverlay extends Overlay {

    private static final int SIDEPANEL_GROUP = 937;
    private static final int FACILITIES_CHILD = 25;
    private static final int SIDEPANEL_CANVAS_CHILD = 5;
    /** Boat config ids: a skiff has one net, a sloop two. */
    private static final int SKIFF = 2, SLOOP = 3;
    /** Child indexes of the Lower and Raise buttons: a skiff's net, then a sloop's starboard and port. */
    private static final int SKIFF_LOWER = 29, SKIFF_RAISE = 30;
    private static final int STARBOARD_LOWER = 41, STARBOARD_RAISE = 42;
    private static final int PORT_LOWER = 45, PORT_RAISE = 46;

    private final Client client;
    private final RoutePlannerConfig config;

    @Inject
    public NetDepthOverlay(Client client, RoutePlannerConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        WorldEntity boat = Sailing.boat(client);
        WorldEntityConfig boatCfg = boat == null ? null : boat.getConfig();
        if (boatCfg == null || (boatCfg.getId() != SKIFF && boatCfg.getId() != SLOOP)) return null;
        int want = Shoals.depth(Shoals.nearest(client));
        if (want <= 0) return null;

        int nets = boatCfg.getId() == SLOOP ? 2 : 1;
        for (int net = 0; net < nets; net++) {
            int now = Shoals.netDepth(client, net);
            if (now <= 0 || now == want) continue;   // raised, or already right
            boolean lower = now < want;
            int child = boatCfg.getId() == SKIFF ? (lower ? SKIFF_LOWER : SKIFF_RAISE)
                : net == 0 ? (lower ? STARBOARD_LOWER : STARBOARD_RAISE)
                : (lower ? PORT_LOWER : PORT_RAISE);
            highlight(graphics, child);
        }
        return null;
    }

    private void highlight(Graphics2D g, int child) {
        Widget parent = client.getWidget(SIDEPANEL_GROUP, FACILITIES_CHILD);
        Widget canvas = client.getWidget(SIDEPANEL_GROUP, SIDEPANEL_CANVAS_CHILD);
        Widget button = parent == null ? null : parent.getChild(child);
        if (button == null || button.isHidden() || canvas == null) return;
        Rectangle b = button.getBounds();
        // Scrolled out of the panel's view: nothing to point at
        if (b == null || b.width <= 0 || b.height <= 0 || !canvas.getBounds().intersects(b)) return;
        Color c = config.npcHighlightColor();
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 110));
        g.fillRoundRect(b.x, b.y, b.width, b.height, 8, 8);
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 230));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(b.x, b.y, b.width, b.height, 8, 8);
    }
}
