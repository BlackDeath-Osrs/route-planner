package com.routeplanner.sailing;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldEntityConfig;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;

/**
 * Trawling shoals (Sailing): finding the one being fished, how deep it swims, and how deep the nets
 * are. Depths are 1 shallow, 2 medium, 3 deep (0 = a raised net, or a shoal whose depth isn't known).
 * Worked out the way Maewi's Deep Sea Trawling plugin does.
 */
public final class Shoals {

    /** World entities of this config type are trawling shoals. */
    private static final int SHOAL_ENTITY_TYPE = 4;

    private Shoals() {}

    /** The nearest shoal as of a game tick, shared by the overlays that each want it every frame. */
    private static WorldEntity nearestShoal;
    private static int nearestTick = -1;

    /** The shoal nearest the player's boat, or null; worked out once per game tick. */
    public static WorldEntity nearest(Client client) {
        int tick = client.getTickCount();
        if (tick != nearestTick) {
            nearestTick = tick;
            nearestShoal = findNearest(client);
        }
        return nearestShoal;
    }

    private static WorldEntity findNearest(Client client) {
        WorldView top = client.getTopLevelWorldView();
        WorldPoint boat = Sailing.position(client);
        if (top == null || boat == null) return null;
        WorldEntity best = null;
        int bestDist = Integer.MAX_VALUE;
        for (WorldEntity e : top.worldEntities()) {
            WorldEntityConfig cfg = e.getConfig();
            LocalPoint lp = e.getLocalLocation();
            if (cfg == null || cfg.getId() != SHOAL_ENTITY_TYPE || lp == null) continue;
            int d = WorldPoint.fromLocalInstance(client, lp).distanceTo2D(boat);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    /** How deep the shoal swims right now, from its ripples' animation; 0 if that can't be told. */
    public static int depth(WorldEntity shoal) {
        WorldView wv = shoal == null ? null : shoal.getWorldView();
        if (wv == null) return 0;
        for (NPC npc : wv.npcs()) {
            if (npc == null || npc.getId() != NpcID.SAILING_SHOAL_RIPPLES) continue;
            switch (npc.getAnimation()) {
                case AnimationID.DEEP_SEA_TRAWLING_SHOAL_SHALLOW:
                case AnimationID.DEEP_SEA_TRAWLING_SHOAL_SHALLOW_NOSOUND:
                    return 1;
                case AnimationID.DEEP_SEA_TRAWLING_SHOAL_MID:
                case AnimationID.DEEP_SEA_TRAWLING_SHOAL_MID_NOSOUND:
                    return 2;
                case AnimationID.DEEP_SEA_TRAWLING_SHOAL_DEEP:
                case AnimationID.DEEP_SEA_TRAWLING_SHOAL_DEEP_NOSOUND:
                    return 3;
                default:
                    return 0;
            }
        }
        return 0;
    }

    /** How deep net {@code index} (0 = starboard, or a skiff's only net; 1 = port) is; 0 = raised. */
    public static int netDepth(Client client, int index) {
        return client.getVarbitValue(index == 0 ? VarbitID.SAILING_SIDEPANEL_BOAT_TRAWLING_NET_0_DEPTH
            : VarbitID.SAILING_SIDEPANEL_BOAT_TRAWLING_NET_1_DEPTH);
    }

    /** Whether either net is in the water. */
    public static boolean netsDown(Client client) {
        return netDepth(client, 0) > 0 || netDepth(client, 1) > 0;
    }
}
