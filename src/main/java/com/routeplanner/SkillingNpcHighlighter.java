package com.routeplanner;

import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import com.routeplanner.sailing.Sailing;
import com.routeplanner.util.NpcNames;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.npcoverlay.HighlightedNpc;
import net.runelite.client.game.npcoverlay.NpcOverlayService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.util.Collections;
import java.util.List;
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

    // No NpcSpawned/NpcDespawned handlers: NpcOverlayService checks new NPCs itself, and a full
    // rebuild on every spawn re-ran shouldHighlight for every NPC in the scene.

    /** The target names, lower-cased, parsed once per target string instead of once per NPC. */
    private String parsedTarget;
    private List<String> targetNames = Collections.emptyList();

    private List<String> targetNames(String target) {
        if (!target.equals(parsedTarget)) {
            targetNames = NpcNames.parse(target);
            parsedTarget = target;
        }
        return targetNames;
    }

    private HighlightedNpc shouldHighlight(NPC npc) {
        if (!isHighlighted(npc)) return null;
        return HighlightedNpc.builder()
            .npc(npc)
            .highlightColor(plugin.getConfig().npcHighlightColor())
            .outline(true)
            .hull(false)
            .tile(false)
            .nameOnMinimap(true)
            .build();
    }

    /** Whether the active step highlights this NPC. */
    public boolean isHighlighted(NPC npc) {
        if (plugin.getActiveRoute() == null) return false;

        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null || !step.isHighlightEnabled()) return false;

        String targetName;
        if (step.getSkillingTargetNpc() != null) {
            targetName = step.getSkillingTargetNpc();
        } else if (step.getNpcHighlight() != null) {
            targetName = step.getNpcHighlight();
        } else {
            return false;
        }
        if (targetName == null || targetName.trim().isEmpty()) return false;

        // Support multiple NPCs: comma-separated, with / for alternatives
        // e.g. "Rommik,Shop assistant/Shop keeper"
        if (!NpcNames.matches(targetNames(targetName), npc.getName())) return false;

        // Only highlight NPCs within 30 tiles of the player; at sea, measured from the boat and out
        // as far as the client draws (sea creatures are further apart)
        WorldPoint at = Sailing.position(client);
        if (at == null) return false;
        int range = 30;
        if (Sailing.isSailing(client) && client.getTopLevelWorldView() != null) {
            range = Math.max(range, client.getTopLevelWorldView().getScene().getDrawDistance());
        }
        if (npc.getWorldLocation().distanceTo(at) > range) return false;
        if (!plugin.highlightAllowed(step, npc.getWorldLocation())) return false;

        return true;
    }
}
