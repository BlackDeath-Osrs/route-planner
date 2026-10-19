package com.routeplanner.record;

import com.routeplanner.model.RouteStep;
import com.routeplanner.sailing.CatchMessages;
import com.routeplanner.sailing.Sailing;
import com.routeplanner.sailing.ShoalRoutes;
import com.routeplanner.util.LocationNames;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The Sailing half of the activity recorder: activities that pay out long after the click that
 * started them, so they run as sessions rather than a click window.
 *
 *  - Salvaging: deploying a hook by a wreck; its XP is one "Merchant salvage" step while the boat stays.
 *  - Sorting salvage at a station: its own "Sort Opulent salvage" step (no tile at sea).
 *  - Trawling: the net's catches (the player's or a crewmate's) make one "Catch 120 Marlin" step at a
 *    stop on the shoal's route; its XP belongs to it and makes no step of its own.
 *
 * Salvaging and sorting alternate (and the crew gets talked to in between), so each wreck keeps its
 * own two steps and the XP goes back to the right one. Sailing XP from travelling (wind motes, the helm,
 * the sails) isn't training; only a recent click on a wreck counts outside these sessions.
 */
final class SailingActivity {

    /** Step targets: labels, not things in the world to highlight. */
    static final String SALVAGING = "Salvaging";
    static final String SORTING = "Sorting salvage";

    /** Same session if the boat stays within this many tiles of where it began. */
    private static final int SESSION_RADIUS = 15;
    /** How long sorting XP may pause before the session is over. */
    private static final int SORTING_GAP_TICKS = 30;
    /** A shoal can sail on between stops for a while; this long without trawl XP ends the session. */
    private static final int TRAWL_GAP_TICKS = 100;
    /** How far from the boat to look for the shipwreck being salvaged. */
    private static final int WRECK_RADIUS = 15;
    /** How near the boat must be to a shoal's route for the trawl to be on it. */
    private static final int SHOAL_ROUTE_TILES = 20;
    private static final String WAIT_FOR_SHOAL = "Wait here for the shoal.";
    /** Sailing XP that is training rather than getting about, by what was clicked. */
    private static final String[] TRAINING_TARGETS = {"salvag", "shipwreck", "wreck"};

    private final Client client;
    private final RouteRecorder host;

    /** Where a salvaging hook was deployed, and the wreck beside it (e.g. "Large shipwreck"). */
    private WorldPoint salvageAt;
    private String salvageWreck;
    /** Where a salvaging station was used, when its XP last came, and what's being sorted. */
    private WorldPoint sortingAt;
    private int sortingTick = -1000;
    private String sortingItem;
    /** Whether a trawling net is in use, and when its XP last came. */
    private boolean trawling;
    private int trawlXpTick = -1000;
    /** This wreck's Salvage and Sort steps, the trawl's step, and the XP on each so far. */
    private RouteStep salvageStep, sortStep, trawlStep;
    private final Map<RouteStep, Long> sessionXp = new IdentityHashMap<>();

    SailingActivity(Client client, RouteRecorder host) {
        this.client = client;
        this.host = host;
    }

    void start() {
        salvageAt = null;
        salvageWreck = null;
        sortingAt = null;
        trawling = false;
        salvageStep = sortStep = trawlStep = null;
        sessionXp.clear();
    }

    /** An NPC or object was clicked: a salvaging hook, a trawling net or a salvaging station starts a session. */
    void onTargetClick(String option, String target, WorldPoint at, int tick) {
        String t = target == null ? "" : target.toLowerCase();
        if ("Deploy".equalsIgnoreCase(option) && t.contains("salvaging hook")) {
            // Another wreck (or the first): its own Salvage and Sort steps
            if (salvageAt == null || at == null || at.distanceTo2D(salvageAt) > SESSION_RADIUS) salvageStep = sortStep = null;
            salvageAt = at;
            salvageWreck = nearestShipwreck(at);
            host.trace("SALVAGE session started at " + at + " wreck=" + salvageWreck);
        } else if (t.endsWith("trawling net")) {
            startTrawling(tick);
        } else if (t.contains("salvaging station")) {
            sortingAt = at;
            sortingTick = tick;
            sortingItem = salvageInInventory();
            host.trace("SORTING started at " + at + " item=" + sortingItem);
        }
    }

    /** "Lower net" in the Sailing panel: trawling is on. */
    void onLowerNet(int tick) {
        startTrawling(tick);
    }

