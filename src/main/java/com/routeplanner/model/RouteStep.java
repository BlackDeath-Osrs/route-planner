package com.routeplanner.model;

import lombok.Data;
import net.runelite.api.coords.WorldPoint;

@Data
public class RouteStep {
    private String name;
    private StepType type;
    private WorldPoint worldPoint;
    private int targetId;
    private boolean completed;
    private transient boolean locationReached; // true once player reached worldPoint; nav visuals off (sticky)
    private String sectionId;   // which RouteSection this step belongs to

    // Agility task fields
    private String agilityCourse;
    private String agilityGoalType;
    private long agilityGoalValue;

    // Quest step fields
    private String questName;          // matches net.runelite.api.Quest enum name
    private boolean questComplete;     // true = auto-complete step when quest is FINISHED

    // Item/Bank step fields
    private String itemList;  // comma separated, / for alternatives e.g. "Spade,Rune pouch/Divine Rune pouch"
    private String itemMode = "BANK";  // "BANK" (fetch from bank), "SHOP" (buy), or "SELL" (sell to shop)
    private transient boolean sellArmed = false;  // SELL: true once items seen in inventory
    private java.util.Map<String, Long> pickupBaseline;  // PICKUP/SHOP: inventory counts when step became active
    // The account the step's baselines (pickupBaseline, skillingStartXp) were taken on when it became
    // active; 0 = not taken yet. Saved, so reloading keeps the progress; an imported route or another
    // account doesn't match, so those take their own.
    private long baselineAccount;

    /** PICKUP and SHOP count items gained since the step started, not items already carried. */
    public boolean countsGainedItems() {
        return "PICKUP".equals(itemMode) || "SHOP".equals(itemMode);
    }

    // Guided quest (Quest Helper) step fields
    private int questStopIndex = -1;   // flat step index to stop at, -1 = whole quest
    private String questStopText;       // display text of the stop step

    // Teleport step fields
    private String teleportSpell;     // spell name e.g. "Varrock Teleport"
    private net.runelite.api.coords.WorldPoint teleportDestination; // destination WorldPoint
    private int teleportSpellId;      // widget child ID for highlighting  // matches net.runelite.api.Quest enum name

    private String teleportMethod;    // Phase 2: "SPELL", "ITEM" or "NEXUS" (null = walk only)
    private String teleportItem;      // Phase 2: item name for ITEM teleports (e.g. "Amulet of glory")
    private String teleportNexus;     // NEXUS teleports: Portal Nexus destination as shown in its menu

    // Skilling step fields
    private String skillingSkill;      // e.g. "THIEVING"
    private String skillingGoalType;   // "XP_GAIN", "XP_TARGET", "LEVEL"
    private long skillingGoalValue;    // target value
    private long skillingStartXp;      // XP when step became active
    private long skillingProgress;     // current XP gained (for XP_GAIN)
    private String skillingTargetNpc;  // NPC to highlight (e.g. "Knight of Ardougne")
    private String skillingTargetObject; // game object to highlight (e.g. "Baker's stall")
    private String gearReminder; // fishing gear reminder (e.g. "Fly fishing rod, Feathers")
    private String herblorePotion; // potion for Herblore steps (drives the bank highlight)
    private String bankHighlightItems; // generic bank highlight for skilling steps (Cooking, etc.)

    // Transition point: when set, this step has two phases.
    // Phase 1: path to transitionPoint (the staircase/ladder tile).
    // Phase 2: once player plane matches worldPoint plane, path to worldPoint.
    private WorldPoint transitionPoint;
    private int transitionObjectId = -1; // game object ID to highlight as the transition object (-1 = none)

    // Boarding point (Sailing): when set, the step starts by getting on a boat.
    // Phase 1: path to boardPoint (the dock by the gangplank) and highlight the gangplank.
    // Phase 2: once on the boat, path over the sea to worldPoint. With no worldPoint, boarding is the step.
    private WorldPoint boardPoint;
    private int boardObjectId = -1;      // the gangplank's object ID, if known (-1 = find it by name)
    private transient boolean boarded;   // true once the player got on a boat during this step

    // Catch goal (Sailing trawling): fish caught into the net, counted from the catch messages
    // since they never reach the inventory. Progress is saved so a reload carries on.
    private String catchFish;       // e.g. "Giant krill"
    private int catchGoal;          // how many to catch (0 = none)
    private int catchProgress;      // caught so far while this step is active

    public boolean hasCatchGoal() {
        return catchGoal > 0 && catchFish != null;
    }

    public boolean hasBoarding() {
        return boardPoint != null;
    }

    // Note step (informational, not tracked)
    private String noteText;
    private boolean noteAutoComplete; // NOTE step: done on arrival, no manual tick (off = wait for the tick)
    private String npcHighlight; // NOTE step: optional NPC name to highlight
    private boolean highlightEnabled; // master toggle: light up this step's in-game targets (default off)
    private String recipeLabel; // editor: the exact recipe/method dropdown label chosen (for reverse-prefill)
    private int npcKillCount;    // NOTE step: kills to auto-complete (0 = manual)
    private transient int npcKillProgress; // kills so far while this step is active
    private String dialogOptions;          // NOTE step: chat option sequence to highlight, e.g. "3,1""

    public RouteStep(String name, StepType type, WorldPoint worldPoint, int targetId) {
        this.name = name;
        this.type = type;
        this.worldPoint = worldPoint;
        this.targetId = targetId;
        this.completed = false;
    }

    // Constructor for skilling steps
    public RouteStep(String name, String skill, String goalType, long goalValue, long startXp) {
        this.name = name;
        this.type = StepType.SKILLING;
        this.worldPoint = null;
        this.targetId = -1;
        this.completed = false;
        this.skillingSkill = skill;
        this.skillingGoalType = goalType;
        this.skillingGoalValue = goalValue;
        this.skillingStartXp = startXp;
        this.skillingProgress = 0;
    }

    // --- Component presence helpers (Phase 2) ---
    // A component is a view over fields the step already carries.
    // Descriptive only; nothing branches on these yet.

    public boolean hasLocation() {
        return worldPoint != null || teleportMethod != null;
    }

    public boolean hasItems() {
        return (itemList != null && !itemList.trim().isEmpty())
            || "DEPOSIT_ALL".equals(itemMode);
    }

    public boolean hasSkillGoal() {
        return skillingSkill != null && !skillingSkill.trim().isEmpty();
    }

    public boolean hasHighlight() {
        return (skillingTargetNpc != null && !skillingTargetNpc.trim().isEmpty())
            || (skillingTargetObject != null && !skillingTargetObject.trim().isEmpty())
            || (npcHighlight != null && !npcHighlight.trim().isEmpty());
    }

    public boolean hasQuest() {
        return questName != null && !questName.trim().isEmpty();
    }

    public boolean hasNote() {
        return noteText != null && !noteText.trim().isEmpty();
    }
}
