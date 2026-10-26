package com.routeplanner.agility;

import net.runelite.api.coords.WorldPoint;
import java.util.Arrays;
import java.util.List;

public class AgilityCoursePresets {

    public static final List<AgilityCourse> ALL = Arrays.asList(
        gnome(),
        draynor(),
        alKharid(),
        varrock(),
        canifis(),
        falador(),
        seers(),
        rellekka(),
        ardougne(),
        pollnivneach(),
        prifddinas()
    );

    // Region IDs for auto-detection
    public static AgilityCourse getCourseForRegion(int regionId) {
        switch (regionId) {
            case 10553: return gnome();
            case 12338: return draynor();
            case 13105: return alKharid();
            case 12597: return varrock();
            case 13878: return canifis();
            case 11828: return falador();
            case 10806: return seers();
            case 10297: return rellekka();
            case 10547: return ardougne();
            default: return null;
        }
    }

    // Start tile for each course (walk-to destination). null = no location known yet.
    public static WorldPoint startLocation(String courseName) {
        if (courseName == null) return null;
        switch (courseName) {
            case "Draynor Village": return new WorldPoint(3103, 3279, 0);
            case "Al Kharid":       return new WorldPoint(3273, 3196, 0);
            case "Varrock":         return new WorldPoint(3221, 3414, 0);
            case "Canifis":         return new WorldPoint(3505, 3488, 0);
            case "Falador":         return new WorldPoint(3036, 3341, 0);
            case "Seers Village":   return new WorldPoint(2729, 3485, 0);
            case "Rellekka":        return new WorldPoint(2626, 3676, 0);
            case "Ardougne":        return new WorldPoint(2673, 3320, 0);
            default:                return null;
        }
    }

    public static AgilityCourse gnome() {
        return new AgilityCourse("Gnome Stronghold", 1, 86.5, Arrays.asList(
            new AgilityObstacle("Log Balance",      null, 23145),
            new AgilityObstacle("Obstacle Net 1",   null, 23134),
            new AgilityObstacle("Tree Branch Up",   null, 23559),
            new AgilityObstacle("Balancing Rope",   null, 23557),
            new AgilityObstacle("Tree Branch Down", null, 23560),
            new AgilityObstacle("Obstacle Net 2",   null, 23135),
            new AgilityObstacle("Obstacle Pipe",    null, 23138, 23139)
        ));
    }

    public static AgilityCourse draynor() {
        return new AgilityCourse("Draynor Village", 1, 120, Arrays.asList(
            new AgilityObstacle("Rough Wall",  null, 11404),
            new AgilityObstacle("Tightrope 1", null, 11405),
            new AgilityObstacle("Tightrope 2", null, 11406),
            new AgilityObstacle("Narrow Wall", null, 11430),
            new AgilityObstacle("Wall",        null, 11630),
            new AgilityObstacle("Gap",         null, 11631),
            new AgilityObstacle("Crate",       null, 11632)
        ));
    }

    public static AgilityCourse alKharid() {
        return new AgilityCourse("Al Kharid", 20, 216, Arrays.asList(
            new AgilityObstacle("Rough Wall",     null, 11633),
            new AgilityObstacle("Tightrope 1",    null, 14398),
            new AgilityObstacle("Cable",          null, 14402),
            new AgilityObstacle("Zip Line",       null, 14403),
            new AgilityObstacle("Tropical Tree",  null, 14404),
            new AgilityObstacle("Roof Top Beams", null, 11634),
            new AgilityObstacle("Tightrope 2",    null, 14409),
            new AgilityObstacle("Gap",            null, 14399)
        ));
    }

    public static AgilityCourse varrock() {
        return new AgilityCourse("Varrock", 30, 238, Arrays.asList(
            new AgilityObstacle("Rough Wall",   null, 14412),
            new AgilityObstacle("Clothes Line", null, 14413),
            new AgilityObstacle("Gap 1",        null, 14414),
            new AgilityObstacle("Wall",         null, 14832),
            new AgilityObstacle("Gap 2",        null, 14833),
            new AgilityObstacle("Gap 3",        null, 14834),
            new AgilityObstacle("Leap",         null, 14835),
            new AgilityObstacle("Ledge",        null, 14836),
            new AgilityObstacle("Edge",         null, 14841)
        ));
    }

    public static AgilityCourse canifis() {
        return new AgilityCourse("Canifis", 40, 240, Arrays.asList(
            new AgilityObstacle("Tall Tree", null, 14843),
            new AgilityObstacle("Gap 1",     null, 14844),
            new AgilityObstacle("Gap 2",     null, 14845),
            new AgilityObstacle("Gap 3",     null, 14848),
            new AgilityObstacle("Gap 4",     null, 14846),
            new AgilityObstacle("Polevault", null, 14894),
            new AgilityObstacle("Gap 5",     null, 14847),
            new AgilityObstacle("Gap 6",     null, 14897)
        ));
    }

