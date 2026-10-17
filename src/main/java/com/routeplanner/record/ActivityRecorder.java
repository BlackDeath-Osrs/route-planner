package com.routeplanner.record;

import com.routeplanner.agility.AgilityCourse;
import com.routeplanner.agility.AgilityCoursePresets;
import com.routeplanner.agility.AgilityObstacle;
import com.routeplanner.model.RouteStep;
import com.routeplanner.util.LocationNames;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

import java.util.*;

/**
 * The skilling and combat half of Record mode: turns XP drops and kills into steps.
 *
 *  - Gathering (woodcutting, mining, fishing, hunter): the items you got become a PICKUP-style
 *    item count ("28 Yew logs"), with the tree / rock / fishing spot as the highlight target.
 *  - Agility: XP on a known course becomes an XP goal on that course, started at its start tile.
 *  - Any other skill: the XP gained becomes an "Gain XP amount" goal, with the NPC or object
 *    you were using (a pickpocket NPC, a stall, a range) as the highlight target.
 *  - Kills: NPCs you were fighting that die become a kill-count step highlighting that NPC.
 *
 * Repeating the same thing in the same spot adds to one step rather than making many. Like the
 * rest of the recorder it only reads game state; it never acts for the player.
 */
class ActivityRecorder {

    /** Same activity if the next XP / kill comes within this many tiles of where it started. */
    private static final int ACTIVITY_RADIUS = 15;
    /** While XP keeps coming this recently, the player is busy here: no idle location steps. */
    private static final int BUSY_TICKS = 30;
    /** A clicked NPC / object explains XP that arrives within this many ticks. */
    private static final int TARGET_WINDOW_TICKS = 30;
    /** Teleport spells give Magic XP; ignore Magic this soon after a teleport click. */
    private static final int TELEPORT_XP_TICKS = 10;

    private static final Set<Skill> GATHERING = EnumSet.of(
        Skill.WOODCUTTING, Skill.MINING, Skill.FISHING, Skill.HUNTER);
    /** Combat XP is recorded as kills instead. */
    private static final Set<Skill> COMBAT = EnumSet.of(
        Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.HITPOINTS, Skill.SLAYER);

    private final Client client;
    private final RouteRecorder host;

    private final Map<Skill, Integer> xp = new EnumMap<>(Skill.class);

    /** The NPC or object the player last used. */
    private String targetName;
    private String targetOption;
    private boolean targetIsNpc;
    private int targetObjectId = -1;
    private WorldPoint targetPos;   // where the player stood when they clicked it
    private WorldPoint targetObjectTile; // the clicked object's own tile, when it was an object
    private int targetTick = -1000;
    private int teleportTick = -1000;
    private int combatTick = -1000;

    /** The step being added to, and what it's for. */
    private RouteStep step;
    private Skill skill;            // null for a kill step
    private String stepTarget;      // what it highlights (NPC / object / course name)
    private WorldPoint stepPos;
    private long stepXp;
    private int lastXpTick = -1000;
    private int stepBeganTick = -1000;
    /** XP added to the current step in the last few ticks, so a quest reward can be taken back out. */
    private long recentXp;
    private int recentXpTick = -1000;
    private int ignoreXpUntil = -1000;
    private final Map<String, Long> gathered = new LinkedHashMap<>();
    /** Items gained this tick before the XP drop that explains them arrived. */
    private final Map<String, Long> pendingGains = new LinkedHashMap<>();
    private int pendingTick = -1;

    ActivityRecorder(Client client, RouteRecorder host) {
        this.client = client;
        this.host = host;
    }

    void start() {
        xp.clear();
        for (Skill s : Skill.values()) {
            if (s == Skill.OVERALL) continue;
            xp.put(s, client.getSkillExperience(s));
        }
        targetName = null;
        targetTick = teleportTick = combatTick = lastXpTick = -1000;
        step = null;
        gathered.clear();
        pendingGains.clear();
    }

    /** An NPC or object was clicked (not walking or examining). */
    void onTargetClick(String option, String target, boolean npc, int objectId, WorldPoint at, WorldPoint objectTile, int tick) {
        targetPos = at;
        targetObjectTile = objectTile;
        targetObjectId = npc ? -1 : objectId;
        targetName = cleanName(target);
        targetOption = option;
        targetIsNpc = npc;
        targetTick = tick;
        if ("Attack".equalsIgnoreCase(option)) combatTick = tick;
    }

    void onTeleportClick(int tick) {
        teleportTick = tick;
    }

    /** True while the player is busy with an activity here, so standing still isn't a new stop. */
    boolean isBusy(WorldPoint pos, int tick) {
        if (step == null || host.lastDraft() != step || tick - lastXpTick > BUSY_TICKS) return false;
        if (skill == Skill.AGILITY) return true; // a lap ranges all over (and up) the course
        return pos == null || stepPos == null || pos.distanceTo2D(stepPos) <= ACTIVITY_RADIUS;
    }

