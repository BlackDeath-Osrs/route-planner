package com.routeplanner.record;

import com.routeplanner.RoutePlannerConfig;
import com.routeplanner.model.Route;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import com.routeplanner.sailing.CatchMessages;
import com.routeplanner.sailing.Sailing;
import com.routeplanner.sailing.SailingDocks;
import com.routeplanner.teleport.PortalNexus;
import com.routeplanner.teleport.TeleportItem;
import com.routeplanner.teleport.TeleportItems;
import com.routeplanner.teleport.TeleportSpell;
import com.routeplanner.teleport.TeleportSpells;
import com.routeplanner.util.LocationNames;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Record mode: watches what the player does in game and turns it into draft route steps.
 *
 * This class only READS game events and WRITES draft RouteSteps. It never sends input,
 * never calls menuAction, never touches the network, and has no way to replay anything --
 * a recording is just a guide the player still follows by hand. It doesn't store mouse
 * positions, timings or keystrokes, only what happened in game (where you went, what you
 * clicked on), and only for the local player.
 *
 * It is registered on the event bus only while recording. All state is touched on the
 * client thread; start/stop are called from the panel via ClientThread.
 */
@Slf4j
@Singleton
public class RouteRecorder {

    /** Same-plane movement beyond this in one tick is a teleport/transport, not walking. */
    private static final int JUMP_TILES = 10;
    /** A recorded stop must be at least this far from the last step to count as a new place. */
    private static final int MIN_STEP_SPACING = 5;
    /** Teleports can take a while to land (home teleport ~17 ticks, glory rub + dialog). */
    private static final int TELEPORT_WINDOW_TICKS = 40;

    private final Client client;
    private final EventBus eventBus;
    private final RoutePlannerConfig config;
    private final ItemRecorder items;
    private final ActivityRecorder activity;
    private final DialogRecorder dialog;

    @Getter private volatile boolean recording;
    /** The route the recording was started on; the review saves into this one. */
    @Getter private Route targetRoute;
    private final List<RouteStep> drafts = new ArrayList<>();

    private WorldPoint lastPos;
    private WorldPoint anchor;          // where the last step was recorded (or recording started)
    private int idleTicks;

    /** The most recent click of any kind, so a jump can be explained by what caused it. */
    private Click lastClick;
    /** The most recent click that could start a teleport (a spell cast or a teleport item). */
    private Click lastTeleportClick;
    /** Marks a nexus pick whose menu text couldn't be read; named from where you land instead. */
    private static final String NEXUS_UNREAD = "";
    /** The last Portal Nexus destination picked (menu row or right-click option), and when. */
    private String lastNexusDest;
    private int lastNexusTick;
    /** A stair/ladder climb waiting to be attached to the next location step on the new floor. */
    private WorldPoint pendingTransitionTile;
    private int pendingTransitionObjectId = -1;
    /** Whether the player was on a boat last tick, to notice them getting on one (Sailing). */
    private boolean wasAboard;
    /** The last "Board" click on an object (the gangplank); a boat-picking menu usually follows it. */
    private Click lastBoardClick;
    /** How recent the gangplank click must be for boarding to be credited to it. */
    private static final int BOARD_CLICK_TICKS = 20;
    /** How close to a dock the player must step off a boat for it to count as docking there. */
    private static final int DOCK_RADIUS = 15;
    /** The dock the player last boarded at; stopping near it is getting ready, not a destination. */
    private WorldPoint boardedAt;
    /** How far out from that dock the boat must stop for the stop to become a step. */
    private static final int BOARD_IDLE_RADIUS = 12;

    private static final class Click {
        final int tick;
        final MenuAction action;
        final String option;
        final String target;
        final int id;
        final int itemId;
        final int sceneX;
        final int sceneY;

        Click(int tick, MenuOptionClicked e) {
            this.tick = tick;
            this.action = e.getMenuAction();
            this.option = Text.removeTags(e.getMenuOption());
            this.target = Text.removeTags(e.getMenuTarget());
            this.id = e.getId();
            this.itemId = e.getItemId();
            this.sceneX = e.getParam0();
            this.sceneY = e.getParam1();
        }

