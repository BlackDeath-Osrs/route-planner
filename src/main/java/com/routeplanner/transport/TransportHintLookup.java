package com.routeplanner.transport;

import net.runelite.api.coords.WorldPoint;

import java.util.List;

/**
 * Finds the best usable plane transition (staircase, ladder, etc.) for getting from a player's
 * current plane to a specific target tile on another plane. Deliberately scoped to a single,
 * direct hop -- if reaching the target plane requires climbing through an intermediate floor,
 * this returns null and the caller falls back to the plain "different floor" message rather than
 * attempting multi-hop routing, which is out of scope.
 *
 * <p>Scoring is total travel distance (player -> transition's origin, PLUS transition's
 * destination -> the real target), not just "nearest transition to the player". A transition that
 * is close to the player but drops you far from the actual destination is a worse choice overall
 * than one that is farther to reach but lands you right next to the target -- e.g. Lumbridge
 * Castle has both a staircase (near Duke Horacio) and a ladder (near a different part of the
 * building) going to the same plane; picking by origin-distance alone can pick the ladder and
 * strand the player on the correct floor but far from where they need to be.
 */
public final class TransportHintLookup {

    private TransportHintLookup() {}

    /**
     * @param player        the player's current position
     * @param actualTarget  the active step's real destination (used to score which transition
     *                      lands the player closest to where they actually need to go)
     * @param maxDistance   only consider transitions whose origin is within this many tiles of the
     *                      player (a plain WorldPoint distance check, not pathfinding distance --
     *                      a coarse "is it plausibly nearby" filter, not a promise of walkability)
     * @return the transition with the lowest total travel distance (player->origin +
     *         destination->actualTarget), or null if none exists within range. Never throws.
     */
    /** Minimum improvement (in tiles of total travel distance) a new candidate must show over the
     *  currently-committed transition before we switch to it. Without this, two transitions that
     *  are within a few tiles of each other on total distance -- common near a building with
     *  transitions on multiple sides -- cause the recommendation to flip-flop as the player simply
     *  walks around, since "best by even one tile" changes constantly. A real margin means we only
     *  switch when a candidate is genuinely, substantially better, not just narrowly ahead. */
    private static final int SWITCH_MARGIN = 8;

    /**
     * @param current  the transition currently committed to (from a prior call), or null if none.
     *                 Kept as long as it is still a valid candidate and no other option beats its
     *                 score by more than SWITCH_MARGIN tiles.
     */
    public static Transport findNearest(List<Transport> transitions, WorldPoint player, WorldPoint actualTarget, int maxDistance, Transport current) {
        if (transitions == null || player == null || actualTarget == null) return null;
        if (player.getPlane() == actualTarget.getPlane()) return null; // already there, nothing to find

        Transport best = null;
        int bestTotalDistance = Integer.MAX_VALUE;
        Integer currentScore = null;

        for (Transport t : transitions) {
            if (t == null || t.origin == null || t.destination == null) continue;
            if (t.origin.getPlane() != player.getPlane()) continue;
            if (t.destination.getPlane() != actualTarget.getPlane()) continue;

            int legToTransition = player.distanceTo(t.origin);
            if (legToTransition < 0 || legToTransition > maxDistance) continue; // out of range, or unreachable by plane (shouldn't happen, planes already match)

            int legFromTransition = t.destination.distanceTo(actualTarget);
            if (legFromTransition < 0) continue; // destination and actualTarget planes match by construction, but guard anyway

            int total = legToTransition + legFromTransition;

            if (current != null && sameTransition(t, current)) {
                currentScore = total;
            }
            if (total < bestTotalDistance) {
                bestTotalDistance = total;
                best = t;
            }
        }

        if (best == null) return null; // nothing valid in range at all, current or otherwise

        // The previously-committed transition is still a valid candidate: keep it unless something
        // else beats it by a real margin, not just a narrow edge.
        if (currentScore != null && !sameTransition(best, current)) {
            if (bestTotalDistance >= currentScore - SWITCH_MARGIN) {
                return current;
            }
        }
        return best;
    }

    /** Transports don't have identity/equality of their own; two Transport instances describe the
     *  "same" transition if they share an origin tile (transports.tsv sometimes lists the same
     *  physical staircase from both climb directions as separate rows, but a single origin tile
     *  uniquely identifies "this specific staircase, approached from here"). */
    private static boolean sameTransition(Transport a, Transport b) {
        if (a == null || b == null) return false;
        return a.origin != null && a.origin.equals(b.origin);
    }
}