    /**
     * The course an obstacle belongs to, by its object id (courses can span several map
     * regions, so the id is the reliable part), falling back to the region the player is in.
     */
    private static AgilityCourse courseFor(int objectId, WorldPoint pos) {
        if (objectId > 0) {
            for (AgilityCourse c : AgilityCoursePresets.ALL) {
                for (AgilityObstacle o : c.getObstacles()) if (o.matchesId(objectId)) return c;
            }
        }
        return pos == null ? null : AgilityCoursePresets.getCourseForRegion(pos.getRegionID());
    }

    /** Running a course right now: agility XP came recently. */
    boolean runningCourse(WorldPoint pos, int tick) {
        return skill == Skill.AGILITY && step != null && host.lastDraft() == step && tick - lastXpTick <= BUSY_TICKS;
    }

    /** True when the player is running a known agility course (its jumps and climbs aren't travel). */
    boolean onAgilityCourse(WorldPoint pos, int clickedObjectId, int tick) {
        // Mid-lap any obstacle counts, even one the course table doesn't list by id.
        if (runningCourse(pos, tick)) return true;
        for (AgilityCourse c : AgilityCoursePresets.ALL) {
            for (AgilityObstacle o : c.getObstacles()) if (o.matchesId(clickedObjectId)) return true;
        }
        return false;
    }

    void onXp(Skill s, int newXp, WorldPoint pos, int tick) {
        Integer old = xp.put(s, newXp);
        if (old == null || old <= 0 || newXp <= old) return;
        long gained = newXp - old;
        if (tick <= ignoreXpUntil) { host.trace("XP+" + gained + " " + s + " ignored (quest reward)"); return; }
        if (COMBAT.contains(s)) { host.trace("XP+" + gained + " " + s + " ignored (combat)"); return; }
        if (s == Skill.MAGIC && (tick - teleportTick <= TELEPORT_XP_TICKS || tick - combatTick <= TARGET_WINDOW_TICKS)) {
            host.trace("XP+" + gained + " MAGIC ignored (teleport/combat)");
            return;
        }

        String target = tick - targetTick <= TARGET_WINDOW_TICKS ? targetName : null;
        AgilityCourse course = s == Skill.AGILITY
            ? courseFor(tick - targetTick <= TARGET_WINDOW_TICKS ? targetObjectId : -1, pos) : null;
        if (course != null) target = course.getName();

        // A lap that began on an obstacle the course list doesn't know: once a later obstacle
        // names the course, the step takes it on instead of splitting the lap.
        if (course != null && s == Skill.AGILITY && skill == s && step != null && host.lastDraft() == step
            && stepTarget == null && tick - lastXpTick <= BUSY_TICKS) {
            stepTarget = course.getName();
            step.setRecipeLabel(course.getName());
        }
        boolean same = step != null && host.lastDraft() == step && skill == s
            && (course != null ? course.getName().equals(stepTarget)
                // A course the plugin doesn't know: every obstacle has its own name, so keep
                // the lap together while the XP keeps coming instead of matching the target.
                : s == Skill.AGILITY ? stepTarget == null && tick - lastXpTick <= BUSY_TICKS
                : near(pos) && (target == null || target.equals(stepTarget)));
        if (s == Skill.AGILITY && course == null) target = null;
        host.trace("XP+" + gained + " " + s + " target=" + target + " course=" + (course == null ? null : course.getName())
            + " -> " + (same ? "add to current step" : "new activity (current=" + RouteRecorder.describeStep(step)
            + ", lastDraftIsCurrent=" + (step != null && host.lastDraft() == step) + ")"));
        if (!same) begin(s, target, course, pos);
        stepXp += gained;
        if (tick - recentXpTick > 2) recentXp = 0;
        recentXp += gained;
        recentXpTick = tick;
        lastXpTick = tick;
        if (GATHERING.contains(s) && pendingTick >= tick - 1) {
            pendingGains.forEach((k, v) -> gathered.merge(k, v, Long::sum));
        }
        pendingGains.clear();
        refresh(pos);
    }

    /**
     * A quest was just finished: its reward XP isn't training. XP that arrived in the last couple
     * of ticks is taken back off the step (removing the step if that was all it had), and XP
     * for a few ticks more is ignored (reward lamps and the scroll can come slightly later).
     */
    void onQuestComplete(int tick) {
        ignoreXpUntil = tick + 10;
        if (step == null || tick - recentXpTick > 2 || recentXp <= 0) return;
        stepXp -= recentXp;
        recentXp = 0;
        host.trace("QUEST reward XP removed from " + RouteRecorder.describeStep(step));
        if (stepXp <= 0 && tick - stepBeganTick <= 2) {
            host.removeDraft(step);
            step = null;
        } else if (stepXp > 0) {
            refresh(null);
        }
    }

    /** Items arrived in the inventory that weren't picked up, bought or withdrawn. */
    void onItemsGained(Map<String, Long> gains, int tick) {
        if (step != null && GATHERING.contains(skill) && host.lastDraft() == step && tick - lastXpTick <= 2) {
            gains.forEach((k, v) -> gathered.merge(k, v, Long::sum));
            refresh(null);
            return;
        }
        // The XP for these may come later this tick: hold on to them until it does.
        if (pendingTick != tick) pendingGains.clear();
        gains.forEach((k, v) -> pendingGains.merge(k, v, Long::sum));
        pendingTick = tick;
    }

