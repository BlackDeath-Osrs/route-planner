package com.routeplanner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import java.awt.Color;

@ConfigGroup(RoutePlannerConfig.GROUP)
public interface RoutePlannerConfig extends Config {

    /** The config group everything is saved under, including the routes themselves. */
    String GROUP = "routeplanner";

    @ConfigSection(
        name = "HUD Custom Configuration",
        description = "Per-element HUD colors, transparency, and outline (used when HUD Theme = Custom)",
        position = 90,
        closedByDefault = true
    )
    String hudCustomSection = "hudCustomSection";

    @ConfigSection(
        name = "Waypoint Path",
        description = "How the pathfinder route is drawn: line or tile style, colors, and animation",
        position = 80,
        closedByDefault = true
    )
    String waypointSection = "waypointSection";

    @ConfigSection(
        name = "Record mode",
        description = "How recording turns what you do in game into draft steps",
        position = 95,
        closedByDefault = true
    )
    String recordSection = "recordSection";

    @ConfigSection(
        name = "Beta features",
        description = "Features still being worked on. They may change, or not always behave yet.",
        position = 1000,
        closedByDefault = true
    )
    String betaSection = "betaSection";

    @ConfigSection(
        name = "Sailing",
        description = "Routes by boat, docks, salvaging and trawling.",
        position = 97,
        closedByDefault = true
    )
    String sailingSection = "sailingSection";

    @ConfigItem(
        keyName = "betaFeatures",
        name = "Enable beta features",
        description = "Turns on features that are still being tried out: drawn areas on location steps"
            + " (Draw area in the step editor).",
        position = 0,
        section = betaSection
    )
    default boolean betaFeatures() { return false; }

    @ConfigItem(
        keyName = "routes",
        name = "",
        description = "",
        hidden = true
    )
    default String routes() { return ""; }

    @ConfigItem(
        keyName = "hubNetworkDisclosure",
        name = "Route Hub network use",
        description = "The Route Hub (Import -> Browse Route Hub) fetches route data from a "
            + "GitHub-hosted repository over plain, read-only HTTP GET requests. No player "
            + "information or account data is ever sent. Sharing a route the other way is a "
            + "manual GitHub pull request you open yourself -- the plugin never uploads anything.",
        position = 1
    )
    default boolean hubNetworkDisclosure() { return true; }

    @ConfigItem(
        keyName = "mode",
        name = "Mode",
        description = "Developer mode lets you create and edit routes. Player mode only allows importing and playing a route.",
        position = 0,
        hidden = true
    )
    default RouteMode mode() { return RouteMode.DEVELOPER; }

    @ConfigItem(
        keyName = "hudTheme",
        name = "HUD Theme",
        description = "Color theme for the on-screen HUD. 'OSRS Brown' is the custom theme; 'Quest Helper style' matches RuneLite's standard overlay look.",
        position = 1
    )
    default HudTheme hudTheme() { return HudTheme.OSRS_BROWN; }

    @ConfigItem(
        keyName = "arrivalHideDelay",
        name = "Arrival Hide Delay",
        description = "Increase the tick value 0-30 for the delay. 0 = hide on arrival, 30 = Always On.",
        position = 3
    )
    @Range(min = 0, max = 30)
    default int arrivalHideDelay() { return 0; }

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

    // ---- Waypoint Path section ----
    @ConfigItem(
        keyName = "pathfinder",
        name = "Pathfinder",
        description = "Which pathfinder builds the waypoint path. Shortest Path uses the Shortest Path"
            + " plugin's routing (including any teleports its own settings allow) and falls back to"
            + " the built-in pathfinder when that plugin isn't installed or enabled.",
        position = 199,
        section = waypointSection
    )
    default PathfinderMode pathfinder() { return PathfinderMode.BUILT_IN; }

    @ConfigItem(
        keyName = "shoalRoutesAlways",
        name = "Always show shoal routes",
        description = "Draw every trawling shoal's route and stops in view, not only while your nets are down"
            + " by a shoal.",
        position = 1,
        section = sailingSection
    )
    default boolean shoalRoutesAlways() { return false; }

    @ConfigItem(
        keyName = "wreckAlert",
        name = "Salvage Alert",
        description = "Sound that plays when not actively salvaging",
        position = 2,
        section = sailingSection
    )
    default WreckAlert wreckAlert() { return WreckAlert.BOSUNS_CALL; }

    @Range(min = 0, max = 100)
    @ConfigItem(
        keyName = "wreckAlertVolume",
        name = "Alert Volume",
        description = "",
        position = 3,
        section = sailingSection
    )
    default int wreckAlertVolume() { return 70; }

    @ConfigItem(
        keyName = "pathDisplayMode",
        name = "Display mode",
        description = "Draw the route as a line, highlighted tiles, or both.",
        position = 200,
        section = waypointSection
    )
    default PathDisplayMode pathDisplayMode() { return PathDisplayMode.LINE; }

    @ConfigItem(
        keyName = "lineStyle",
        name = "Line style",
        description = "Stroke style for the waypoint line.",
        position = 201,
        section = waypointSection
    )
    default LineStyle lineStyle() { return LineStyle.TRACER; }

    @Alpha
    @ConfigItem(
        keyName = "lineColor",
        name = "Line color",
        description = "Main color of the waypoint line (alpha = transparency).",
        position = 202,
        section = waypointSection
    )
    default Color lineColor() { return new Color(255, 140, 0, 220); }

