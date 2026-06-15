package com.routeplanner.skilling;

import java.util.*;

public class ThievingStalls {

    // Stall name must match the in-game object name (full stall, not the looted variant)
    public static final List<SkillingNpc> STALLS = Arrays.asList(
        new SkillingNpc("Vegetable stall",   2,  10.0,  "Hosidius, Civitas illa Fortis"),
        new SkillingNpc("Baker's stall",     5,  16.0,  "Ardougne, Kourend"),
        new SkillingNpc("Tea stall",         5,  16.0,  "Varrock east"),
        new SkillingNpc("Crafting stall",    5,  16.0,  "Ape Atoll, Prifddinas"),
        new SkillingNpc("Food stall",        5,  16.0,  "Ape Atoll"),
        new SkillingNpc("Monkey food stall", 5,  16.0,  "Ape Atoll"),
        new SkillingNpc("Rock cake stall",   15, 6.5,   "Gu'Tanoth"),
        new SkillingNpc("Silk stall",        20, 24.0,  "Ardougne, Civitas illa Fortis"),
        new SkillingNpc("Wine stall",        22, 27.0,  "Draynor Village"),
        new SkillingNpc("Fruit stall",       25, 28.5,  "Hosidius, Prifddinas"),
        new SkillingNpc("Seed stall",        27, 10.0,  "Draynor Village"),
        new SkillingNpc("Fur stall",         35, 36.0,  "Ardougne, Rellekka"),
        new SkillingNpc("Fish stall",        42, 42.0,  "Rellekka, Civitas illa Fortis"),
        new SkillingNpc("Crossbow stall",    49, 52.0,  "Keldagrim"),
        new SkillingNpc("Silver stall",      50, 54.0,  "Ardougne, Keldagrim"),
        new SkillingNpc("Spice stall",       65, 81.0,  "Ardougne, Civitas illa Fortis"),
        new SkillingNpc("Magic stall",       65, 100.0, "Ape Atoll"),
        new SkillingNpc("Scimitar stall",    65, 100.0, "Ape Atoll"),
        new SkillingNpc("Gem stall",         75, 160.0, "Ardougne, Keldagrim, Prifddinas")
    );

    public static SkillingNpc getByName(String name) {
        return STALLS.stream()
            .filter(s -> s.getName().equalsIgnoreCase(name))
            .findFirst().orElse(null);
    }
}