    /** An NPC died; count it if the player was fighting it. */
    void onNpcDeath(NPC npc, WorldPoint pos) {
        Player me = client.getLocalPlayer();
        if (me == null || npc.getName() == null) return;
        if (me.getInteracting() != npc && npc.getInteracting() != me) return;
        String name = cleanName(npc.getName());
        RouteStep last = host.lastDraft();
        boolean same = last != null && name.equalsIgnoreCase(last.getNpcHighlight()) && last.getNpcKillCount() > 0
            && host.isNear(last, pos, ACTIVITY_RADIUS);
        if (!same) {
            // Loot picked up after a kill sits on the same step, so fighting and looting stay together.
            last = host.stepAt(pos, "Kill " + name + " at " + place(pos), ActivityRecorder::canTakeKills);
            last.setNpcHighlight(name);
            last.setNpcKillCount(0);
            step = null; // a kill step isn't an XP activity
        }
        last.setNpcKillCount(last.getNpcKillCount() + 1);
        last.setNoteText("Kill " + last.getNpcKillCount() + " " + name + ".");
    }

    private static boolean canTakeKills(RouteStep s) {
        return "PICKUP".equals(s.getItemMode()) && !s.hasSkillGoal()
            && s.getSkillingTargetNpc() == null && s.getSkillingTargetObject() == null && !s.hasNote();
    }

    private void begin(Skill s, String target, AgilityCourse course, WorldPoint pos) {
        WorldPoint at = pos;
        if (s == Skill.AGILITY) {
            // Best: the course's first obstacle as actually clicked. Then the course preset.
            WorldPoint startTile = null;
            if (course != null && targetObjectTile != null && !course.getObstacles().isEmpty()
                && course.getObstacles().get(0).matchesId(targetObjectId)) startTile = targetObjectTile;
            if (startTile == null && course != null) startTile = AgilityCoursePresets.startLocation(course.getName());
            // No known start: the lap starts where its first obstacle was clicked (on the ground,
            // not wherever on the rooftops the first XP landed you).
            if (startTile == null && targetPos != null) startTile = targetPos;
            if (startTile != null) at = startTile;
        }
        step = host.stepAt(at, "Train " + cap(s.name()) + " at " + place(pos), null);
        skill = s;
        stepTarget = target;
        stepPos = pos;
        stepXp = 0;
        stepBeganTick = host.currentTick();
        gathered.clear();
        if (course != null) {
            step.setRecipeLabel(course.getName());
        } else if (target != null) {
            if (targetIsNpc) step.setSkillingTargetNpc(target);
            else step.setSkillingTargetObject(target);
        }
    }

    /** Rewrite the step from what's been recorded so far. */
    private void refresh(WorldPoint pos) {
        WorldPoint where = stepPos != null ? stepPos : pos;
        String verb = targetOption != null && stepTarget != null && stepTarget.equals(targetName)
            ? targetOption : null;
        if (GATHERING.contains(skill) && !gathered.isEmpty()) {
            // Gathering is about what you come away with, so it's an item count.
            List<String> entries = new ArrayList<>();
            gathered.forEach((k, v) -> entries.add(v > 1 ? v + " " + k : k));
            step.setItemMode("PICKUP");
            step.setItemList(String.join(", ", entries));
            step.setSkillingSkill(null);
            step.setSkillingGoalType(null);
            step.setName((verb != null ? verb + " " + stepTarget : "Gather") + " at " + place(where));
            return;
        }
        step.setSkillingSkill(skill.name());
        step.setSkillingGoalType("XP_GAIN");
        step.setSkillingGoalValue(stepXp);
        step.setSkillingStartXp(0); // captured when the step becomes active during playback
        if (skill == Skill.AGILITY) {
            step.setName(step.getRecipeLabel() != null
                ? "Agility: " + step.getRecipeLabel() + " course" : "Agility at " + place(where));
        } else if (verb != null) {
            step.setName(verb + " " + stepTarget + " at " + place(where));
        } else {
            step.setName("Train " + cap(skill.name()) + " at " + place(where));
        }
    }

    private boolean near(WorldPoint pos) {
        if (pos == null || stepPos == null) return pos == null && stepPos == null;
        return pos.distanceTo2D(stepPos) <= ACTIVITY_RADIUS;
    }

    /** "Man  (level-2)" -> "Man". */
    private static String cleanName(String raw) {
        if (raw == null) return null;
        // "Use Raw shrimps -> Range": the thing used on is the target.
        int arrow = raw.lastIndexOf("->");
        if (arrow >= 0) raw = raw.substring(arrow + 2);
        return raw.replaceAll("\\s*\\(level-\\d+\\)", "").trim();
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private static String place(WorldPoint pos) {
        return pos == null ? "here" : LocationNames.describe(pos);
    }
}
