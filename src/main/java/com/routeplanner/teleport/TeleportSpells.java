package com.routeplanner.teleport;

import net.runelite.api.SpriteID;
import net.runelite.api.coords.WorldPoint;
import java.util.Arrays;
import java.util.List;

public class TeleportSpells {

    public static final List<TeleportSpell> ALL = Arrays.asList(
        new TeleportSpell("Home Teleport",              new WorldPoint(3221, 3219, 0), "STANDARD", SpriteID.SPELL_LUMBRIDGE_HOME_TELEPORT, "home"),
        new TeleportSpell("Teleport to House",          new WorldPoint(0, 0, 0),       "STANDARD", SpriteID.SPELL_TELEPORT_TO_HOUSE, "house"),
        new TeleportSpell("Varrock Teleport",              new WorldPoint(3213, 3429, 0), "STANDARD", SpriteID.SPELL_VARROCK_TELEPORT, "varrock"),
        new TeleportSpell("Lumbridge Teleport",            new WorldPoint(3221, 3219, 0), "STANDARD", SpriteID.SPELL_LUMBRIDGE_TELEPORT, "lumbridge"),
        new TeleportSpell("Falador Teleport",              new WorldPoint(2965, 3379, 0), "STANDARD", SpriteID.SPELL_FALADOR_TELEPORT, "falador"),
        new TeleportSpell("Camelot Teleport",              new WorldPoint(2726, 3485, 0), "STANDARD", SpriteID.SPELL_CAMELOT_TELEPORT, "camelot"),
        new TeleportSpell("Ardougne Teleport",             new WorldPoint(2661, 3304, 0), "STANDARD", SpriteID.SPELL_ARDOUGNE_TELEPORT, "ardougne"),
        new TeleportSpell("Civitas illa Fortis Teleport",  new WorldPoint(1680, 3132, 0), "STANDARD", SpriteID.SPELL_CIVITAS_ILLA_FORTIS_TELEPORT, "civitas"),
        new TeleportSpell("Watchtower Teleport",           new WorldPoint(2586, 3097, 0), "STANDARD", SpriteID.SPELL_WATCHTOWER_TELEPORT, "watchtower"),
        new TeleportSpell("Trollheim Teleport",            new WorldPoint(2889, 3681, 0), "STANDARD", SpriteID.SPELL_TROLLHEIM_TELEPORT, "trollheim"),
        new TeleportSpell("Kourend Castle Teleport",       new WorldPoint(1639, 3673, 0), "STANDARD", SpriteID.SPELL_TELEPORT_TO_KOUREND, "kourend"),
        new TeleportSpell("Salve Graveyard Teleport",      new WorldPoint(3432, 3462, 0), "STANDARD", SpriteID.SPELL_SALVE_GRAVEYARD_TELEPORT, "salve"),
        new TeleportSpell("Fenkenstrain's Castle Teleport", new WorldPoint(3546, 3528, 0), "STANDARD", SpriteID.SPELL_FENKENSTRAINS_CASTLE_TELEPORT, "fenk"),
        new TeleportSpell("West Ardougne Teleport",        new WorldPoint(2500, 3290, 0), "STANDARD", SpriteID.SPELL_WEST_ARDOUGNE_TELEPORT, "westard"),
        new TeleportSpell("Harmony Island Teleport",       new WorldPoint(3794, 2867, 0), "STANDARD", SpriteID.SPELL_HARMONY_ISLAND_TELEPORT, "harmony"),
        new TeleportSpell("Cemetery Teleport",             new WorldPoint(3477, 3504, 0), "STANDARD", SpriteID.SPELL_CEMETERY_TELEPORT, "cemetery"),
        new TeleportSpell("Barrows Teleport",              new WorldPoint(3565, 3314, 0), "STANDARD", SpriteID.SPELL_BARROWS_TELEPORT, "barrows"),
        new TeleportSpell("Ape Atoll Teleport",            new WorldPoint(2757, 2764, 0), "STANDARD", SpriteID.SPELL_TELEPORT_TO_APE_ATOLL, "apeatoll"),
        new TeleportSpell("Paddewwa Teleport",             new WorldPoint(3097, 9882, 0), "ANCIENT", SpriteID.SPELL_PADDEWWA_TELEPORT, "paddewwa"),
        new TeleportSpell("Senntisten Teleport",           new WorldPoint(3320, 3335, 0), "ANCIENT", SpriteID.SPELL_SENNTISTEN_TELEPORT, "senntisten"),
        new TeleportSpell("Kharyrll Teleport",             new WorldPoint(3492, 3474, 0), "ANCIENT", SpriteID.SPELL_KHARYRLL_TELEPORT, "kharyrll"),
        new TeleportSpell("Lassar Teleport",               new WorldPoint(3004, 3469, 0), "ANCIENT", SpriteID.SPELL_LASSAR_TELEPORT, "lassar"),
        new TeleportSpell("Dareeyak Teleport",             new WorldPoint(2970, 3695, 0), "ANCIENT", SpriteID.SPELL_DAREEYAK_TELEPORT, "dareeyak"),
        new TeleportSpell("Carrallanger Teleport",         new WorldPoint(3157, 3666, 0), "ANCIENT", SpriteID.SPELL_CARRALLANGAR_TELEPORT, "carrallanger"),
        new TeleportSpell("Annakarl Teleport",             new WorldPoint(3288, 3888, 0), "ANCIENT", SpriteID.SPELL_ANNAKARL_TELEPORT, "annakarl"),
        new TeleportSpell("Ghorrock Teleport",             new WorldPoint(2977, 3873, 0), "ANCIENT", SpriteID.SPELL_GHORROCK_TELEPORT, "ghorrock"),
        new TeleportSpell("Moonclan Teleport",             new WorldPoint(2113, 3914, 0), "LUNAR", SpriteID.SPELL_MOONCLAN_TELEPORT, "moonclan"),
        new TeleportSpell("Ourania Teleport",              new WorldPoint(2469, 3246, 0), "LUNAR", SpriteID.SPELL_OURANIA_TELEPORT, "ourania"),
        new TeleportSpell("Waterbirth Teleport",           new WorldPoint(2546, 3757, 0), "LUNAR", SpriteID.SPELL_WATERBIRTH_TELEPORT, "waterbirth"),
        new TeleportSpell("Barbarian Teleport",            new WorldPoint(2544, 3570, 0), "LUNAR", SpriteID.SPELL_BARBARIAN_TELEPORT, "barbarian"),
        new TeleportSpell("Khazard Teleport",              new WorldPoint(2634, 3167, 0), "LUNAR", SpriteID.SPELL_KHAZARD_TELEPORT, "khazard"),
        new TeleportSpell("Fishing Guild Teleport",        new WorldPoint(2611, 3393, 0), "LUNAR", SpriteID.SPELL_FISHING_GUILD_TELEPORT, "fishing"),
        new TeleportSpell("Catherby Teleport",             new WorldPoint(2801, 3348, 0), "LUNAR", SpriteID.SPELL_CATHERBY_TELEPORT, "catherby"),
        new TeleportSpell("Ice Plateau Teleport",          new WorldPoint(2976, 3938, 0), "LUNAR", SpriteID.SPELL_ICE_PLATEAU_TELEPORT, "iceplateau"),
        new TeleportSpell("Lumbridge Graveyard Teleport",  new WorldPoint(3232, 3190, 0), "ARCEUUS", SpriteID.SPELL_ARCEUUS_LIBRARY_TELEPORT, "lumbgrave"),
        new TeleportSpell("Draynor Manor Teleport",        new WorldPoint(3109, 3353, 0), "ARCEUUS", SpriteID.SPELL_DRAYNOR_MANOR_TELEPORT, "draynor"),
        new TeleportSpell("Mind Altar Teleport",           new WorldPoint(2980, 3511, 0), "ARCEUUS", SpriteID.SPELL_MIND_ALTAR_TELEPORT, "mindaltar"),
        new TeleportSpell("Respawn Teleport",              new WorldPoint(3221, 3219, 0), "ARCEUUS", SpriteID.SPELL_RESPAWN_TELEPORT, "respawn"),
        new TeleportSpell("Salve Graveyard Teleport",      new WorldPoint(3432, 3462, 0), "ARCEUUS", SpriteID.SPELL_SALVE_GRAVEYARD_TELEPORT, "salve"),
        new TeleportSpell("West Ardougne Teleport",        new WorldPoint(2500, 3290, 0), "ARCEUUS", SpriteID.SPELL_WEST_ARDOUGNE_TELEPORT, "westard"),
        new TeleportSpell("Cemetery Teleport",             new WorldPoint(3477, 3504, 0), "ARCEUUS", SpriteID.SPELL_CEMETERY_TELEPORT, "cemetery"),
        new TeleportSpell("Barrows Teleport",              new WorldPoint(3565, 3314, 0), "ARCEUUS", SpriteID.SPELL_BARROWS_TELEPORT, "barrows"),
        new TeleportSpell("Ape Atoll Teleport",            new WorldPoint(2757, 2764, 0), "ARCEUUS", SpriteID.SPELL_APE_ATOLL_TELEPORT, "apeatoll")
    );

    public static TeleportSpell getByName(String name) {
        return ALL.stream()
            .filter(t -> t.getName().equals(name))
            .findFirst().orElse(null);
    }
}