        boolean isNpc() {
            switch (action) {
                case NPC_FIRST_OPTION:
                case NPC_SECOND_OPTION:
                case NPC_THIRD_OPTION:
                case NPC_FOURTH_OPTION:
                case NPC_FIFTH_OPTION:
                    return true;
                default:
                    return false;
            }
        }

        boolean isObject() {
            switch (action) {
                case GAME_OBJECT_FIRST_OPTION:
                case GAME_OBJECT_SECOND_OPTION:
                case GAME_OBJECT_THIRD_OPTION:
                case GAME_OBJECT_FOURTH_OPTION:
                case GAME_OBJECT_FIFTH_OPTION:
                    return true;
                default:
                    return false;
            }
        }

        String describe() {
            return target.isEmpty() ? option : option + " " + target;
        }
    }

    private final SailingDocks sailingDocks;

    @Inject
    public RouteRecorder(Client client, EventBus eventBus, RoutePlannerConfig config, ItemManager itemManager,
                         SailingDocks sailingDocks) {
        this.client = client;
        this.eventBus = eventBus;
        this.config = config;
        this.sailingDocks = sailingDocks;
        this.items = new ItemRecorder(client, itemManager, this);
        this.activity = new ActivityRecorder(client, this);
        this.dialog = new DialogRecorder(client, this);
    }

    /** Begin recording into {@code route}. Call on the client thread. */
    public void start(Route route) {
        if (recording) return;
        drafts.clear();
        targetRoute = route;
        lastPos = playerPos();
        anchor = lastPos;
        idleTicks = 0;
        lastClick = null;
        lastTeleportClick = null;
        lastNexusDest = null;
        pendingTransitionTile = null;
        pendingTransitionObjectId = -1;
        wasAboard = Sailing.isSailing(client);
        lastBoardClick = null;
        boardedAt = null;
        items.start();
        activity.start();
        dialog.start();
        recording = true;
        eventBus.register(this);
        log.info("Route Planner: recording started on route '{}'", route.getName());
    }

    /**
     * Stop recording and hand back the drafts. Records a final location step if the player
     * ended somewhere new. Call on the client thread.
     */
    public List<RouteStep> stop() {
        if (!recording) return new ArrayList<>();
        eventBus.unregister(this);
        recording = false;
        WorldPoint pos = playerPos();
        if (pos != null && !inInstance() && isNewPlace(pos)
            && !activity.isBusy(pos, client.getTickCount())) addLocationStep(pos);
        if (config.recordAlwaysHighlight()) {
            for (RouteStep s : drafts) if (s.hasHighlight()) s.setHighlightEnabled(true);
        }
        log.info("Route Planner: recording stopped, {} draft steps", drafts.size());
        List<RouteStep> out = new ArrayList<>(drafts);
        drafts.clear();
        return out;
    }

    /** Drop everything without producing drafts (e.g. plugin shutting down). */
    public void cancel() {
        if (recording) eventBus.unregister(this);
        recording = false;
        drafts.clear();
    }

    /**
     * Diagnostic trace (config "Diagnostic trace"): one log line per event and decision, with
     * the tick, tile, region and the step it touched, so a bad recording can be replayed on paper.
     */
    void trace(String msg) {
        if (!config.recordTrace()) return;
        WorldPoint p = playerPos();
        log.info("[RP-TRACE] t={} pos={} region={} inst={} drafts={} | {}", client.getTickCount(),
            p == null ? "?" : p.getX() + "," + p.getY() + "," + p.getPlane(),
            p == null ? "?" : p.getRegionID(), inInstance(), drafts.size(), msg);
    }

