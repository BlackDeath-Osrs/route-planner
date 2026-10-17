package com.routeplanner.ui;

import com.routeplanner.bank.BankItemManager;
import com.routeplanner.model.RouteStep;

import java.util.List;

/**
 * Live progress for the active step, read on the client thread each tick and handed to the
 * side panel's step card, which only repaints when {@link #signature()} changes.
 */
public final class StepLive {

    /** Distance values that aren't a tile count. */
    public static final int NO_TARGET = -1;
    public static final int OTHER_FLOOR = -2;

    public final RouteStep step;
    public final int distance;
    public final String itemHeader;                         // "Buy", "Gather"...; null without items
    public final List<BankItemManager.ItemProgress> items;
    public final String skillLabel;                         // e.g. "Fishing XP gained"; null without a goal
    public final long skillHave;
    public final long skillNeed;
    public final int dialogDone;

    public StepLive(RouteStep step, int distance, String itemHeader, List<BankItemManager.ItemProgress> items,
                    String skillLabel, long skillHave, long skillNeed, int dialogDone) {
        this.step = step;
        this.distance = distance;
        this.itemHeader = itemHeader;
        this.items = items;
        this.skillLabel = skillLabel;
        this.skillHave = skillHave;
        this.skillNeed = skillNeed;
        this.dialogDone = dialogDone;
    }

    /** Everything shown except the distance, so the card rebuilds only when progress moves. */
    public String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(System.identityHashCode(step)).append('|').append(itemHeader).append('|');
        if (items != null) for (BankItemManager.ItemProgress i : items) sb.append(i.name).append(':').append(i.have).append('/').append(i.need).append(',');
        sb.append('|').append(skillLabel).append(':').append(skillHave).append('/').append(skillNeed);
        sb.append('|').append(step.getNpcKillProgress()).append('|').append(dialogDone);
        return sb.toString();
    }
}
