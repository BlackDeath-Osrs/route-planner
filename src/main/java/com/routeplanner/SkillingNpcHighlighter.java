package com.routeplanner;

import lombok.RequiredArgsConstructor;
import com.routeplanner.model.RouteStep;
import com.routeplanner.sailing.Sailing;
import com.routeplanner.teleport.Quetzals;
import com.routeplanner.util.NpcNames;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.npcoverlay.*;
import javax.inject.*;
import java.util.*;
import java.util.function.Function;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SkillingNpcHighlighter {

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final NpcOverlayService npcOverlayService;
    private final Function<NPC, HighlightedNpc> highlightFunction = this::shouldHighlight;

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

    /** Targets that an NPC in range is named exactly; their longer-named lookalikes stay dark. */
    private Set<String> exactNearby = Collections.emptySet();

    /** Once a tick: re-check which targets have an exact-named NPC in range, re-lighting if that changed. */
    public void onGameTick() {
        String target = target(activeStep());
        Set<String> exact = new HashSet<>();
        WorldPoint at = target == null ? null : Sailing.position(client);
        if (at != null && client.getTopLevelWorldView() != null) {
            List<String> names = targetNames(target);
            int range = range();
            for (NPC npc : client.getTopLevelWorldView().npcs()) {
                if (npc == null) continue;
                String t = NpcNames.exactTarget(names, npc.getName());
                if (t != null && npc.getWorldLocation().distanceTo(at) <= range) exact.add(t);
            }
        }
        if (!exact.equals(exactNearby)) {
            exactNearby = exact;
            rebuild();
        }
    }

    private RouteStep activeStep() {
        return plugin.getActiveRoute() == null ? null : plugin.getActiveRoute().getActiveStep();
    }

    /** The step's NPC target text, or null when it highlights no NPC. */
    private static String target(RouteStep step) {
        if (step == null || !step.isHighlightEnabled()) return null;
        String t = step.getSkillingTargetNpc() != null ? step.getSkillingTargetNpc() : step.getNpcHighlight();
        return t == null || t.trim().isEmpty() ? null : t;
    }

    /** How far from the player NPCs are lit: 30 tiles, or as far as the client draws at sea. */
    private int range() {
        int range = 30;
        if (Sailing.isSailing(client) && client.getTopLevelWorldView() != null) {
            range = Math.max(range, client.getTopLevelWorldView().getScene().getDrawDistance());
        }
        return range;
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

    /** On a Renu quetzal step not yet flown: Renu, at any landing site in range. */
    private boolean isRenuToTake(RouteStep step, NPC npc) {
        if (!Quetzals.isRenuStep(step) || !Quetzals.RENU.equalsIgnoreCase(npc.getName())) return false;
        WorldPoint at = Sailing.position(client);
        return at != null && !Quetzals.arrived(step, at) && npc.getWorldLocation().distanceTo(at) <= range();
    }

    /** Whether the active step highlights this NPC. */
    public boolean isHighlighted(NPC npc) {
        RouteStep step = activeStep();
        if (isRenuToTake(step, npc)) return true;
        String targetName = target(step);
        if (targetName == null) return false;

        // Comma-separated NPCs with / for alternatives, e.g. "Rommik,Shop assistant/Shop keeper"
        if (!NpcNames.matches(targetNames(targetName), npc.getName(), exactNearby)) return false;

        // Only NPCs in range of the player (at sea, of the boat)
        WorldPoint at = Sailing.position(client);
        if (at == null) return false;
        if (npc.getWorldLocation().distanceTo(at) > range()) return false;
        return plugin.highlightAllowed(step, npc.getWorldLocation());
    }
}
