package com.routeplanner;

import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.client.game.npcoverlay.HighlightedNpc;
import net.runelite.client.game.npcoverlay.NpcOverlayService;

import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.eventbus.Subscribe;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.util.function.Function;

@Singleton
public class SkillingNpcHighlighter {


    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final NpcOverlayService npcOverlayService;
    private final Function<NPC, HighlightedNpc> highlightFunction = this::shouldHighlight;

    @Inject
    public SkillingNpcHighlighter(Client client, RoutePlannerPlugin plugin,
                                   NpcOverlayService npcOverlayService) {
        this.client = client;
        this.plugin = plugin;
        this.npcOverlayService = npcOverlayService;
    }

    public void startUp() {
        npcOverlayService.registerHighlighter(highlightFunction);
    }

    public void rebuild() {
        npcOverlayService.rebuild();
    }

    public void shutDown() {
        npcOverlayService.unregisterHighlighter(highlightFunction);
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        npcOverlayService.rebuild();
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        npcOverlayService.rebuild();
    }

    private HighlightedNpc shouldHighlight(NPC npc) {
        if (plugin.getActiveRoute() == null) return null;

        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null || !step.isHighlightEnabled()) return null;

        String targetName;
        if (step.getSkillingTargetNpc() != null) {
            targetName = step.getSkillingTargetNpc();
        } else if (step.getNpcHighlight() != null) {
            targetName = step.getNpcHighlight();
        } else {
            return null;
        }
        if (targetName == null || targetName.trim().isEmpty()) return null;

        // Support multiple NPCs: comma-separated, with / for alternatives
        // e.g. "Rommik,Shop assistant/Shop keeper"
        String npcName = npc.getName();
        if (npcName == null) return null;
        boolean nameMatch = false;
        outer:
        for (String entry : targetName.split(",")) {
            for (String alt : entry.split("/")) {
                String t = alt.trim();
                if (npcName.equalsIgnoreCase(t)
                    || npcName.toLowerCase().startsWith(t.toLowerCase() + " ")
                    || npcName.toLowerCase().startsWith(t.toLowerCase() + "(")) {
                    nameMatch = true;
                    break outer;
                }
            }
        }
        if (!nameMatch) return null;

        // Only highlight NPCs within 30 tiles of the player; at sea, measured from the boat and out
        // as far as the client draws (sea creatures are further apart)
        net.runelite.api.coords.WorldPoint at = com.routeplanner.sailing.Sailing.position(client);
        if (at == null) return null;
        int range = 30;
        if (com.routeplanner.sailing.Sailing.isSailing(client) && client.getTopLevelWorldView() != null) {
            range = Math.max(range, client.getTopLevelWorldView().getScene().getDrawDistance());
        }
        if (npc.getWorldLocation().distanceTo(at) > range) return null;

        return HighlightedNpc.builder()
            .npc(npc)
            .highlightColor(plugin.getConfig().npcHighlightColor())
            .outline(true)
            .hull(false)
            .tile(false)
            .nameOnMinimap(true)
            .build();
    }
}