    static String describeStep(RouteStep s) {
        if (s == null) return "null";
        return "'" + s.getName() + "' wp=" + s.getWorldPoint() + " items=" + s.getItemMode() + ":" + s.getItemList()
            + " skill=" + s.getSkillingSkill() + "/" + s.getSkillingGoalValue() + " recipe=" + s.getRecipeLabel()
            + " npc=" + s.getSkillingTargetNpc() + " obj=" + s.getSkillingTargetObject()
            + " kill=" + s.getNpcHighlight() + "x" + s.getNpcKillCount() + " tele=" + s.getTeleportMethod()
            + " trans=" + s.getTransitionPoint() + " board=" + s.getBoardPoint();
    }

    /** The latest recorded step's name, for the panel's recording banner (null before the first). */
    public String getLastDraftName() {
        try {
            return drafts.isEmpty() ? null : drafts.get(drafts.size() - 1).getName();
        } catch (RuntimeException e) {
            return null; // read from the Swing thread while the client thread adds a step
        }
    }

    /** Draft steps so far, for the on-screen indicator. */
    public int getDraftCount() {
        return drafts.size();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked e) {
        Click click = new Click(client.getTickCount(), e);
        lastClick = click;
        // "Board", "Board-previous"... on the gangplank
        if (click.isObject() && click.option.toLowerCase().startsWith("board")) lastBoardClick = click;
        trace("CLICK action=" + click.action + " option='" + click.option + "' target='" + click.target
            + "' id=" + click.id + " item=" + click.itemId + " p0=" + e.getParam0() + " p1=" + e.getParam1());
        if (click.action.name().startsWith("GROUND_ITEM_") && "Take".equalsIgnoreCase(click.option)) {
            items.onTake(click.id, click.tick);
        }
        if (matchSpell(click) != null || isTeleportItem(click.itemId)) {
            lastTeleportClick = click;
            activity.onTeleportClick(click.tick);
        }
        if ("Lower net".equalsIgnoreCase(click.option)) activity.onLowerNet(click.tick);
        // Crewmates on the boat (commanding them, chatting): not part of the route
        NPC clickedNpc = click.isNpc() ? e.getMenuEntry().getNpc() : null;
        if (clickedNpc != null && Sailing.isSailing(client)
                && clickedNpc.getWorldView() == client.getLocalPlayer().getWorldView()) {
            trace("CREW click on " + click.target + ", not recorded");
            dialog.onCrewClick(click.tick);
            return;
        }
        // A facility on the deck (the trawling net's "Collect-from" asks where the fish go): its menu
        // isn't a route step, though using it still counts for activities below
        if (click.isObject() && Sailing.isSailing(client)
                && e.getMenuEntry().getWorldViewId() == client.getLocalPlayer().getWorldView().getId()) {
            dialog.onCrewClick(click.tick);
        }
        if ((click.isNpc() || click.isObject()) && !"Examine".equalsIgnoreCase(click.option)
            && !"Walk here".equalsIgnoreCase(click.option)) {
            // An object click names the object's own tile (scene coords): exact, unlike the player's.
            WorldPoint objectTile = click.isObject() && !inInstance()
                ? WorldPoint.fromScene(client.getTopLevelWorldView(), click.sceneX, click.sceneY, client.getTopLevelWorldView().getPlane())
                : null;
            activity.onTargetClick(click.option, click.target, click.isNpc(), click.id, stepTile(), objectTile, click.tick);
        }
        if (click.isNpc()) dialog.onNpcClick(click.option, click.target, click.tick);
        dialog.onMenuClick(e.getParam1(), e.getParam0(), stepTile());
        String nexus = nexusDestination(e, click);
        if (nexus != null) {
            lastNexusDest = nexus;
            lastNexusTick = click.tick;
        }
    }

