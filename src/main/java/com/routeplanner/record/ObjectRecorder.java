package com.routeplanner.record;

import com.routeplanner.model.ItemMode;
import com.routeplanner.model.RouteStep;
import com.routeplanner.util.LocationNames;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Object interactions that aren't skilling: searching a crate, opening a chest, inspecting a
 * painting, using a key on a door, taking an agility shortcut, entering a cave. Most give no XP,
 * so the activity recorder never sees them, but routes and quests are full of them.
 *
 *  - A click on an object is remembered until the player stands next to it and stops; then it
 *    becomes "Search Crate" (the menu option and the object), highlighting the object. "Use X ->
 *    Y" becomes "Use X on Y" and outlines X in the inventory.
 *  - Items that turn up right after (a key from the crate) go on that step as a pickup, so it
 *    ticks itself off. A conversation that opens (reading a book, a puzzle) puts its dialogue
 *    choices on it too.
 *  - XP straight after means it was skilling after all: the step is dropped and the activity
 *    recorder's step stands instead. Agility XP is the exception: off an agility course it means
 *    the object was a shortcut (a rope swing, a log), so the shortcut is the step and the XP
 *    isn't an Agility step. A shortcut that carries the player along (a swing) never lets them
 *    stop next to it, so its XP makes the step, at the object.
 *  - An object that moves the player (a shortcut, a cave entrance) can do so the moment they
 *    reach it; the step is then made where they stood before the move.
 *  - Doors, gates, curtains, stairs and ladders (the climb becomes the next step's stairs),
 *    banking, boarding, teleports and agility course obstacles are left to the rest of the
 *    recorder.
 */
class ObjectRecorder {

    /** Give up on a click if the player hasn't reached the object within this many ticks. */
    private static final int REACH_TICKS = 30;
    /** Standing this close (tiles, from the object's tile) counts as at it; big objects need 2. */
    private static final int REACH_TILES = 2;
    /** Items or a conversation this soon after the interaction belong to it. */
    static final int FOLLOW_TICKS = 6;
    /** XP this soon after means the click was skilling. */
    private static final int XP_TICKS = 6;
    /** A long shortcut (a log balance, a pipe) can take this many ticks before its XP comes. */
    private static final int SHORTCUT_XP_TICKS = 20;

    /** Options the rest of the recorder covers: boarding, teleports, banking, trading. */
    private static final Pattern SKIP_OPTION = Pattern.compile(
        "(?i)^(walk here|examine|cancel|board.*|teleport.*|travel|use-quickly|quick-.*|bank|collect|deposit.*|trade)$");
    /** Objects that are plain travel or banking whatever the option. */
    private static final Pattern SKIP_OBJECT = Pattern.compile(
        "(?i).*\\b(door|doors|gate|gates|curtain|curtains|trapdoor|staircase|stairs|ladder|bank booth|bank chest|"
            + "deposit box|portal|gangplank|fairy ring|fairy tree|spirit tree)\\b.*");

    private final Client client;
    private final RouteRecorder host;

    private String option;
    private String target;
    private String used;
    private int clickTick = -1000;
    private int sceneX;
    private int sceneY;
    private WorldPoint objectTile;   // the clicked object's tile (null in instances)

    private RouteStep step;          // the latest interaction's step
    private int stepTick = -1000;
    private boolean stepHadItems;
    private final Map<Skill, Integer> xp = new EnumMap<>(Skill.class);

    ObjectRecorder(Client client, RouteRecorder host) {
        this.client = client;
        this.host = host;
    }

    void start() {
        option = null;
        step = null;
        clickTick = stepTick = -1000;
        xp.clear();
        for (Skill s : Skill.values()) {
            if (s == Skill.OVERALL) continue;
            try {
                xp.put(s, client.getSkillExperience(s));
            } catch (RuntimeException ignored) {
                // a skill the client doesn't report (newer than the API)
            }
        }
    }

    /** Whether this click is one this recorder handles (not plain travel, banking or an examine). */
    static boolean interesting(String option, String target) {
        if (option == null || target == null || target.isEmpty()) return false;
        if (SKIP_OPTION.matcher(option.trim()).matches()) return false;
        // Using an item on a door or gate (a key) is part of the quest; opening one is travel
        return usedItem(target) != null || !SKIP_OBJECT.matcher(objectName(target)).matches();
    }

    /**
     * An object was clicked (any option); scene coordinates are the object's tile. Obstacles of
     * an agility course ({@code courseObstacle}) are the course's lap, not steps.
     */
    void onClick(String option, String target, boolean courseObstacle, WorldPoint objectTile, int sceneX, int sceneY, int tick) {
        if (courseObstacle || !interesting(option, target)) {
            this.option = null;
            return;
        }
        this.option = option.trim();
        this.target = objectName(target);
        this.used = usedItem(target);
        this.objectTile = objectTile;
        this.sceneX = sceneX;
        this.sceneY = sceneY;
        this.clickTick = tick;
        host.trace("OBJECT click '" + this.option + "' on '" + this.target + "'" + (used != null ? " using '" + used + "'" : ""));
    }

    /** The player walked off or went to an NPC instead: a pending object click is off. */
    void onOtherClick() {
        option = null;
    }

    /**
     * Each tick: once the player is next to the clicked object and has stopped, the interaction
     * is a step. {@code playerSceneX/Y} are the player's scene coordinates, which work in
     * instances too.
     */
    void onTick(WorldPoint pos, int playerSceneX, int playerSceneY, boolean moving, int tick) {
        if (option == null) return;
        if (tick - clickTick > REACH_TICKS) {
            host.trace("OBJECT '" + option + " " + target + "' never reached");
            option = null;
            return;
        }
        int d = Math.max(Math.abs(playerSceneX - sceneX), Math.abs(playerSceneY - sceneY));
        // Next to it, or close and stopped (a big object's far side)
        if (d > REACH_TILES || (moving && d > 1)) return;
        makeStep(pos, tick);
    }

    /**
     * The player was moved (a jump, a climb) while a click was pending: the object did it, so the
     * interaction happened at {@code from}, where they stood before.
     */
    void onJump(WorldPoint from, int tick) {
        if (option == null || tick - clickTick > REACH_TICKS) return;
        host.trace("OBJECT '" + option + " " + target + "' moved the player");
        makeStep(from, tick);
    }

    private void makeStep(WorldPoint pos, int tick) {
        String name = used != null ? "Use " + used + " on " + target : cap(option) + " " + target;
        RouteStep last = host.lastDraft();
        // Clicking the same thing again (searching twice, a puzzle): one step
        if (last != null && last == step && name.equals(stripAt(last.getName())) && host.isNear(last, pos, 3)) {
            host.trace("OBJECT repeat of '" + name + "', same step");
            stepTick = tick;
            option = null;
            return;
        }
        RouteStep s = host.stepAt(pos, name + LocationNames.at(pos));
        s.setSkillingTargetObject(target);
        s.setHighlightEnabled(true);
        if (used != null) s.setInventoryHighlightItems(used);
        step = s;
        stepTick = tick;
        stepHadItems = s.hasItems();
        option = null;
        host.trace("OBJECT step " + RouteRecorder.describeStep(s));
    }

    /** The latest interaction's step, if it was just made (a conversation opening goes on it). */
    RouteStep recentStep(int tick) {
        return step != null && tick - stepTick <= FOLLOW_TICKS && host.lastDraft() == step ? step : null;
    }

    /**
     * Items that arrived without a pickup, purchase or withdrawal. True if they went on the
     * interaction's step. Items can turn up before the player has stopped by the object (picking
     * a banana takes a tick); they prove the interaction happened, so they make the step.
     */
    boolean onItemsGained(Map<String, Long> gains, int tick) {
        if (gains.isEmpty()) return false;
        if (recentStep(tick) == null && option != null && tick - clickTick <= REACH_TICKS) makeStep(objectTile, tick);
        RouteStep s = recentStep(tick);
        if (s == null || stepHadItems) return false;
        s.setItemList(addItems(s.getItemMode() == ItemMode.PICKUP ? s.getItemList() : null, gains));
        s.setItemMode(ItemMode.PICKUP);
        host.trace("OBJECT items " + gains + " -> " + RouteRecorder.describeStep(s));
        return true;
    }

    private static final Pattern COUNTED = Pattern.compile("^(\\d+) (.+)$");

    /** An item list with {@code gains} added, same items summed: "Banana" + 1 Banana = "2 Banana". */
    static String addItems(String list, Map<String, Long> gains) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (list != null) {
            for (String e : list.split(",")) {
                String t = e.trim();
                if (t.isEmpty()) continue;
                java.util.regex.Matcher m = COUNTED.matcher(t);
                if (m.matches()) counts.merge(m.group(2), Long.parseLong(m.group(1)), Long::sum);
                else counts.merge(t, 1L, Long::sum);
            }
        }
        for (Map.Entry<String, Long> g : gains.entrySet()) counts.merge(g.getKey(), g.getValue(), Long::sum);
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, Long> c : counts.entrySet()) {
            entries.add(c.getValue() > 1 ? c.getValue() + " " + c.getKey() : c.getKey());
        }
        return String.join(", ", entries);
    }

    /**
     * XP gained: if it came right after the interaction, that was skilling, so the step goes.
     * Returns true for Agility XP from a shortcut, which belongs to the shortcut's step and isn't
     * an activity of its own. (Course obstacles never get here: their clicks aren't taken.)
     */
    boolean onXp(Skill skill, int newXp, int tick) {
        Integer prev = xp.put(skill, newXp);
        if (prev == null || newXp <= prev) return false;
        if (skill == Skill.AGILITY) {
            if (option != null && tick - clickTick <= REACH_TICKS) {
                host.trace("OBJECT agility XP from '" + option + " " + target + "': a shortcut, step at the object");
                makeStep(objectTile, tick);
                return true;
            }
            // Shortcut XP comes when the obstacle finishes, which can be a while after reaching it
            if (step != null && host.lastDraft() == step && tick - stepTick <= SHORTCUT_XP_TICKS) {
                host.trace("OBJECT agility XP from shortcut '" + step.getName() + "', kept as its step");
                return true;
            }
        }
        RouteStep s = step;
        if (s != null && tick - stepTick <= XP_TICKS && host.lastDraft() == s && !s.hasItems()
            && (s.getDialogOptions() == null || s.getDialogOptions().trim().isEmpty()) && !s.hasQuest()) {
            host.trace("OBJECT '" + s.getName() + "' gave " + skill + " XP: skilling, step dropped");
            host.removeDraft(s);
            step = null;
        }
        if (option != null && tick - clickTick <= REACH_TICKS) option = null; // still walking there: skilling
        return false;
    }

    static String objectName(String target) {
        int arrow = target.lastIndexOf("->");
        String t = arrow >= 0 ? target.substring(arrow + 2) : target;
        return t.trim();
    }

    static String usedItem(String target) {
        int arrow = target.indexOf("->");
        return arrow < 0 ? null : target.substring(0, arrow).trim();
    }

    private static String stripAt(String name) {
        int at = name.indexOf(" at ");
        return at < 0 ? name : name.substring(0, at);
    }

    private static String cap(String s) {
        String t = s.replace('-', ' ').trim();
        return t.isEmpty() ? t : t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
    }
}