    /**
     * Whether XP of this skill belongs to the trawl (the net's Sailing XP, the catches' Fishing XP): it
     * keeps the session going but makes no step, since the catches do (see onCatch).
     */
    boolean absorbTrawlXp(Skill s, int tick) {
        if ((s != Skill.SAILING && s != Skill.FISHING) || !trawling(tick)) return false;
        trawlXpTick = tick;
        return true;
    }

    /**
     * What session this Sailing XP belongs to: SORTING or SALVAGING, or null for neither. Checking it
     * ends sessions the player has left.
     */
    String session(WorldPoint pos, int tick) {
        if (sorting(pos, tick)) return SORTING;
        if (salvaging(pos)) return SALVAGING;
        return null;
    }

    /** Whether a recent click on {@code targetOption targetName} makes Sailing XP training (a wreck). */
    static boolean trainingClick(String targetOption, String targetName) {
        String what = ((targetOption == null ? "" : targetOption) + " " + (targetName == null ? "" : targetName)).toLowerCase();
        for (String k : TRAINING_TARGETS) if (what.contains(k)) return true;
        return false;
    }

    /** Sorting on the boat can be done anywhere at sea, so that step gets no tile; at a port it does. */
    boolean anywhere(String session) {
        return SORTING.equals(session) && Sailing.isSailing(client);
    }

    /** This session's step if it already has one (still a draft), to go back to; otherwise null. */
    RouteStep sessionStep(String session) {
        RouteStep s = SALVAGING.equals(session) ? salvageStep : SORTING.equals(session) ? sortStep : null;
        return s != null && host.hasDraft(s) ? s : null;
    }

    /** The XP on a session step so far. */
    long xp(RouteStep step) {
        return sessionXp.getOrDefault(step, 0L);
    }

    /** A new step was begun for this session. */
    void began(String session, RouteStep step) {
        if (SALVAGING.equals(session)) salvageStep = step;
        if (SORTING.equals(session)) sortStep = step;
    }

    /** The step's XP has grown; kept if it's a session step, to carry on with after a switch. */
    void recordXp(RouteStep step, long xp) {
        if (step != null && (step == salvageStep || step == sortStep)) sessionXp.put(step, xp);
    }

    /** Sets up a new session step: what it highlights. */
    void setUp(String session, RouteStep step) {
        if (SALVAGING.equals(session) && salvageWreck != null) step.setSkillingTargetObject(salvageWreck);
        if (SORTING.equals(session)) step.setSkillingTargetObject("Salvaging station");
    }

    /** The name for a session step, e.g. "Merchant salvage at ..." or "Sort Opulent salvage"; null if not one. */
    String name(String session, RouteStep step, WorldPoint stepPos, WorldPoint where) {
        if (SALVAGING.equals(session)) {
            // "Merchant shipwreck" -> "Merchant salvage"
            String wreck = step.getSkillingTargetObject();
            String kind = wreck != null && wreck.toLowerCase().endsWith(" shipwreck")
                ? wreck.substring(0, wreck.length() - " shipwreck".length()) + " salvage" : "Salvage";
            return kind + LocationNames.at(where);
        }
        if (SORTING.equals(session)) {
            String what = sortingItem != null ? sortingItem : "salvage";
            return stepPos == null ? "Sort " + what : "Sort " + what + LocationNames.at(where);
        }
        return null;
    }

    /** True while trawling, so waiting for the shoal (or stopping the boat) isn't a new stop. */
    boolean isBusy(int tick) {
        return trawling && tick - trawlXpTick <= TRAWL_GAP_TICKS;
    }

    /**
     * Fish caught by the trawling net (the player's or a crewmate's): they go on this trawl's step as a
     * catch goal, "Catch 120 Giant krill". The step waits for the shoal at a stop on its route (the one
     * nearest where the trawl began), since it swims on and comes back there. A catch means trawling,
     * even if the nets were already down when recording started. Returns whether a new step was made.
     */
    boolean onCatch(String fish, int count, WorldPoint pos, int tick) {
        startTrawling(tick);
        boolean fresh = trawlStep == null || !host.hasDraft(trawlStep)
            || (trawlStep.getCatchFish() != null && !CatchMessages.sameFish(fish, trawlStep.getCatchFish()));
        if (fresh) {
            WorldPoint meet = shoalStopNear(pos);
            trawlStep = host.stepAt(meet, "Trawl" + LocationNames.at(meet), null);
            trawlStep.setNoteText(WAIT_FOR_SHOAL);
            trawlStep.setNoteAutoComplete(true);
            trawlStep.setCatchFish(fish);
            trawlStep.setCatchGoal(0);
        }
        trawlStep.setCatchGoal(trawlStep.getCatchGoal() + count);
        trawlStep.setName("Catch " + trawlStep.getCatchGoal() + " " + trawlStep.getCatchFish());
        host.trace("CATCH +" + count + " " + fish + " -> " + trawlStep.getName());
        return fresh;
    }

