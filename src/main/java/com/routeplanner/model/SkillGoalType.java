package com.routeplanner.model;

/** What a skill goal's value means. Saved by name, so these names must never change. */
public enum SkillGoalType {
    /** XP gained since the step started. */
    XP_GAIN,
    /** A total XP to reach. */
    XP_TARGET,
    /** A real (unboosted) level to reach. */
    LEVEL;

    /** The type with this name, or null when it isn't one (e.g. a blank legacy agility goal). */
    public static SkillGoalType parse(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