    @ConfigItem(
        keyName = "lineUseDashColor",
        name = "Separate dash color",
        description = "Draw the dashes/dots (or the tracer pulse) in their own color over a faint base line.",
        position = 203,
        section = waypointSection
    )
    default boolean lineUseDashColor() { return false; }

    @Alpha
    @ConfigItem(
        keyName = "lineDashColor",
        name = "Dash / dot color",
        description = "Color of the dashes/dots when 'Separate dash color' is on.",
        position = 204,
        section = waypointSection
    )
    default Color lineDashColor() { return new Color(255, 238, 85, 230); }

    @Range(min = 1, max = 10)
    @ConfigItem(
        keyName = "lineThickness",
        name = "Line thickness",
        description = "Waypoint line thickness in pixels.",
        position = 205,
        section = waypointSection
    )
    default int lineThickness() { return 3; }

    @ConfigItem(
        keyName = "lineGlow",
        name = "Glow / outline",
        description = "Draw a soft wider glow under the line for visibility.",
        position = 206,
        section = waypointSection
    )
    default boolean lineGlow() { return true; }

    @Range(min = 0, max = 60)
    @ConfigItem(
        keyName = "tracerSpeed",
        name = "Tracer speed",
        description = "How fast the tracer pulse runs along the line, in tiles per second (Tracer style).",
        position = 207,
        section = waypointSection
    )
    default int tracerSpeed() { return 30; }

    @Range(min = 0, max = 100)
    @ConfigItem(
        keyName = "tracerIntensity",
        name = "Tracer intensity",
        description = "How much dimmer the line is than the pulse (Tracer style). 0 = no difference.",
        position = 208,
        section = waypointSection
    )
    default int tracerIntensity() { return 50; }

    @ConfigItem(
        keyName = "lineAnimate",
        name = "Animate flow",
        description = "Move the dashes, dots or tracer pulse along the line toward the destination (not the solid style).",
        position = 209,
        section = waypointSection
    )
    default boolean lineAnimate() { return true; }

    @Alpha
    @ConfigItem(
        keyName = "pathFollowDrawDistance",
        name = "Follow draw distance",
        description = "Only draw the path within the client's current draw distance. Turn off to set your own limit.",
        position = 210,
        section = waypointSection
    )
    default boolean pathFollowDrawDistance() { return true; }

    @Range(min = 5, max = 100)
    @ConfigItem(
        keyName = "pathMaxDistance",
        name = "Max path distance",
        description = "How many tiles ahead to draw the path when 'Follow draw distance' is off.",
        position = 211,
        section = waypointSection
    )
    default int pathMaxDistance() { return 40; }

    @ConfigItem(
        keyName = "tileFillColor",
        name = "Tile fill color",
        description = "Fill color for path tiles (Tiles/Both modes).",
        position = 220,
        section = waypointSection
    )
    default Color tileFillColor() { return new Color(255, 140, 0, 90); }

    @Alpha
    @ConfigItem(
        keyName = "tileBorderColor",
        name = "Tile border color",
        description = "Border color for path tiles.",
        position = 221,
        section = waypointSection
    )
    default Color tileBorderColor() { return new Color(255, 170, 0, 200); }

    @Range(min = 0, max = 4)
    @ConfigItem(
        keyName = "tileBorderThickness",
        name = "Tile border thickness",
        description = "Path tile border thickness in pixels (0 = no border).",
        position = 222,
        section = waypointSection
    )
    default int tileBorderThickness() { return 1; }

    @ConfigItem(
        keyName = "tileRounded",
        name = "Rounded tiles",
        description = "Round the corners of path tiles.",
        position = 223,
        section = waypointSection
    )
    default boolean tileRounded() { return false; }

    @ConfigItem(
        keyName = "tilePulse",
        name = "Animate tile pulse",
        description = "Gently pulse the path tiles.",
        position = 224,
        section = waypointSection
    )
    default boolean tilePulse() { return false; }

    @ConfigItem(
        keyName = "tileFade",
        name = "Fade far tiles",
        description = "Dim path tiles further from you for a sense of direction.",
        position = 225,
        section = waypointSection
    )
    default boolean tileFade() { return true; }

    // ---- Record mode section ----
    @Range(min = 2, max = 30)
    @ConfigItem(
        keyName = "recordIdleTicks",
        name = "Stop length for a step",
        description = "While recording, standing still this many game ticks (0.6s each) somewhere new"
            + " records a location step there.",
        position = 300,
        section = recordSection
    )
    default int recordIdleTicks() { return 5; }

    @ConfigItem(
        keyName = "recordAlwaysHighlight",
        name = "Highlight recorded targets",
        description = "Turn on Highlight for recorded steps that have an NPC or object to highlight"
            + " (trees, rocks, fishing spots, NPCs you killed or pickpocketed...). Off: you can"
            + " still turn it on per step in the review.",
        position = 301,
        section = recordSection
    )
    default boolean recordAlwaysHighlight() { return true; }

    @ConfigItem(
        keyName = "recordTrace",
        name = "Bug report log",
        description = "Only needed if you're reporting a problem with recording or Sailing: writes what the"
            + " plugin sees and decides to RuneLite's log file (client.log, lines start with [RP-TRACE])."
            + " It stays on your computer unless you send it. Leave off otherwise.",
        position = 302,
        section = recordSection
    )
    default boolean recordTrace() { return false; }

    @ConfigItem(
        keyName = "waypointPreview",
        name = "Preview in game",
        description = "Draw a short demo path near you using the current settings, even without an active route. Tick on to preview, untick to stop.",
        position = 230,
        section = waypointSection
    )
    default boolean waypointPreview() { return false; }
}
