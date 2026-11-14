package com.routeplanner.teleport;

import lombok.RequiredArgsConstructor;
import com.routeplanner.RoutePlannerConfig;
import com.routeplanner.util.SceneObjects;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.client.config.ConfigManager;
import javax.inject.*;
import java.util.*;

/**
 * Remembers, per character, which spirit tree patches have a grown tree the player can travel
 * from. A farming patch shows each player their own crop, so a grown tree seen in a patch is
 * the player's. Checked whenever a patch is in view, and kept between sessions.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PlantedSpiritTrees {

    private static final String KEY = "plantedSpiritTrees";
    /** How often the patches in view are looked at. */
    private static final int CHECK_TICKS = 5;

    private final Client client;
    private final ConfigManager configManager;

    /** The grown trees' keys ({@link SpiritTrees.Tree#key()}); null until read for this character. */
    private Set<String> grown;
    private String loadedFor;

    /** The trees grown in the player's patches, as far as the player has seen. */
    public Set<String> grown() {
        load();
        return grown == null ? Collections.emptySet() : grown;
    }

    public void onGameTick() {
        if (client.getGameState() != GameState.LOGGED_IN || client.getTickCount() % CHECK_TICKS != 0) return;
        WorldView wv = client.getTopLevelWorldView();
        if (wv == null || wv.isInstance() || wv.getScene() == null) return;
        load();
        if (grown == null) return;
        boolean changed = false;
        for (SpiritTrees.Tree tree : SpiritTrees.plantedTrees()) {
            Boolean ready = treeReady(wv, tree);
            if (ready == null) continue; // not in view
            changed |= ready ? grown.add(tree.key()) : grown.remove(tree.key());
        }
        if (changed) {
            configManager.setRSProfileConfiguration(RoutePlannerConfig.GROUP, KEY, String.join(";", grown));
        }
    }

    /**
     * Whether the patch's tree can be travelled from now; null when the patch isn't in the scene.
     * Each patch has its own object id (the data names them all 8355), so the patch is found as
     * the farming-patch object (one that changes with the player's crop) around the tree's tiles.
     */
    private Boolean treeReady(WorldView wv, SpiritTrees.Tree tree) {
        Tile[][] plane = wv.getScene().getTiles()[wv.getPlane()];
        WorldPoint wp = tree.tiles().get(0);
        if (wp.getPlane() != wv.getPlane()) return null;
        LocalPoint lp = LocalPoint.fromWorld(wv, wp);
        if (lp == null) return null;
        boolean patchSeen = false;
        // The patch stands in the middle of the tiles around it: look a few tiles in
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                int x = lp.getSceneX() + dx, y = lp.getSceneY() + dy;
                if (x < 0 || y < 0 || x >= plane.length || y >= plane[x].length || plane[x][y] == null) continue;
                for (GameObject obj : plane[x][y].getGameObjects()) {
                    if (!SceneObjects.isObject(obj)) continue;
                    ObjectComposition comp = client.getObjectDefinition(obj.getId());
                    if (comp == null || comp.getImpostorIds() == null) continue;
                    patchSeen = true;
                    if (canTravel(comp.getImpostor())) return true;
                }
            }
        }
        return patchSeen ? false : null;
    }

    private static boolean canTravel(ObjectComposition comp) {
        if (comp == null || comp.getActions() == null) return false;
        for (String a : comp.getActions()) if (a != null && a.equalsIgnoreCase("Travel")) return true;
        return false;
    }

    /** Read the saved trees once per character (the RS profile changes when switching characters). */
    private void load() {
        String profile = configManager.getRSProfileKey();
        if (profile == null) {
            grown = null;
            loadedFor = null;
            return;
        }
        if (profile.equals(loadedFor)) return;
        loadedFor = profile;
        grown = new HashSet<>();
        String saved = configManager.getRSProfileConfiguration(RoutePlannerConfig.GROUP, KEY);
        if (saved != null) {
            for (String k : saved.split(";")) if (!k.trim().isEmpty()) grown.add(k.trim());
        }
    }
}
