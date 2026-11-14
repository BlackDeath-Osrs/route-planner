package com.routeplanner.teleport;

import lombok.*;
import net.runelite.api.coords.WorldPoint;

@Getter
@RequiredArgsConstructor
public class TeleportSpell {
    private final String name;
    private final WorldPoint destination;
    private final String book;
    private final int spriteId;
    private final String iconResource; // resource name in /spells/

    @Override
    public String toString() {
        return name;
    }
}
