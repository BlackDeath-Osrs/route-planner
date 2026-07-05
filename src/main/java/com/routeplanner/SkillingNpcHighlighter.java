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
        if (step == null) return null;

        String targetName;
        if (step.getType() == StepType.SKILLING) {
            targetName = step.getSkillingTargetNpc();
        } else if (step.getType() == StepType.NOTE) {
            targetName = step.getNpcHighlight();
        } else {
            return null;
        }
        if (targetName == null || targetName.trim().isEmpty()) return null;

        // Match NPC name - exact match first, then partial for generic names
        String npcName = npc.getName();
        if (npcName == null) return null;
        boolean nameMatch = npcName.equalsIgnoreCase(targetName)
            || npcName.toLowerCase().startsWith(targetName.toLowerCase() + " ")
            || npcName.toLowerCase().startsWith(targetName.toLowerCase() + "(");
        if (!nameMatch) return null;

        // Only highlight NPCs within 30 tiles of the player
        if (client.getLocalPlayer() == null) return null;
        int dist = npc.getWorldLocation().distanceTo(
            client.getLocalPlayer().getWorldLocation());
        if (dist > 30) return null;

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
