package com.routeplanner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import java.awt.Color;

@ConfigGroup("routeplanner")
public interface RoutePlannerConfig extends Config {

    @ConfigSection(
        name = "HUD Custom Configuration",
        description = "Per-element HUD colors, transparency, and outline (used when HUD Theme = Custom)",
        position = 90,
        closedByDefault = true
    )
    String hudCustomSection = "hudCustomSection";

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

    @ConfigItem(
        keyName = "hudFontFamily",
        name = "HUD Font",
        description = "Font family for the HUD. Note: RuneScape fonts are fixed-size (use RuneScape Small for a smaller look); the size slider affects system fonts (Arial/Serif/etc.)."
    )
    default HudFont hudFontFamily() { return HudFont.ARIAL; }

    // ---- HUD Custom Configuration section (used when HUD Theme = Custom) ----
    @Alpha
    @ConfigItem(
        keyName = "hudBgColor",
        position = 91,
        name = "Body Color",
        description = "HUD background/body color (alpha = transparency).",
        section = hudCustomSection
    )
    default Color hudBgColor() { return new Color(52, 40, 24, 64); }

    @ConfigItem(
        keyName = "hudOutline",
        position = 92,
        name = "Show Outline",
        description = "Draw the HUD border outline.",
        section = hudCustomSection
    )
    default boolean hudOutline() { return true; }

    @Alpha
    @ConfigItem(
        keyName = "hudBorderColor",
        position = 93,
        name = "Outline Color",
        description = "HUD border/outline color.",
        section = hudCustomSection
    )
    default Color hudBorderColor() { return new Color(160, 130, 85, 180); }

    @Range(min = 1, max = 5)
    @ConfigItem(
        keyName = "hudBorderThickness",
        position = 94,
        name = "Outline Thickness",
        description = "HUD border thickness in pixels (1-5).",
        section = hudCustomSection
    )
    default int hudBorderThickness() { return 1; }

    @ConfigItem(
        keyName = "hudTitleColor",
        position = 95,
        name = "Title Color",
        description = "HUD title text color.",
        section = hudCustomSection
    )
    default Color hudTitleColor() { return new Color(255, 152, 31); }

    @ConfigItem(
        keyName = "hudCurrentColor",
        position = 96,
        name = "Current Step Color",
        description = "Color of the current step text.",
        section = hudCustomSection
    )
    default Color hudCurrentColor() { return new Color(255, 235, 200); }

    @ConfigItem(
        keyName = "hudNextColor",
        position = 97,
        name = "Next Steps Color",
        description = "Color of upcoming step text.",
        section = hudCustomSection
    )
    default Color hudNextColor() { return new Color(190, 170, 140); }

    @ConfigItem(
        keyName = "hudHaveColor",
        position = 98,
        name = "Item Have Color",
        description = "Color shown when you have a required item.",
        section = hudCustomSection
    )
    default Color hudHaveColor() { return new Color(110, 210, 90); }

    @ConfigItem(
        keyName = "hudDimColor",
        position = 99,
        name = "Secondary Text Color",
        description = "Color of dim/secondary text.",
        section = hudCustomSection
    )
    default Color hudDimColor() { return new Color(155, 135, 105); }

    @Alpha
    @ConfigItem(
        keyName = "hudDividerColor",
        position = 100,
        name = "Divider Color",
        description = "Color of divider lines (alpha = transparency).",
        section = hudCustomSection
    )
    default Color hudDividerColor() { return new Color(185, 145, 85, 45); }

    @ConfigItem(
        keyName = "hudMatchInterface",
        position = 101,
        name = "Match HUD to interface",
        description = "Auto-generate a matching HUD palette from your active resource pack's overlay color (sets Theme to Custom). Tick to apply.",
        section = hudCustomSection
    )
    default boolean hudMatchInterface() { return false; }
}
