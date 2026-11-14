package com.routeplanner.skilling;

import lombok.*;

@Data
@RequiredArgsConstructor
public class SkillingNpc {
    private final String name;
    private final int levelRequired;
    private final double xpPerAction;
    private final String notes;

    @Override
    public String toString() {
        return name + " (Lv " + levelRequired + " - " + xpPerAction + " xp)";
    }
}
