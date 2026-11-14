package com.routeplanner;

import net.runelite.api.*;
import net.runelite.client.ui.overlay.*;
import javax.inject.*;
import java.awt.*;

/**
 * The plugin's icon above the NPCs the active step highlights. Their outlines are drawn by
 * RuneLite's NPC overlay service, which can't add an image, so the icon is drawn here.
 */
@Singleton
public class NpcIconOverlay extends Overlay {

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final SkillingNpcHighlighter highlighter;

    @Inject
    public NpcIconOverlay(Client client, RoutePlannerPlugin plugin, SkillingNpcHighlighter highlighter) {
        this.client = client;
        this.plugin = plugin;
        this.highlighter = highlighter;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null || !plugin.getConfig().highlightIcon()) return null;
        WorldView wv = client.getTopLevelWorldView();
        if (wv == null) return null;
        for (NPC npc : wv.npcs()) {
            if (npc != null && highlighter.isHighlighted(npc)) {
                HighlightIcon.drawAbove(graphics, plugin, npc.getConvexHull());
            }
        }
        return null;
    }
}
