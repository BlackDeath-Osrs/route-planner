package com.routeplanner.model;

import lombok.Data;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import java.util.Map;

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
    private ItemMode itemMode = ItemMode.BANK;  // null (left out of older saves) means BANK
    private transient boolean sellArmed = false;  // SELL: true once items seen in inventory
    private Map<String, Long> pickupBaseline;  // PICKUP/SHOP: inventory counts when step became active
    // The account the step's baselines (pickupBaseline, skillingStartXp) were taken on when it became
    // active; 0 = not taken yet. Saved, so reloading keeps the progress; an imported route or another
    // account doesn't match, so those take their own.
    private long baselineAccount;

    /** PICKUP and SHOP count items gained since the step started, not items already carried. */
    public boolean countsGainedItems() {
        return itemMode == ItemMode.PICKUP || itemMode == ItemMode.SHOP;
    }

    // Guided quest (Quest Helper) step fields
    private int questStopIndex = -1;   // flat step index to stop at, -1 = whole quest
    private String questStopText;       // display text of the stop step

    // Teleport step fields
    private String teleportSpell;     // spell name e.g. "Varrock Teleport"
    private WorldPoint teleportDestination; // destination WorldPoint
    private int teleportSpellId;      // widget child ID for highlighting  // matches net.runelite.api.Quest enum name

    private String teleportMethod;    // "SPELL", "ITEM", "NEXUS" or "PORTAL" (null = walk only)
    private String teleportItem;      // item name for ITEM teleports (e.g. "Amulet of glory")
    private String teleportNexus;     // NEXUS: destination as shown in the nexus menu; PORTAL: house portal's destination ("Varrock" for Varrock Portal)

    // Skilling step fields
    private String skillingSkill;      // e.g. "THIEVING"
    private SkillGoalType skillingGoalType;
    private long skillingGoalValue;    // target value
    private long skillingStartXp;      // XP when step became active
    private long skillingProgress;     // current XP gained (for XP_GAIN)
    private String skillingTargetNpc;  // NPC to highlight (e.g. "Knight of Ardougne")
    private String skillingTargetObject; // game object to highlight (e.g. "Baker's stall")
    private String gearReminder; // fishing gear reminder (e.g. "Fly fishing rod, Feathers")
    private String herblorePotion; // potion for Herblore steps (drives the bank highlight)
    private String bankHighlightItems; // generic bank highlight for skilling steps (Cooking, etc.)
    private String inventoryHighlightItems; // items to outline in the inventory, e.g. "Cocktail shaker, Lemon"

    // Area (optional): two opposite corner tiles on one floor, in any order. Arriving anywhere inside
    // counts, instead of only the few tiles around worldPoint (e.g. the grounds Hans walks around).
    private WorldPoint areaFrom;
    private WorldPoint areaTo;
    // A painted area's exact tiles (TileArea.encode). areaFrom/areaTo then hold its bounding box,
    // which is what older versions of the plugin read.
    private String areaTiles;
    // Highlights (NPCs, objects, ground items) only inside the area, not everywhere in range
    private boolean highlightInAreaOnly;
    private transient TileArea areaCache;
    private transient String areaCacheKey;

    public boolean hasArea() {
        return areaFrom != null && areaTo != null;
    }

    /** The area: its painted tiles, else the rectangle on areaFrom's floor; null when there's none. */
    public TileArea area() {
        if (!hasArea()) return null;
        String key = areaTiles != null ? areaTiles : areaFrom + "/" + areaTo;
        if (key.equals(areaCacheKey)) return areaCache;
        TileArea a = areaTiles != null ? TileArea.decode(areaTiles) : null;
        if (a == null) {
            int x = Math.min(areaFrom.getX(), areaTo.getX()), y = Math.min(areaFrom.getY(), areaTo.getY());
            a = TileArea.of(new WorldArea(x, y, Math.abs(areaFrom.getX() - areaTo.getX()) + 1,
                Math.abs(areaFrom.getY() - areaTo.getY()) + 1, areaFrom.getPlane()));
        }
        areaCache = a;
        areaCacheKey = key;
        return a;
    }

    /** Set the area (null clears it). A rectangle is kept as just its corners. */
    public void setArea(TileArea a) {
        if (a == null) {
            areaFrom = null;
            areaTo = null;
            areaTiles = null;
            return;
        }
        WorldArea b = a.bounds();
        areaFrom = new WorldPoint(b.getX(), b.getY(), b.getPlane());
        areaTo = new WorldPoint(b.getX() + b.getWidth() - 1, b.getY() + b.getHeight() - 1, b.getPlane());
        areaTiles = a.isRectangle() ? null : a.encode();
    }

    // Transition point: when set, the step first paths to transitionPoint (the staircase/ladder
    // tile), then, once the player is on worldPoint's floor, on to worldPoint.
    private WorldPoint transitionPoint;
    private int transitionObjectId = -1; // game object ID to highlight as the transition object (-1 = none)

    // Boarding point (Sailing): when set, the step starts by getting on a boat: path to boardPoint
    // (the dock by the gangplank) and highlight the gangplank, then once aboard, sail to worldPoint.
    // With no worldPoint, boarding is the step.
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
    public RouteStep(String name, String skill, SkillGoalType goalType, long goalValue, long startXp) {
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

    /**
     * Clear the progress made on this step (not its completed tick), so it starts fresh: location
     * nav re-arms, SELL re-arms, and the item/XP baselines are taken again.
     */
    public void resetProgress() {
        locationReached = false;
        boarded = false;
        sellArmed = false;
        pickupBaseline = null;
        baselineAccount = 0;
        catchProgress = 0;
        npcKillProgress = 0;
        skillingProgress = 0;
        if (skillingGoalType == SkillGoalType.XP_GAIN) skillingStartXp = 0; // re-captured on the next StatChanged
    }

    // --- Component presence helpers ---
    // A component is a view over fields the step already carries.
    // Descriptive only; nothing branches on these yet.

    public boolean hasLocation() {
        return worldPoint != null || teleportMethod != null;
    }

    public boolean hasItems() {
        return (itemList != null && !itemList.trim().isEmpty())
            || itemMode == ItemMode.DEPOSIT_ALL;
    }

    /** The skill goal's skill, or null when there's none or the saved name isn't a skill. */
    public Skill skill() {
        if (!hasSkillGoal()) return null;
        try {
            return Skill.valueOf(skillingSkill.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean hasSkillGoal() {
        return skillingSkill != null && !skillingSkill.trim().isEmpty();
    }

    /** Anything the highlight toggle lights up: NPCs, objects, and bank/inventory items. */
    public boolean hasHighlight() {
        return notBlank(skillingTargetNpc) || notBlank(skillingTargetObject) || notBlank(npcHighlight)
            || notBlank(bankHighlightItems) || notBlank(herblorePotion) || notBlank(inventoryHighlightItems);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    public boolean hasQuest() {
        return questName != null && !questName.trim().isEmpty();
    }

    public boolean hasNote() {
        return noteText != null && !noteText.trim().isEmpty();
    }
}
