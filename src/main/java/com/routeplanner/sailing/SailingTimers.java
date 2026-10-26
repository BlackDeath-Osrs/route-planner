package com.routeplanner.sailing;

import com.routeplanner.RoutePlannerConfig;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Countdowns and the voyage time for the HUD while sailing:
 *
 *  - "Shoal moves in 0:32": a shoal that's stopped stays for a set time by its fish, counted from
 *    when it was seen to stop (as Maewi's Deep Sea Trawling plugin does).
 *  - The wrecks sunk alert: every shipwreck being salvaged (each one in hook reach, so a crewmate's hook
 *    on a second wreck counts too) has sunk, so it's time to move on. Wrecks last different times, so a
 *    sound when they're all gone beats a countdown for each.
 *  - "Arrive in 1:23": the sea route left at the boat's speed (halfway between its base speed and its
 *    speed cap), shown while it's under way.
 *
 * Each counts down second by second from when it will end, rather than jumping a tick at a time.
 */
@Singleton
public class SailingTimers {

    /** Only things this close to the boat get a countdown. */
    private static final int NEAR_TILES = 30;
    /** How near the hull a shipwreck must be for a hook to reach it, in tiles. */
    private static final int HOOK_REACH = 4;
    /** How far from the boat to look for shipwrecks. */
    private static final int WRECK_RADIUS = 12;
    /** Points further apart than this on the route are a jump (a cave mouth), not sailed. */
    private static final int MAX_STEP_TILES = 5;
    /** How quickly the measured speed follows a change: 0..1, higher is quicker. */
    private static final double SPEED_SMOOTHING = 0.25;
    /** Below this many tiles a tick the boat isn't really under way, and an arrival time would be silly. */
    private static final double MIN_SPEED = 0.3;
    /** A game tick, in milliseconds. */
    private static final long TICK_MS = 600;
    /** The arrival time is only re-estimated when it's off by more than this (or 15%). */
    private static final long ETA_SLACK_MS = 3000;

    private final Client client;
    private final RoutePlannerConfig config;

    private int shoalId = -1;
    private WorldPoint shoalLast;
    private int movingTicks, stoppedTicks;
    private boolean shoalWasMoving;
    /** When each countdown ends, in wall-clock milliseconds, so the HUD counts down second by second. */
    private long shoalMovesAt = -1;

    /** The wrecks being salvaged (in hook reach since a hook went down or salvage came in), by their tile. */
    private final Set<WorldPoint> wrecks = new LinkedHashSet<>();
    private final AudioPlayer audioPlayer;
    private final Notifier notifier;

    private WorldPoint boatLast;
    private double measured;   // tiles per tick, over the last few ticks
    /** The expected arrival, in wall-clock milliseconds; re-estimated only when it's clearly off. */
    private long arrivalAt = -1;

    @Inject
    public SailingTimers(Client client, RoutePlannerConfig config, AudioPlayer audioPlayer,
                         Notifier notifier) {
        this.client = client;
        this.config = config;
        this.audioPlayer = audioPlayer;
        this.notifier = notifier;
    }

    /** Once a tick, from the plugin: follows the nearest shoal, the wreck being salvaged and the boat's speed. */
    public void onGameTick() {
        if (!Sailing.isSailing(client)) {
            boatLast = null;
            measured = 0;
            arrivalAt = -1;
            return;
        }
        int tick = client.getTickCount();
        WorldPoint boat = Sailing.position(client);
        if (boat != null && boatLast != null) {
            double moved = Math.hypot(boat.getX() - boatLast.getX(), boat.getY() - boatLast.getY());
            if (moved <= MAX_STEP_TILES) measured += (moved - measured) * SPEED_SMOOTHING;
        }
        boatLast = boat;
        followShoal(tick);
        boolean sank = false;
        Iterator<WorldPoint> it = wrecks.iterator();
        while (it.hasNext()) {
            WorldPoint wreck = it.next();
            if (!activeWreckAt(wreck)) { sank = true; it.remove(); }
            else if (!near(wreck, boat)) it.remove();   // sailed off: no alert for those
        }
        // The last one being salvaged just sank, and there's nothing else in reach to salvage
        if (sank && wrecks.isEmpty() && wrecksInReach().isEmpty()) wrecksSunkAlert();
    }

