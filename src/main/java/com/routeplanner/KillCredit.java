package com.routeplanner;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Which NPCs the player has been hitting. An NPC counts as the player's kill if one of their
 * hitsplats landed on it shortly before it died, blocked hits included: a slow weapon or a run of
 * zeroes is still a fight, while an NPC other players are killing never shows one.
 */
public class KillCredit {
    /** Long enough for the slowest weapons, short enough to drop an NPC the player walked away from. */
    static final int WINDOW_TICKS = 20;

    private final Map<Object, Integer> lastHitTick = new WeakHashMap<>();

    public void onHit(Object npc, int tick) {
        lastHitTick.put(npc, tick);
    }

    public boolean isMine(Object npc, int tick) {
        Integer hit = lastHitTick.get(npc);
        return hit != null && tick - hit <= WINDOW_TICKS;
    }
}
