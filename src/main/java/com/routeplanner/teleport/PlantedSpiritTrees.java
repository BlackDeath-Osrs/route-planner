package com.routeplanner.teleport;

import com.routeplanner.RoutePlannerConfig;
import com.routeplanner.util.SceneObjects;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Remembers, per character, which spirit tree patches have a grown tree the player can travel
 * from. A farming patch shows each player their own crop, so a grown tree seen in a patch is
 * the player's. Checked whenever a patch is in view, and kept between sessions.
 */
@Singleton
public class PlantedSpiritTrees {

    private static final String KEY = "plantedSpiritTrees";
    /** How often the patches in view are looked at. */
    private static final int CHECK_TICKS = 5;

    private final Client client;
    private final ConfigManager configManager;

    /** The grown trees' keys ({@link SpiritTrees.Tree#key()}); null until read for this character. */
    private Set<String> grown;
    private String loadedFor;

    @Inject
    public PlantedSpiritTrees(Client client, ConfigManager configManager) {
        this.client = client;
        this.configManager = configManager;
    }

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

    /** Whether the patch's tree can be travelled from now; null when the patch isn't in the scene. */
    private Boolean treeReady(WorldView wv, SpiritTrees.Tree tree) {
        Tile[][] plane = wv.getScene().getTiles()[wv.getPlane()];
        for (WorldPoint wp : tree.tiles()) {
            if (wp.getPlane() != wv.getPlane()) return null;
            LocalPoint lp = LocalPoint.fromWorld(wv, wp);
            if (lp == null) return null;
            // The patch stands in the middle of the tiles around it: look a few tiles in
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    int x = lp.getSceneX() + dx, y = lp.getSceneY() + dy;
                    if (x < 0 || y < 0 || x >= plane.length || y >= plane[x].length || plane[x][y] == null) continue;
                    for (GameObject obj : plane[x][y].getGameObjects()) {
                        if (!SceneObjects.isObject(obj) || obj.getId() != tree.objectId) continue;
                        return canTravel(obj.getId());
                    }
                }
            }
            return null;
        }
        return null;
    }

    private boolean canTravel(int id) {
        ObjectComposition comp = client.getObjectDefinition(id);
        if (comp != null && comp.getImpostorIds() != null) comp = comp.getImpostor();
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
