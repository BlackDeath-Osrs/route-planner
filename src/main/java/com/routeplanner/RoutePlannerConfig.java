package com.routeplanner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Alpha;
import java.awt.Color;

@ConfigGroup("routeplanner")
public interface RoutePlannerConfig extends Config {

    @ConfigItem(
        keyName = "routes",
        name = "",
        description = "",
        hidden = true
    )
    default String routes() { return ""; }

    @ConfigItem(
        keyName = "mode",
        name = "Mode",
        description = "Developer mode lets you create and edit routes. Player mode only allows importing and playing a route.",
        position = 0
    )
    default RouteMode mode() { return RouteMode.DEVELOPER; }

    @ConfigItem(
        keyName = "hudTheme",
        name = "HUD Theme",
        description = "Color theme for the on-screen HUD. 'OSRS Brown' is the custom theme; 'Quest Helper style' matches RuneLite's standard overlay look.",
        position = 1
    )
    default HudTheme hudTheme() { return HudTheme.OSRS_BROWN; }

    @Alpha
    @ConfigItem(
        keyName = "tileHighlightColor",
        name = "Tile Highlight Color",
        description = "Color for the active step tile highlight"
    )
    default Color tileHighlightColor() { return new Color(0, 255, 0, 100); }

    @Alpha
    @ConfigItem(
        keyName = "npcHighlightColor",
        name = "NPC/Object Highlight Color",
        description = "Color for NPC and object outlines"
    )
    default Color npcHighlightColor() { return new Color(255, 165, 0, 200); }

    @Alpha
    @ConfigItem(
        keyName = "minimapColor",
        name = "Minimap Marker Color",
        description = "Color for minimap markers"
    )
    default Color minimapColor() { return Color.GREEN; }

    @ConfigItem(
        keyName = "hudFontSize",
        name = "HUD Font Size",
        description = "Font size for the XP tracker HUD"
    )
    default int hudFontSize() { return 12; }

    @ConfigItem(
        keyName = "hudFontBold",
        name = "HUD Bold Font",
        description = "Use bold font in HUD"
    )
    default boolean hudFontBold() { return true; }
}