    /** Every wreck being salvaged has sunk: the alert picked in the config. */
    private void wrecksSunkAlert() {
        switch (config.wreckAlert()) {
            case BOSUNS_CALL:
                int volume = config.wreckAlertVolume();
                if (volume <= 0) break;
                try {
                    // The volume setting as gain in decibels: 100 plays the sound as made, 50 about half as loud
                    float gainDb = (float) (20 * Math.log10(Math.min(100, volume) / 100.0));
                    audioPlayer.play(SailingTimers.class, "/com/routeplanner/sailing/bosun-call.wav", gainDb);
                } catch (Exception e) {
                    Toolkit.getDefaultToolkit().beep();   // no sound device for it: at least a beep
                }
                break;
            case SYSTEM:
                Toolkit.getDefaultToolkit().beep();
                break;
            case RUNELITE:
                notifier.notify("All wrecks have sunk");
                break;
            default:
                break;
        }
    }

    /**
     * The boat's sailing speed in tiles per tick: halfway between its base speed and its speed cap (the
     * side panel's stats, in 128ths of a tile a tick), which holds steady where the speed measured
     * tick by tick jumps about. The measured speed when the stats aren't known.
     */
    private double speed() {
        int base = client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_BASESPEED);
        int cap = client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_SPEEDCAP);
        if (base > 0 && cap > 0) return (base + cap) / 2.0 / Perspective.LOCAL_TILE_SIZE;
        if (base > 0) return base / (double) Perspective.LOCAL_TILE_SIZE;
        return measured;
    }

    /**
     * Watches the nearest shoal move and stop. Moving for two ticks then still for two is a stop; the
     * countdown starts then, from how long that shoal's fish stay (two ticks already gone).
     */
    private void followShoal(int tick) {
        WorldEntity shoal = Shoals.nearest(client);
        LocalPoint lp = shoal == null ? null : shoal.getLocalLocation();
        int id = shoal == null || shoal.getWorldView() == null ? -1 : shoal.getWorldView().getId();
        if (lp == null || id != shoalId) {
            shoalId = id;
            shoalLast = null;
            movingTicks = stoppedTicks = 0;
            shoalWasMoving = false;
            shoalMovesAt = -1;
            if (lp == null) return;
        }
        WorldPoint at = WorldPoint.fromLocalInstance(client, lp);
        if (shoalLast != null) {
            if (at.equals(shoalLast)) { stoppedTicks++; movingTicks = 0; }
            else { movingTicks++; stoppedTicks = 0; }
        }
        shoalLast = at;
        if (shoalWasMoving && stoppedTicks >= 2) {
            int stay = ShoalRoutes.stopTicks(ShoalRoutes.find(id, at, 10));
            shoalMovesAt = stay > 0 ? System.currentTimeMillis() + (stay - 2) * TICK_MS : -1;
            shoalWasMoving = false;
        } else if (movingTicks >= 2) {
            shoalWasMoving = true;
            shoalMovesAt = -1;
        }
    }

    /** Deploying a salvaging hook: watch the wrecks it can reach. */
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked e) {
        String option = Text.removeTags(e.getMenuOption()), target = Text.removeTags(e.getMenuTarget()).toLowerCase();
        if ("Deploy".equalsIgnoreCase(option) && target.contains("salvaging hook")) trackWrecks();
    }

    /** Salvage coming in (a crewmate on the hook, say) starts it too, if it hasn't started. */
    @Subscribe
    public void onStatChanged(StatChanged e) {
        if (e.getSkill() == Skill.SAILING && Sailing.isSailing(client)) trackWrecks();
    }

    /** Watches every wreck in hook reach, to know when they've all sunk. */
    private void trackWrecks() {
        wrecks.addAll(wrecksInReach());
    }

    /** The lines to show on the HUD now, e.g. "Arrive in 1:23"; empty when there's nothing to show. */
    public List<String> hudLines(List<WorldPoint> path, SailingDocks docks) {
        List<String> lines = new ArrayList<>();
        if (!Sailing.isSailing(client)) return lines;
        long now = System.currentTimeMillis();
        WorldPoint boat = Sailing.position(client);
        // Only while actually under way, with a fair stretch still to go
        double tilesLeft = sailedTilesLeft(path, docks);
        double speed = speed();
        if (measured >= MIN_SPEED && speed > 0 && tilesLeft > 5) {
            long estimate = now + (long) (tilesLeft / speed * TICK_MS);
            // Keep counting down from the last estimate unless the new one is clearly different
            long left = arrivalAt - now;
            if (arrivalAt < 0 || Math.abs(estimate - arrivalAt) > Math.max(ETA_SLACK_MS, left * 0.15)) arrivalAt = estimate;
            lines.add("Arrive in " + clock(arrivalAt - now));
        } else {
            arrivalAt = -1;
        }
        if (shoalMovesAt > now && near(shoalLast, boat)) lines.add("Shoal moves in " + clock(shoalMovesAt - now));
        return lines;
    }

    /** How far the boat has still to sail on the route: up to where it's left at a dock, not counting jumps. */
    private static double sailedTilesLeft(List<WorldPoint> path, SailingDocks docks) {
        if (path == null) return 0;
        double tiles = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            WorldPoint a = path.get(i), b = path.get(i + 1);
            if (a.getPlane() != b.getPlane() || docks.crossing(a, b) != null) break;
            double d = Math.hypot(b.getX() - a.getX(), b.getY() - a.getY());
            if (d <= MAX_STEP_TILES) tiles += d;
        }
        return tiles;
    }

    private static boolean near(WorldPoint p, WorldPoint boat) {
        return p != null && boat != null && p.distanceTo2D(boat) <= NEAR_TILES;
    }

    /** Milliseconds as m:ss, rounded up so it reaches 0:00 as the time runs out. */
    private static String clock(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    /** The tiles of shipwrecks that can be salvaged and that a hook can reach from the hull. */
    private Set<WorldPoint> wrecksInReach() {
        Set<WorldPoint> out = new LinkedHashSet<>();
        WorldPoint boat = Sailing.position(client);
        WorldView wv = client.getTopLevelWorldView();
        LocalPoint c = boat == null || wv == null ? null : LocalPoint.fromWorld(wv, boat);
        Tile[][][] all = c == null || wv.getScene() == null ? null : wv.getScene().getTiles();
        if (all == null) return out;
        Tile[][] tiles = all[wv.getPlane()];
        for (int x = Math.max(0, c.getSceneX() - WRECK_RADIUS); x <= Math.min(tiles.length - 1, c.getSceneX() + WRECK_RADIUS); x++) {
            for (int y = Math.max(0, c.getSceneY() - WRECK_RADIUS); y <= Math.min(tiles[x].length - 1, c.getSceneY() + WRECK_RADIUS); y++) {
                Tile t = tiles[x][y];
                if (t == null) continue;
                for (GameObject obj : t.getGameObjects()) {
                    // Each wreck once (from its corner tile), if any tile it covers is within reach of the hull
                    if (obj == null || !obj.getSceneMinLocation().equals(t.getSceneLocation()) || !activeWreck(obj)) continue;
                    if (inHookReach(wv, obj)) out.add(obj.getWorldLocation());
                }
            }
        }
        return out;
    }

    private boolean inHookReach(WorldView wv, GameObject obj) {
        Point min = obj.getSceneMinLocation(), max = obj.getSceneMaxLocation();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                WorldPoint tile = new WorldPoint(wv.getBaseX() + x, wv.getBaseY() + y, wv.getPlane());
                int d = Sailing.hullDistance(client, tile);
                if (d >= 0 && d <= HOOK_REACH) return true;
            }
        }
        return false;
    }

    /** Whether there's still a shipwreck that can be salvaged at {@code at} (it sinks when it runs out). */
    private boolean activeWreckAt(WorldPoint at) {
        WorldView wv = client.getTopLevelWorldView();
        LocalPoint lp = wv == null ? null : LocalPoint.fromWorld(wv, at);
        Tile[][][] all = lp == null || wv.getScene() == null ? null : wv.getScene().getTiles();
        if (all == null) return true;   // out of view: can't tell, keep counting
        Tile t = all[wv.getPlane()][lp.getSceneX()][lp.getSceneY()];
        if (t == null) return false;
        for (GameObject obj : t.getGameObjects()) if (obj != null && activeWreck(obj)) return true;
        return false;
    }

    /** A shipwreck with its Inspect option: one that can be salvaged (a sunken one has no options). */
    private boolean activeWreck(GameObject obj) {
        ObjectComposition def = client.getObjectDefinition(obj.getId());
        if (def != null && def.getImpostorIds() != null) def = def.getImpostor();
        if (def == null || def.getName() == null || !def.getName().toLowerCase().endsWith("shipwreck")) return false;
        String[] actions = def.getActions();
        if (actions != null) for (String a : actions) if (a != null && !a.isEmpty()) return true;
        return false;
    }
}
