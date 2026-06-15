package com.routeplanner.teleport;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@Getter
public class TeleportSpell {
    private final String name;
    private final WorldPoint destination;
    private final String book;
    private final int spriteId;
    private final String iconResource; // resource name in /spells/

    public TeleportSpell(String name, WorldPoint destination, String book, int spriteId, String iconResource) {
        this.name = name;
        this.destination = destination;
        this.book = book;
        this.spriteId = spriteId;
        this.iconResource = iconResource;
    }

    @Override
    public String toString() {
        return name;
    }
}