    private void startTrawling(int tick) {
        if (!trawling) host.trace("TRAWL session started");
        trawling = true;
        trawlXpTick = tick;
    }

    /** Whether the trawling session is still going: XP from it came recently and we're still aboard. */
    private boolean trawling(int tick) {
        if (!trawling) return false;
        if (tick - trawlXpTick <= TRAWL_GAP_TICKS && Sailing.isSailing(client)) return true;
        host.trace("TRAWL session ended");
        trawling = false;
        trawlStep = null;
        return false;
    }

    /** Whether a salvaging session is running here: a hook was deployed and the boat is still by the wreck. */
    private boolean salvaging(WorldPoint pos) {
        if (salvageAt == null) return false;
        if (pos != null && pos.distanceTo2D(salvageAt) <= SESSION_RADIUS) return true;
        host.trace("SALVAGE session ended (boat left " + salvageAt + ")");
        salvageAt = null;
        salvageStep = sortStep = null;
        return false;
    }

    /**
     * Whether salvage is being sorted: a salvaging station was used nearby and its XP keeps coming
     * (each drop extends it). Walking or sailing off, a long pause, or running out of salvage to
     * sort ends it, so a wreck's own XP doesn't keep it going.
     */
    private boolean sorting(WorldPoint pos, int tick) {
        if (sortingAt == null) return false;
        if (tick - sortingTick <= SORTING_GAP_TICKS
                && (pos == null || pos.distanceTo2D(sortingAt) <= SESSION_RADIUS)) {
            sortingTick = tick;
            // The last piece's XP can arrive after it left the inventory: count it, then stop.
            if (salvageInInventory() == null) {
                host.trace("SORTING ended (no salvage left)");
                sortingAt = null;
            }
            return true;
        }
        host.trace("SORTING ended");
        sortingAt = null;
        return false;
    }

    /** The first salvage item in the inventory, e.g. "Opulent salvage", or null. */
    private String salvageInInventory() {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return null;
        for (Item item : inv.getItems()) {
            if (item.getId() <= 0) continue;
            String name = client.getItemDefinition(item.getId()).getName();
            if (name != null && name.toLowerCase().endsWith(" salvage")) return name;
        }
        return null;
    }

    /** The name of the shipwreck object nearest {@code at} (all eight end in "shipwreck"), or null. */
    private String nearestShipwreck(WorldPoint at) {
        if (at == null) return null;
        WorldView wv = client.getTopLevelWorldView();
        LocalPoint c = wv == null ? null : LocalPoint.fromWorld(wv, at);
        if (c == null) return null;
        Tile[][] tiles = wv.getScene().getTiles()[wv.getPlane()];
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int x = Math.max(0, c.getSceneX() - WRECK_RADIUS); x <= Math.min(tiles.length - 1, c.getSceneX() + WRECK_RADIUS); x++) {
            for (int y = Math.max(0, c.getSceneY() - WRECK_RADIUS); y <= Math.min(tiles[x].length - 1, c.getSceneY() + WRECK_RADIUS); y++) {
                Tile t = tiles[x][y];
                if (t == null) continue;
                for (GameObject obj : t.getGameObjects()) {
                    if (obj == null) continue;
                    ObjectComposition def = client.getObjectDefinition(obj.getId());
                    if (def != null && def.getImpostorIds() != null) def = def.getImpostor();
                    String name = def == null ? null : def.getName();
                    if (name == null || !name.toLowerCase().endsWith("shipwreck")) continue;
                    int d = Math.max(Math.abs(x - c.getSceneX()), Math.abs(y - c.getSceneY()));
                    if (d < bestDist) { bestDist = d; best = name; }
                }
            }
        }
        return best;
    }

    /** The stop on the shoal's route nearest {@code pos} (where the boat is trawling), or {@code pos}. */
    private static WorldPoint shoalStopNear(WorldPoint pos) {
        if (pos == null) return null;
        ShoalRoutes.Route route = ShoalRoutes.find(-1, pos, SHOAL_ROUTE_TILES);
        if (route == null) return pos;
        WorldPoint best = pos;
        int bestDist = Integer.MAX_VALUE;
        for (WorldPoint stop : route.stops) {
            int d = stop.distanceTo2D(pos);
            if (d < bestDist) { bestDist = d; best = stop; }
        }
        return best;
    }
}