    /**
     * The destination picked at a Portal Nexus, if this click picked one: either a row in the
     * nexus teleport menu (those clicks only say "Continue", so the row's own text is read) or
     * a destination option right-clicked on the nexus itself.
     */
    private String nexusDestination(MenuOptionClicked e, Click click) {
        int widgetId = e.getParam1();
        if (widgetId >>> 16 == PortalNexus.MENU_GROUP) {
            Widget w = client.getWidget(widgetId);
            Widget row = w != null && e.getParam0() >= 0 ? w.getChild(e.getParam0()) : null;
            String text = PortalNexus.rowText(client, row != null ? row : w);
            if (text == null) {
                log.info("Route Planner: couldn't read Portal Nexus row text (widget {}, child {})",
                    widgetId, e.getParam0());
                return NEXUS_UNREAD;
            }
            return text;
        }
        if (click.isObject() && click.target.toLowerCase().startsWith("portal nexus")) {
            switch (click.option.toLowerCase()) {
                case "teleport menu": case "configuration": case "examine": case "walk here":
                    return null;
                default:
                    return click.option;
            }
        }
        return null;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged e) {
        traceSailingContainer(e);
        if (e.getContainerId() != InventoryID.INVENTORY.getId()) return;
        items.onInventoryChanged(stepTile(), client.getTickCount());
    }