    public static AgilityCourse falador() {
        return new AgilityCourse("Falador", 50, 340, Arrays.asList(
            new AgilityObstacle("Rough Wall",   null, 14898),
            new AgilityObstacle("Tightrope 1",  null, 14899),
            new AgilityObstacle("Cross Wall",   null, 14901),
            new AgilityObstacle("Gap 1",        null, 14903),
            new AgilityObstacle("Gap 2",        null, 14904),
            new AgilityObstacle("Tightrope 1",  null, 14905),
            new AgilityObstacle("Tightrope 2",  null, 14911),
            new AgilityObstacle("Gap 3",        null, 14919),
            new AgilityObstacle("Ledge",        null, 14920),
            new AgilityObstacle("Ledge 2",      null, 14921),
            new AgilityObstacle("Ledge 3",      null, 14922, 14923),
            new AgilityObstacle("Ledge 4",      null, 14924),
            new AgilityObstacle("Edge",         null, 14925)
        ));
    }

    public static AgilityCourse seers() {
        return new AgilityCourse("Seers Village", 60, 570, Arrays.asList(
            new AgilityObstacle("Wall",      null, 14927),
            new AgilityObstacle("Gap 1",     null, 14928),
            new AgilityObstacle("Tightrope", null, 14932),
            new AgilityObstacle("Gap 2",     null, 14929),
            new AgilityObstacle("Gap 3",     null, 14930),
            new AgilityObstacle("Edge",      null, 14931)
        ));
    }

    public static AgilityCourse rellekka() {
        return new AgilityCourse("Rellekka", 80, 780, Arrays.asList(
            new AgilityObstacle("Rough Wall",   null, 14946),
            new AgilityObstacle("Gap 1",        null, 14947),
            new AgilityObstacle("Tightrope 1",  null, 14987),
            new AgilityObstacle("Gap 2",        null, 14990),
            new AgilityObstacle("Gap 3",        null, 14991),
            new AgilityObstacle("Tightrope 2",  null, 14992),
            new AgilityObstacle("Pile of Fish", new WorldPoint(2654, 3676, 3), 14994)
        ));
    }

    // Ids from the game's object list (gameval ObjectID). No start tile: a recorded lap starts
    // where its first obstacle was clicked.
    public static AgilityCourse pollnivneach() {
        return new AgilityCourse("Pollnivneach", 70, 890, Arrays.asList(
            new AgilityObstacle("Basket",       null, 14935),
            new AgilityObstacle("Market Stall", null, 14936),
            new AgilityObstacle("Banner",       null, 14937),
            new AgilityObstacle("Gap",          null, 14938),
            new AgilityObstacle("Tree",         null, 14939),
            new AgilityObstacle("Rough Wall",   null, 14940),
            new AgilityObstacle("Monkeybars",   null, 14941),
            new AgilityObstacle("Tree 2",       null, 14944),
            new AgilityObstacle("Drying Line",  null, 14945)
        ));
    }

    public static AgilityCourse prifddinas() {
        return new AgilityCourse("Prifddinas", 75, 1337, Arrays.asList(
            new AgilityObstacle("Ladder",        null, 36221),
            new AgilityObstacle("Tightrope 1",   null, 36225),
            new AgilityObstacle("Chimney",       null, 36227),
            new AgilityObstacle("Roof Edge",     null, 36228),
            new AgilityObstacle("Dark Hole",     null, 36229),
            new AgilityObstacle("Ladder 2",      null, 36231, 36232),
            new AgilityObstacle("Rope Bridge 1", null, 36233),
            new AgilityObstacle("Tightrope 2",   null, 36234),
            new AgilityObstacle("Rope Bridge 2", null, 36235),
            new AgilityObstacle("Tightrope 3",   null, 36236),
            new AgilityObstacle("Tightrope 4",   null, 36237),
            new AgilityObstacle("Dark Hole 2",   null, 36238),
            // Shortcut portals appear at random and skip part of the lap.
            new AgilityObstacle("Portal",        null, 36240, 36241, 36242, 36243, 36244, 36245, 36246)
        ));
    }

    public static AgilityCourse ardougne() {
        return new AgilityCourse("Ardougne", 90, 793, Arrays.asList(
            new AgilityObstacle("Wooden Beams", null, 15608),
            new AgilityObstacle("Gap 1",        null, 15609),
            new AgilityObstacle("Plank",        null, 26635),
            new AgilityObstacle("Gap 2",        null, 15610),
            new AgilityObstacle("Jump",         null, 15611),
            new AgilityObstacle("Steep Roof",   null, 28912),
            new AgilityObstacle("Gap 3",        null, 15612, 28913)
        ));
    }
}
