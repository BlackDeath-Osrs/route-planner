package com.routeplanner.transport;

import net.runelite.api.coords.WorldPoint;

/**
 * A single plane-changing transition parsed from transports.tsv -- a staircase, ladder, or similar
 * object connecting two tiles on different planes. Distinct from the pathfinder's own
 * TransportLoader, which discards everything except origin->destination as a bare routing edge;
 * this keeps the fields needed to describe and highlight the transition to the player.
 */
public class Transport {
    public final WorldPoint origin;
    public final WorldPoint destination;
    // transports.tsv packs the action and object name into one space-joined column, e.g.
    // "Climb-up Staircase" or "Open Door" -- there is no separate delimiter between them in the
    // real data, so they are kept together rather than force-split into two fields that don't
    // actually exist independently in the source file.
    public final String actionText;   // e.g. "Climb-up Staircase", "Open Door"
    public final int objectId;        // -1 if absent/unparseable

    public Transport(WorldPoint origin, WorldPoint destination, String actionText, int objectId) {
        this.origin = origin;
        this.destination = destination;
        this.actionText = actionText;
        this.objectId = objectId;
    }

    /** A short, human-readable hint built from the row's own data, e.g. "Climb-up the Staircase". */
    public String hintText() {
        return actionText != null ? actionText : "Use the transition";
    }
}
