package com.routeplanner.skilling;

import lombok.Data;

@Data
public class SkillingNpc {
    private final String name;
    private final int levelRequired;
    private final double xpPerAction;
    private final String notes;

    public SkillingNpc(String name, int levelRequired, double xpPerAction, String notes) {
        this.name = name;
        this.levelRequired = levelRequired;
        this.xpPerAction = xpPerAction;
        this.notes = notes;
    }

    @Override
    public String toString() {
        return name + " (Lv " + levelRequired + " - " + xpPerAction + " xp)";
    }
}
