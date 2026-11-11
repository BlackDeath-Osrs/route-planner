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
    private transient boolean sellArmed = false;  // SELL / USE_UP: true once the items were seen in the inventory
    private Map<String, Long> pickupBaseline;  // PICKUP/SHOP: carried and worn counts when step became active
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

    // Farming patch: the patch's own object id (the same whatever grows in it), so a step works for
    // any seed. With farmReplant the step is done once something is planted in it; without, once it
    // gave Farming XP and has nothing left to pick, harvest or check (a bush, a tree).
    // -1 = the patch nearest the step's tile, looked up (and kept) the first time it's in view.
    private int farmPatchId;
    private boolean farmReplant;
    private transient boolean farmDone;

    public boolean hasFarmPatch() {
        return farmPatchId != 0;
    }

    /** The patch is still to be looked up from the step's tile. */
    public boolean farmPatchUnresolved() {
        return farmPatchId < 0;
    }

    // Game progress: a quest stage or diary task the game records, e.g. "varbit 3185 >= 4" or
    // "varp 1194 bit 6" (see ProgressCheck). Once the game shows it, the step is done whatever
    // else it asks for, so steps already done before (or done another way) tick themselves off.
    private String progressCheck;
    private transient String parsedCheckText;
    private transient ProgressCheck parsedCheck;

    /** The step's game progress check, or null when it has none (or it can't be read). */
    public ProgressCheck progress() {
        if (progressCheck == null) return null;
        if (!progressCheck.equals(parsedCheckText)) {
            parsedCheck = ProgressCheck.parse(progressCheck);
            parsedCheckText = progressCheck;
        }
        return parsedCheck;
    }

    // Note step (informational, not tracked)
    private String noteText;
    private boolean noteAutoComplete; // NOTE step: done on arrival, no manual tick (off = wait for the tick)
    private String npcHighlight; // NOTE step: optional NPC name to highlight
    private boolean highlightEnabled; // master toggle: light up this step's in-game targets (default off)
    private String recipeLabel; // editor: the exact recipe/method dropdown label chosen (for reverse-prefill)
    private int npcKillCount;    // NOTE step: kills to auto-complete (0 = manual)
    private int npcKillProgress;           // kills so far while this step is active; saved so a reload carries on
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
        farmDone = false;
        if (skillingGoalType == SkillGoalType.XP_GAIN) skillingStartXp = 0; // re-captured on the next StatChanged
    }

    /** Steps are told apart by identity: two steps with the same contents are still two steps. */
    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
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
        return farmPatchId != 0 || notBlank(skillingTargetNpc) || notBlank(skillingTargetObject) || notBlank(npcHighlight)
            || notBlank(bankHighlightItems) || notBlank(herblorePotion) || notBlank(inventoryHighlightItems);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    public boolean hasQuest() {
        return questName != null && !questName.trim().isEmpty();
    }

    /**
     * Leprechaun-noting and herb-cleaning steps recorded before "use up" existed were saved as
     * plain item steps, so they completed as soon as the items were carried. Turn them into
     * use-up steps.
     */
    public void migrateUseUp() {
        if (itemMode != null && itemMode != ItemMode.BANK) return;
        if (itemList == null || itemList.trim().isEmpty() || name == null) return;
        boolean noting = "Tool Leprechaun".equalsIgnoreCase(npcHighlight) && name.startsWith("Note ");
        boolean cleaning = name.startsWith("Clean the grimy herbs") && itemList.trim().equalsIgnoreCase("Grimy *");
        if (noting || cleaning) itemMode = ItemMode.USE_UP;
    }

    /** "Harvest and replant the herb patch at ...", "Tend the bush patch": what older recordings named patch steps. */
    private static final java.util.regex.Pattern OLD_PATCH_STEP =
        java.util.regex.Pattern.compile("^(Harvest and replant|Tend) the .*patch\\b.*");

    /**
     * Patch steps recorded before patches were saved on the step carry only their tile, so they
     * completed on arrival. Mark their patch as unresolved: it's looked up near the tile once in
     * view, and the step then waits for the patch to be worked.
     */
    public void migrateFarmPatch() {
        if (farmPatchId != 0 || worldPoint == null || name == null) return;
        if (!OLD_PATCH_STEP.matcher(name).matches()) return;
        farmPatchId = -1;
        if (name.startsWith("Harvest and replant")) farmReplant = true;
    }

    public boolean hasNote() {
        return noteText != null && !noteText.trim().isEmpty();
    }
}
