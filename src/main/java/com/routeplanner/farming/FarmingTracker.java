package com.routeplanner.farming;

import lombok.RequiredArgsConstructor;
import com.routeplanner.util.SceneObjects;
import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;
import javax.inject.*;

/**
 * Completes farming patch steps. A patch is known by its own object id, which stays the same
 * whatever is growing in it, so one step works for any seed:
 *
 *  - replant steps are done on "You plant ..." just after something was used on the patch
 *    (planting gives no XP of its own, and the message covers every crop: seeds, saplings,
 *    spores, coral frags);
 *  - other patch steps (a bush, a tree) once the patch gave Farming XP and has nothing left to
 *    pick, harvest or check, or once a dead crop was cleared from it.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FarmingTracker {

    /** Farming XP counts for a patch clicked this many ticks before (walking over to it first). */
    private static final int CLICK_WINDOW_TICKS = 30;

    private final Client client;
    private final RoutePlannerPlugin plugin;

    private int clickedPatchId = -1;
    private int clickTick = -1000;
    private int lastFarmingXp = -1;
    /** The step that got Farming XP from its patch (a tended patch is then done once it has no work left). */
    private RouteStep tendedStep;

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked e) {
        if (!isObjectAction(e.getMenuAction())) return;
        clickedPatchId = e.getId();
        clickTick = client.getTickCount();
        // A dead patch gives no XP for clearing, so clearing it counts as tending it: the step is
        // done once the patch is empty (a replant step after it then has the seed planted)
        RouteStep step = activeFarmStep();
        if (step != null && !step.isFarmReplant() && e.getId() == step.getFarmPatchId()
            && "Clear".equalsIgnoreCase(e.getMenuOption()) && isDead(e.getId())) {
            tendedStep = step;
        }
    }

    /** Whether the patch, as it looks now, holds a dead crop ("Dead herbs", "Dead tree", ...). */
    private boolean isDead(int patchId) {
        ObjectComposition comp = client.getObjectDefinition(patchId);
        if (comp != null && comp.getImpostorIds() != null) comp = comp.getImpostor();
        return comp != null && comp.getName() != null && comp.getName().toLowerCase().startsWith("dead");
    }

    @Subscribe
    public void onStatChanged(StatChanged e) {
        if (e.getSkill() != Skill.FARMING) return;
        int old = lastFarmingXp;
        lastFarmingXp = e.getXp();
        if (old < 0 || e.getXp() <= old) return;
        RouteStep step = activeFarmStep();
        if (step == null || clickedPatchId != step.getFarmPatchId()
            || client.getTickCount() - clickTick > CLICK_WINDOW_TICKS) return;
        if (!step.isFarmReplant()) tendedStep = step;
    }

    @Subscribe
    public void onChatMessage(ChatMessage e) {
        if (e.getType() != ChatMessageType.SPAM && e.getType() != ChatMessageType.GAMEMESSAGE) return;
        if (!Text.removeTags(e.getMessage()).startsWith("You plant ")) return;
        RouteStep step = activeFarmStep();
        if (step == null || !step.isFarmReplant() || clickedPatchId != step.getFarmPatchId()
            || client.getTickCount() - clickTick > CLICK_WINDOW_TICKS) return;
        finish(step);
    }

    @Subscribe
    public void onGameTick(GameTick e) {
        RouteStep step = activeFarmStep();
        if (step != null && step.farmPatchUnresolved()) {
            resolvePatch(step);
            return;
        }
        if (step == null || step != tendedStep || step.isFarmReplant()) return;
        // The patch changes look a tick or so after the XP: wait until it has no work left
        if (!hasWorkLeft(step.getFarmPatchId())) finish(step);
    }

    /** How far from the step's tile its patch may be, when it's given by place rather than id. */
    private static final int RESOLVE_RADIUS = 8;

    /** Find the farming patch nearest the step's tile once it's in the scene, and keep its id. */
    private void resolvePatch(RouteStep step) {
        WorldPoint at = step.getWorldPoint();
        WorldView wv = client.getTopLevelWorldView();
        if (at == null || wv == null || wv.getScene() == null || wv.isInstance() || at.getPlane() != wv.getPlane()) return;
        Tile[][] plane = wv.getScene().getTiles()[wv.getPlane()];
        int cx = at.getX() - wv.getBaseX(), cy = at.getY() - wv.getBaseY();
        int best = -1, bestDist = Integer.MAX_VALUE;
        for (int x = Math.max(0, cx - RESOLVE_RADIUS); x <= Math.min(plane.length - 1, cx + RESOLVE_RADIUS); x++) {
            for (int y = Math.max(0, cy - RESOLVE_RADIUS); y <= Math.min(plane[x].length - 1, cy + RESOLVE_RADIUS); y++) {
                Tile tile = plane[x][y];
                if (tile == null || tile.getGameObjects() == null) continue;
                for (GameObject obj : tile.getGameObjects()) {
                    if (!SceneObjects.isObject(obj) || !FarmingPatches.isPatch(obj.getId())) continue;
                    // Distance to the nearest of the patch's tiles (tree patches are 3x3 and bigger)
                    int dx = Math.max(0, Math.max(obj.getSceneMinLocation().getX() - cx, cx - obj.getSceneMaxLocation().getX()));
                    int dy = Math.max(0, Math.max(obj.getSceneMinLocation().getY() - cy, cy - obj.getSceneMaxLocation().getY()));
                    int d = Math.max(dx, dy);
                    if (d < bestDist) { bestDist = d; best = obj.getId(); }
                }
            }
        }
        if (best < 0) return; // not loaded yet, or no patch there
        step.setFarmPatchId(best);
        plugin.saveRoutes();
    }

    private void finish(RouteStep step) {
        step.setFarmDone(true);
        step.setLocationReached(true); // worked the patch, so it was reached, from whichever side
        tendedStep = null;
        plugin.tryCompleteActiveStep(step);
    }

    private RouteStep activeFarmStep() {
        Route route = plugin.getActiveRoute();
        RouteStep step = route == null ? null : route.getActiveStep();
        return step != null && step.hasFarmPatch() && !step.isFarmDone() ? step : null;
    }

    /** Whether the patch, as it looks now, still has something to pick, harvest, check or rake. */
    private boolean hasWorkLeft(int patchId) {
        ObjectComposition comp = client.getObjectDefinition(patchId);
        if (comp != null && comp.getImpostorIds() != null) comp = comp.getImpostor();
        if (comp == null || comp.getActions() == null) return false;
        for (String a : comp.getActions()) {
            if (a == null) continue;
            String l = a.toLowerCase();
            // Not "Clear" or "Chop down": a grown bush or tree always offers them
            if (l.startsWith("pick") || l.startsWith("harvest") || l.equals("check-health")
                || l.equals("rake") || l.equals("cure") || l.equals("take") || l.equals("collect")) return true;
        }
        return false;
    }

    /** "Torstol seed -> Herb patch": the item used, "Torstol seed". */
    public static String usedItem(String target) {
        if (target == null) return null;
        int arrow = target.indexOf("->");
        return arrow < 0 ? null : target.substring(0, arrow).trim();
    }

    private static boolean isObjectAction(MenuAction a) {
        switch (a) {
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
            case WIDGET_TARGET_ON_GAME_OBJECT:
                return true;
            default:
                return false;
        }
    }
}