    @Subscribe
    public void onChatMessage(ChatMessage e) {
        // Trawling catches (Sailing), "You catch three giant krill." (often a spam-filtered message)
        if (e.getType() == ChatMessageType.GAMEMESSAGE || e.getType() == ChatMessageType.SPAM) {
            CatchMessages.Catch c = CatchMessages.parse(e.getMessage());
            if (e.getMessage().toLowerCase().contains("catch")) trace("CHAT '" + e.getMessage() + "' -> " + (c == null ? "not a catch" : c.count + " " + c.fish));
            // Only a trawling net's catch: on the boat with a net down (fishing elsewhere says "You catch" too)
            boolean netDown = client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_TRAWLING_NET_0_DEPTH) > 0
                || client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_TRAWLING_NET_1_DEPTH) > 0;
            if (c != null && netDown && Sailing.isSailing(client)) {
                activity.onCatch(c.fish, c.count, stepTile(), client.getTickCount());
            }
        }
        if (e.getType() != ChatMessageType.GAMEMESSAGE) return;
        if (dialog.onGameMessage(e.getMessage(), stepTile())) activity.onQuestComplete(client.getTickCount());
    }

    @Subscribe
    public void onStatChanged(StatChanged e) {
        trace("XP " + e.getSkill() + " xp=" + e.getXp());
        activity.onXp(e.getSkill(), e.getXp(), stepTile(), client.getTickCount());
    }

    @Subscribe
    public void onActorDeath(ActorDeath e) {
        if (e.getActor() instanceof NPC) trace("DEATH npc='" + e.getActor().getName() + "'");
        if (e.getActor() instanceof NPC) activity.onNpcDeath((NPC) e.getActor(), stepTile());
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        WorldPoint pos = playerPos();
        if (pos == null) return;
        items.onTick(stepTile());
        dialog.onTick(stepTile(), client.getTickCount());
        if (lastPos == null) { lastPos = pos; anchor = pos; return; }

        // Getting on or off a boat moves the player between the dock and the deck: the trip, not a jump.
        boolean aboard = Sailing.isSailing(client);
        boolean boatChanged = aboard != wasAboard;
        if (boatChanged) {
            if (aboard) onBoard(lastPos);
            else onDisembark(pos);
        }
        wasAboard = aboard;

        boolean planeChanged = pos.getPlane() != lastPos.getPlane();
        boolean jumped = planeChanged || pos.distanceTo(lastPos) > JUMP_TILES;
        if (boatChanged) {
            idleTicks = 0;
        } else if (jumped && aboard) {
            // Still on the boat after a jump: sailing through a cave mouth (Wyrmscraig Cavern). That's
            // part of the trip; where it ends gets its step on getting off or stopping.
            trace("JUMP from=" + lastPos + " to=" + pos + " = sailing through a cave mouth, part of the trip");
            idleTicks = 0;
        } else if (jumped) {
            trace("JUMP from=" + lastPos + " to=" + pos + " planeChanged=" + planeChanged
                + " lastClick=" + (lastClick == null ? "none" : lastClick.describe() + "#" + lastClick.id + "@" + lastClick.tick));
            onJump(lastPos, pos, planeChanged);
            idleTicks = 0;
        } else if (pos.equals(lastPos)) {
            // Instances (the house, many quest and boss areas) report made-up coordinates
            // that can't be walked to later, so stopping inside one never makes a step.
            // On a boat, waiting at the mooring to take the helm isn't a destination; stopping out at
            // sea is. Where the trip ends at a dock gets its step on getting off (see onDisembark).
            boolean readyingBoat = aboard && boardedAt != null && pos.distanceTo2D(boardedAt) <= BOARD_IDLE_RADIUS;
            if (++idleTicks == config.recordIdleTicks() && !inInstance() && !readyingBoat && isNewPlace(pos)
                && !activity.isBusy(pos, client.getTickCount())) {
                trace("IDLE -> location step" + (aboard ? " (stopped at sea)" : ""));
                if (aboard) addDraft(newLocationStep(pos, sailName(pos)), pos);
                else addLocationStep(pos);
            }
        } else {
            idleTicks = 0;
        }
        lastPos = pos;
    }

    /** The player moved further in one tick than walking allows: work out why. */
    private void onJump(WorldPoint from, WorldPoint to, boolean planeChanged) {
        int now = client.getTickCount();
        boolean landedInInstance = inInstance();

        // Portal Nexus: checked before objects, since its right-click teleports are object clicks.
        if (lastNexusDest != null && now - lastNexusTick <= TELEPORT_WINDOW_TICKS && !landedInInstance) {
            String dest = lastNexusDest;
            lastNexusDest = null;
            if (dest.equals(NEXUS_UNREAD)) dest = LocationNames.describe(to);
            RouteStep step = newLocationStep(to, "Teleport: Portal Nexus to " + dest);
            step.setTeleportMethod("NEXUS");
            step.setTeleportNexus(dest);
            step.setTeleportDestination(to);
            addDraft(step, to);
            return;
        }

        // Mid-lap, every jump and drop is the course, whatever was clicked last (players often
        // click ahead while an obstacle is still playing out).
        if (activity.runningCourse(from, now)) { trace("JUMP = mid agility lap, ignored"); return; }

        // Stairs, ladders, trapdoors, dungeon entrances, house portals: the last click was an object.
        if (lastClick != null && lastClick.isObject()) {
            // Agility obstacles jump and climb all the time; that's the course, not travel.
            if (activity.onAgilityCourse(from, lastClick.id, now)) { trace("JUMP = agility obstacle, ignored"); return; }
            if (landedInInstance) {
                if (isHousePortalOption(lastClick.option)) {
                    RouteStep step = newLocationStep(from, "Enter house" + LocationNames.at(from));
                    step.setSkillingTargetObject("Portal");
                    addDraft(step, from);
                }
                // Climbs inside an instance have made-up coordinates; don't carry them over.
                pendingTransitionTile = null;
                pendingTransitionObjectId = -1;
                return;
            }
            trace("JUMP = object click (instance=" + landedInInstance + ")");
            if (planeChanged) {
                // A second climb before any step on this floor: keep the first one as a step
                // at its landing so neither climb is lost.
                if (pendingTransitionTile != null) addLocationStep(from);
                pendingTransitionTile = from;
                pendingTransitionObjectId = lastClick.id;
            }
            // Same-plane object jumps (dungeon entrances, shortcuts, leaving the house by its
            // portal) need nothing: both pathfinders already know those transports.
            return;
        }

        Click tele = lastTeleportClick;
        if (tele != null && now - tele.tick <= TELEPORT_WINDOW_TICKS) {
            lastTeleportClick = null;
            if (addTeleportStep(tele, to, landedInInstance)) return;
        }

        // An instance we can't explain (quest, boss, minigame): nothing walkable to record.
        if (landedInInstance) return;

        trace("JUMP = unexplained travel");
        // Boats, NPC travel, fairy rings, anything else: keep it as a location step with a
        // note of what caused it, so nothing is silently dropped.
        RouteStep step = newLocationStep(to, LocationNames.nameOf(to) != null ? "Travel to " + LocationNames.nameOf(to) : "Travel here");
        String cause = lastClick != null ? lastClick.describe() : "unknown";
        step.setNoteText("Recorded travel (last action: " + cause + "). Edit to describe how to get here.");
        addDraft(step, to);
    }

    /**
     * The player got on a boat, standing at {@code dock} the tick before: a step that has you walk
     * to the gangplank and board, like stairs on a climb. Standing at the dock first already made a
     * plain step there; that one becomes the boarding step.
     */
    private void onBoard(WorldPoint dock) {
        boardedAt = dock;
        String hint = sailingDocks.boardingHint(dock);
        RouteStep step = stepAt(dock, hint);
        step.setWorldPoint(null);   // being on the boat is the goal, not a tile
        step.setTransitionPoint(null);
        step.setBoardPoint(dock);
        Click c = lastBoardClick;
        boolean clickedGangplank = c != null && client.getTickCount() - c.tick <= BOARD_CLICK_TICKS;
        step.setBoardObjectId(clickedGangplank ? c.id : -1);
        trace("BOARD at " + dock + " via " + (clickedGangplank ? c.describe() + "#" + c.id : "no recent Board click"));
    }

    /**
     * The player got off a boat at {@code landed}: the end of the trip, so it gets its step right
     * away ("Sail to Lunar Isle") rather than waiting to stand still. Leaving a boat away from any
     * dock (a teleport, say) is left to the jump handling.
     */
    private void onDisembark(WorldPoint landed) {
        SailingDocks.Dock dock = sailingDocks.nearest(landed, DOCK_RADIUS);
        trace("DISEMBARK at " + landed + " dock=" + (dock == null ? "none" : dock.name));
        if (dock == null) return;
        addDraft(newLocationStep(landed, "Sail to " + dock.name), landed);
    }

    /** "Sail to Lunar Isle", or for a spot out at sea the map has no name for, "Sail out to sea". */
    private static String sailName(WorldPoint pos) {
        String name = LocationNames.nameOf(pos);
        return name != null ? "Sail to " + name : "Sail out to sea";
    }

    private static boolean isHousePortalOption(String option) {
        return "Home".equalsIgnoreCase(option) || "Build mode".equalsIgnoreCase(option);
    }

    private boolean addTeleportStep(Click click, WorldPoint landed, boolean landedInInstance) {
        TeleportSpell spell = matchSpell(click);
        if (spell != null && lands(spell.getDestination(), landed, landedInInstance)) {
            RouteStep step = newTeleportStep(spell.getDestination(), spell.getName());
            step.setTeleportMethod("SPELL");
            step.setTeleportSpell(spell.getName());
            addDraft(step, landed);
            return true;
        }
        TeleportItem item = matchItem(click.itemId, landed, landedInInstance);
        if (item != null) {
            RouteStep step = newTeleportStep(item.getDestination(),
                TeleportItems.isHouse(item.getDestination()) ? item.getItemName() : item.getDisplayName());
            step.setTeleportMethod("ITEM");
            step.setTeleportItem(item.getDisplayName());
            addDraft(step, landed);
            return true;
        }
        return false;
    }

    /**
     * A teleport step shaped like one made in the step editor. A house teleport has no
     * real tile to walk to, so it gets no location; it completes once you're inside.
     */
    private RouteStep newTeleportStep(WorldPoint dest, String what) {
        boolean house = TeleportItems.isHouse(dest);
        RouteStep step = house
            ? new RouteStep("Teleport to house (" + what + ")", StepType.LOCATION, null, -1)
            : newLocationStep(dest, "Teleport: " + what);
        step.setTeleportDestination(dest);
        return step;
    }

    private static TeleportSpell matchSpell(Click click) {
        if (!"Cast".equalsIgnoreCase(click.option)) return null;
        // In-game names can carry a prefix the table doesn't, e.g. "Lumbridge Home Teleport".
        TeleportSpell best = null;
        for (TeleportSpell s : TeleportSpells.ALL) {
            String name = s.getName();
            if (click.target.equalsIgnoreCase(name)) return s;
            if (click.target.toLowerCase().endsWith(name.toLowerCase())
                && (best == null || name.length() > best.getName().length())) {
                best = s;
            }
        }
        return best;
    }

    private static boolean isTeleportItem(int itemId) {
        if (itemId <= 0) return false;
        for (TeleportItem t : TeleportItems.ALL) if (t.matchesItemId(itemId)) return true;
        return false;
    }

    /** The teleport-item destination (glory has several) closest to where the player landed. */
    private static TeleportItem matchItem(int itemId, WorldPoint landed, boolean landedInInstance) {
        TeleportItem best = null;
        int bestDist = Integer.MAX_VALUE;
        for (TeleportItem t : TeleportItems.ALL) {
            if (!t.matchesItemId(itemId) || !lands(t.getDestination(), landed, landedInInstance)) continue;
            int d = TeleportItems.isHouse(t.getDestination()) ? 0 : t.getDestination().distanceTo(landed);
            if (d < bestDist) { bestDist = d; best = t; }
        }
        return best;
    }

    /**
     * Whether landing here fits a teleport's destination. Table destinations are approximate
     * arrival tiles, so accept a landing nearby; the house is any instance.
     */
    private static boolean lands(WorldPoint dest, WorldPoint landed, boolean landedInInstance) {
        if (dest == null) return false;
        if (TeleportItems.isHouse(dest)) return landedInInstance;
        return !landedInInstance && dest.distanceTo(landed) <= 30;
    }

    private boolean isNewPlace(WorldPoint pos) {
        return anchor == null || pos.distanceTo(anchor) > MIN_STEP_SPACING;
    }

    private void addLocationStep(WorldPoint pos) {
        addDraft(newLocationStep(pos, LocationNames.nameOf(pos) != null ? "Go to " + LocationNames.nameOf(pos) : "Go to this spot"), pos);
    }

    /** A location step shaped exactly like one made in the step editor. */
    private RouteStep newLocationStep(WorldPoint pos, String name) {
        RouteStep step = new RouteStep(name, StepType.LOCATION, pos, -1);
        // A climb waiting for its destination floor: this step is where it leads.
        if (pendingTransitionTile != null && pos.getPlane() != pendingTransitionTile.getPlane()) {
            step.setTransitionPoint(pendingTransitionTile);
            step.setTransitionObjectId(pendingTransitionObjectId);
        }
        pendingTransitionTile = null;
        pendingTransitionObjectId = -1;
        return step;
    }

    /**
     * The step for something done at {@code pos}. If the latest step is a plain location step
     * right here (e.g. "Go to Varrock" from standing at the bank), it's reused and renamed, so
     * walking somewhere and then banking is one step, not two. {@code pos} is null in instances.
     */
    RouteStep stepAt(WorldPoint pos, String name) {
        return stepAt(pos, name, null);
    }

    /**
     * Like {@link #stepAt(WorldPoint, String)}, but the latest step nearby is also reused (keeping
     * its name) when {@code compatible} accepts it, e.g. kills joining the loot picked up there.
     */
    RouteStep stepAt(WorldPoint pos, String name, Predicate<RouteStep> compatible) {
        RouteStep last = lastDraft();
        if (last != null && isNear(last, pos)) {
            if (isPlainLocation(last)) {
                trace("REUSE plain step #" + drafts.size() + " as '" + name + "'");
                last.setName(name);
                return last;
            }
            if (compatible != null && compatible.test(last)) { trace("REUSE compatible step #" + drafts.size()); return last; }
        }
        RouteStep step = pos == null
            ? new RouteStep(name, StepType.LOCATION, null, -1)
            : newLocationStep(pos, name);
        addDraft(step, pos != null ? pos : anchor);
        return step;
    }

    int currentTick() {
        return client.getTickCount();
    }

    void removeDraft(RouteStep step) {
        drafts.remove(step);
        trace("REMOVED STEP " + describeStep(step));
    }

    /**
     * The "+ Note" button while recording: the note goes on the latest step if the player is
     * still there, otherwise on a new step here. Call on the client thread.
     */
    public void addNote(String text) {
        if (!recording || text == null || text.trim().isEmpty()) return;
        WorldPoint pos = stepTile();
        RouteStep last = lastDraft();
        RouteStep step = last != null && isNear(last, pos) ? last : stepAt(pos, "Note" + (pos == null ? "" : LocationNames.at(pos)));
        String old = step.getNoteText();
        step.setNoteText(old == null || old.trim().isEmpty() ? text.trim() : old.trim() + "\n" + text.trim());
        step.setNoteAutoComplete(true);
        trace("NOTE added to " + describeStep(step));
    }

    /**
     * Trace what's in the trawling net and the cargo holds when they change, to learn when the game
     * sends them (some containers only update while their window is open) before trawling is built.
     */
    private void traceSailingContainer(ItemContainerChanged e) {
        int id = e.getContainerId();
        String which = id == net.runelite.api.gameval.InventoryID.SAILING_TRAWLING_NET ? "TRAWL NET"
            : id >= net.runelite.api.gameval.InventoryID.SAILING_BOAT_1_CARGOHOLD
                && id <= net.runelite.api.gameval.InventoryID.SAILING_BOAT_5_CARGOHOLD
                ? "CARGO HOLD " + (id - net.runelite.api.gameval.InventoryID.SAILING_BOAT_1_CARGOHOLD + 1) : null;
        if (which == null || !config.recordTrace()) return;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Item item : e.getItemContainer().getItems()) {
            if (item.getId() <= 0) continue;
            counts.merge(client.getItemDefinition(item.getId()).getName(), item.getQuantity(), Integer::sum);
        }
        trace(which + " " + counts);
    }

    /** Whether {@code step} is still one of the drafts (not removed in the meantime). */
    boolean hasDraft(RouteStep step) {
        return drafts.contains(step);
    }

    RouteStep lastDraft() {
        return drafts.isEmpty() ? null : drafts.get(drafts.size() - 1);
    }

    boolean isNear(RouteStep step, WorldPoint pos) {
        return isNear(step, pos, MIN_STEP_SPACING);
    }

    boolean isNear(RouteStep step, WorldPoint pos, int tiles) {
        WorldPoint wp = step.getWorldPoint();
        if (wp == null || pos == null) return wp == null && pos == null;
        return wp.distanceTo(pos) <= tiles;
    }

    boolean runningCourse(WorldPoint pos) {
        return activity.runningCourse(pos != null ? pos : playerPos(), client.getTickCount());
    }

    /** Items that arrived without a pickup, purchase or withdrawal: gathering, if anything. */
    void onItemsGained(Map<String, Long> gains, int tick) {
        trace("GAINED " + gains);
        activity.onItemsGained(gains, tick);
    }

    /** Only a location: nothing else recorded on it yet, so more can be added. */
    private static boolean isPlainLocation(RouteStep s) {
        return s.getTeleportMethod() == null && !s.hasItems() && !s.hasSkillGoal() && !s.hasNote()
            && !s.hasQuest() && !s.hasHighlight()
            && (s.getDialogOptions() == null || s.getDialogOptions().trim().isEmpty());
    }

    /** Where the player is, for putting on a step; null inside instances (made-up coordinates). */
    private WorldPoint stepTile() {
        return inInstance() ? null : playerPos();
    }

    private void addDraft(RouteStep step, WorldPoint playerAt) {
        drafts.add(step);
        trace("NEW STEP #" + drafts.size() + " " + describeStep(step));
        anchor = playerAt;
        idleTicks = 0;
    }

    private boolean inInstance() {
        return client.getTopLevelWorldView() != null && client.getTopLevelWorldView().isInstance();
    }

    private WorldPoint playerPos() {
        return Sailing.position(client);
    }
}
