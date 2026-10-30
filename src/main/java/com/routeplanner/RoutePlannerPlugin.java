package com.routeplanner;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.routeplanner.agility.AgilityCourse;
import com.routeplanner.agility.AgilityCoursePresets;
import com.routeplanner.agility.AgilityObstacle;
import com.routeplanner.bank.BankFilterButton;
import com.routeplanner.bank.BankItemManager;
import com.routeplanner.bank.BankOverlay;
import com.routeplanner.bank.GroundItemOverlay;
import com.routeplanner.bank.InventoryItemOverlay;
import com.routeplanner.bank.ShopOverlay;
import com.routeplanner.dialog.DialogHighlightOverlay;
import com.routeplanner.hub.RouteHubCatalog;
import com.routeplanner.model.ItemMode;
import com.routeplanner.model.Route;
import com.routeplanner.model.RouteSection;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.SkillGoalType;
import com.routeplanner.model.StepType;
import com.routeplanner.model.TileArea;
import com.routeplanner.quest.QuestListOverlay;
import com.routeplanner.record.RecordingOverlay;
import com.routeplanner.record.RouteRecorder;
import com.routeplanner.sailing.CatchMessages;
import com.routeplanner.sailing.NetDepthOverlay;
import com.routeplanner.sailing.Sailing;
import com.routeplanner.sailing.SailingDocks;
import com.routeplanner.sailing.SailingTimers;
import com.routeplanner.sailing.ShoalRouteOverlay;
import com.routeplanner.skilling.StallHighlightOverlay;
import com.routeplanner.teleport.MinigameTeleportOverlay;
import com.routeplanner.teleport.NexusTeleportOverlay;
import com.routeplanner.teleport.SpellbookOverlay;
import com.routeplanner.teleport.TeleportItemOverlay;
import com.routeplanner.teleport.TeleportItems;
import com.routeplanner.ui.RouteIcon;
import com.routeplanner.ui.StepLive;
import com.routeplanner.util.NpcNames;
import com.routeplanner.util.RouteSerializer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.npcoverlay.NpcOverlayService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@PluginDescriptor(
    name = "Route Planner",
    description = "Create custom named routes with objectives like a quest helper",
    tags = {"route", "planner", "overlay", "helper"}
)
public class RoutePlannerPlugin extends Plugin {

    // RuneLite services
    @Inject @Getter private Client client;
    @Inject @Getter private ClientThread clientThread;
    @Inject @Getter private RoutePlannerConfig config;
    @Inject private ConfigManager configManager;
    @Inject private EventBus eventBus;
    @Inject private Gson gson;
    @Inject @Getter private SpriteManager spriteManager;
    @Inject @Getter private ItemManager itemManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private OverlayManager overlayManager;
    @Inject @Named("developerMode") @Getter private boolean developerMode;

    // Overlays (see overlays())
    @Inject private RouteOverlay routeOverlay;
    @Inject private MinimapOverlay minimapOverlay;
    @Inject private MinimapArrowOverlay minimapArrowOverlay;
    @Inject private WorldMapPathOverlay worldMapPathOverlay;
    @Inject private RouteHudOverlay routeHudOverlay;
    @Inject @Getter private PathfinderOverlay pathfinderOverlay;
    @Inject private ShoalRouteOverlay shoalRouteOverlay;
    @Inject private NetDepthOverlay netDepthOverlay;
    @Inject private RecordingOverlay recordingOverlay;
    @Inject private NexusTeleportOverlay nexusTeleportOverlay;
    @Inject private BankOverlay bankOverlay;
    @Inject private ShopOverlay shopOverlay;
    @Inject private GroundItemOverlay groundItemOverlay;
    @Inject private DialogHighlightOverlay dialogHighlightOverlay;
    @Inject private StallHighlightOverlay stallHighlightOverlay;
    @Inject private SpellbookOverlay spellbookOverlay;
    @Inject private MinigameTeleportOverlay minigameTeleportOverlay;
    @Inject private QuestListOverlay questListOverlay;
    @Inject private InventoryItemOverlay inventoryItemOverlay;
    @Inject private TeleportItemOverlay teleportItemOverlay;
    @Inject @Getter private AreaDrawer areaDrawer;

    // Helpers
    @Inject private ShortestPathBridge shortestPathBridge;
    @Inject @Getter private RouteRecorder routeRecorder;
    @Inject @Getter private RouteHubCatalog routeHubCatalog;
    @Inject private BankItemManager bankItemManager;
    @Inject private BankFilterButton bankFilterButton;
    @Inject @Getter private TileMenuHandler tileMenuHandler;
    @Inject private SkillingNpcHighlighter skillingNpcHighlighter;
    @Inject @Getter private SailingDocks sailingDocks;
    @Inject @Getter private SailingTimers sailingTimers;

    // Routes
    @Getter private List<Route> routes = new ArrayList<>();
    @Getter private final RouteHistory routeHistory = new RouteHistory();
    @Getter private Route activeRoute = null;

    // Panel and toolbar
    @Getter private RoutePlannerPanel panel;
    private NavigationButton navButton;
    /** The plugin's icon (the world map orb with a pin once loaded), also used on the bank tab. */
    @Getter private volatile BufferedImage routeIcon;

    // Per-tick tracking
    private volatile WorldPoint lastPlayerLocation = null;
    @Getter private int arrivalHoldTicksRemaining = 0;
    private RouteStep lastKillStep = null;
    private RouteStep lastDialogStep = null;
    /** The step the NPC highlights were last worked out for. */
    private RouteStep highlightedStep;
    private int dialogProgress = 0;
    private boolean dialogOptionsVisibleLast = false;
    private boolean anyDialogueOpenLast = false;
    /** Deposit-all steps: when the bank was opened for this step (-1 = not open). */
    private RouteStep depositStep = null;
    private int depositOpenTick = -1;
    private static final int DEPOSIT_CHECK_TICKS = 5; // ~3 seconds after the bank opens

    /** The roots of the chat-box dialogues: options, NPC and player chat, message boxes, level-ups. */
    private static final int[] DIALOGUE_ROOTS = {
        InterfaceID.Chatmenu.UNIVERSE, InterfaceID.ChatLeft.UNIVERSE,
        InterfaceID.ChatRight.UNIVERSE, InterfaceID.Messagebox.UNIVERSE,
        InterfaceID.LevelupDisplay.UNIVERSE
    };

    private static final Skill[] NPC_GOAL_SKILLS = {
        Skill.THIEVING, Skill.ATTACK,
        Skill.STRENGTH, Skill.DEFENCE,
        Skill.RANGED, Skill.PRAYER,
        Skill.MAGIC, Skill.HITPOINTS,
        Skill.AGILITY, Skill.HERBLORE,
        Skill.CRAFTING, Skill.FLETCHING,
        Skill.SLAYER, Skill.FARMING,
        Skill.RUNECRAFT, Skill.HUNTER,
        Skill.CONSTRUCTION
    };

    public int getDialogProgress() {
        return dialogProgress;
    }

    public WorldPoint getLastPlayerLocation() {
        return lastPlayerLocation;
    }

    private boolean isAnyDialogueOpen() {
        for (int id : DIALOGUE_ROOTS) {
            Widget w = client.getWidget(id);
            if (w != null && !w.isHidden()) return true;
        }
        return false;
    }

    private NavigationButton navButton(BufferedImage icon) {
        return NavigationButton.builder()
            .tooltip("Route Planner")
            .icon(icon)
            .priority(7)
            .panel(panel)
            .build();
    }

    @Override
    protected void startUp() throws Exception {
        loadRoutes();
        sailingDocks.loadLearnedPorts();
        restoreActiveRoute();

        panel = injector.getInstance(RoutePlannerPanel.class);
        panel.init(this);

        // A drawn stand-in until the game's world map orb loads (see RouteIcon), then the real one.
        routeIcon = ImageUtil.loadImageResource(getClass(), "route_icon.png");
        navButton = navButton(routeIcon);
        clientToolbar.addNavigation(navButton);
        RouteIcon.load(spriteManager, icon -> SwingUtilities.invokeLater(() -> {
            if (navButton == null) return; // plugin stopped meanwhile
            routeIcon = icon;
            boolean open = panel.isShowing();
            clientToolbar.removeNavigation(navButton);
            navButton = navButton(icon);
            clientToolbar.addNavigation(navButton);
            if (open) clientToolbar.openPanel(navButton);
        }));

        overlays().forEach(overlayManager::add);
        subscribers().forEach(eventBus::register);
        tileMenuHandler.startUp();
        skillingNpcHighlighter.startUp();
    }

    /** Every overlay the plugin shows, added on start-up and removed on shut-down. */
    private List<Overlay> overlays() {
        return Arrays.asList(routeOverlay, minimapOverlay, pathfinderOverlay, shoalRouteOverlay,
            netDepthOverlay, minimapArrowOverlay, worldMapPathOverlay, routeHudOverlay, bankOverlay, shopOverlay,
            groundItemOverlay, dialogHighlightOverlay, stallHighlightOverlay, spellbookOverlay, minigameTeleportOverlay,
            questListOverlay, inventoryItemOverlay, teleportItemOverlay, recordingOverlay, nexusTeleportOverlay,
            areaDrawer);
    }

    /** The helpers with their own @Subscribe handlers, registered on start-up and unregistered on shut-down. */
    private List<Object> subscribers() {
        return Arrays.asList(sailingTimers, bankFilterButton, tileMenuHandler, shortestPathBridge);
    }

    @Override
    protected void shutDown() throws Exception {
        resumeRoute(); // don't leave a test's item/XP baselines saved on the step
        saveRoutes();
        clientToolbar.removeNavigation(navButton);
        navButton = null;
        overlays().forEach(overlayManager::remove);
        subscribers().forEach(eventBus::unregister);
        pathfinderOverlay.shutDown();
        routeRecorder.cancel();
        areaDrawer.cancel();
        bankFilterButton.reset();
        tileMenuHandler.shutDown();
        skillingNpcHighlighter.shutDown();
        shortestPathBridge.reset();
    }

    public void createRoute(String name) {
        routes.add(new Route(name));
        saveRoutes();
        panel.refresh();
    }

    private static final String HUB_WARNING_COLLAPSED_KEY = "hubWarningCollapsed";

    /** Whether the Route Hub's "Community content" warning banner is collapsed. Defaults to
     *  expanded (false) the first time anyone opens the Hub, same convention as everything else
     *  persisted outside the formal config UI (ACTIVE_ROUTE_KEY, the routes blob itself). */
    public boolean isHubWarningCollapsed() {
        String v = configManager.getConfiguration(RoutePlannerConfig.GROUP, HUB_WARNING_COLLAPSED_KEY);
        return "true".equals(v);
    }

    public void setHubWarningCollapsed(boolean collapsed) {
        configManager.setConfiguration(RoutePlannerConfig.GROUP, HUB_WARNING_COLLAPSED_KEY, collapsed);
    }

    private static final String STEP_EDITOR_WIDTH_KEY = "stepEditorWidth";
    private static final String STEP_EDITOR_HEIGHT_KEY = "stepEditorHeight";
    private static final int STEP_EDITOR_DEFAULT_WIDTH = 480;
    private static final int STEP_EDITOR_DEFAULT_HEIGHT = 560;

    /** The step editor's last size, remembered per-user so someone who resizes it once (screen/DPI/
     *  font sizes vary a lot) doesn't have to redo it on every single future step edit. Falls back
     *  to a roomier default than the original 320x460, which was cramped even before considering
     *  any per-user resize preference. */
    public Dimension getStepEditorSize() {
        Integer w = configManager.getConfiguration(RoutePlannerConfig.GROUP, STEP_EDITOR_WIDTH_KEY, Integer.class);
        Integer h = configManager.getConfiguration(RoutePlannerConfig.GROUP, STEP_EDITOR_HEIGHT_KEY, Integer.class);
        int width = (w != null && w > 0) ? w : STEP_EDITOR_DEFAULT_WIDTH;
        int height = (h != null && h > 0) ? h : STEP_EDITOR_DEFAULT_HEIGHT;
        return new Dimension(width, height);
    }

    public void setStepEditorSize(int width, int height) {
        configManager.setConfiguration(RoutePlannerConfig.GROUP, STEP_EDITOR_WIDTH_KEY, width);
        configManager.setConfiguration(RoutePlannerConfig.GROUP, STEP_EDITOR_HEIGHT_KEY, height);
    }

    public void deleteRoute(Route route) {
        routeHistory.forget(route);
        if (activeRoute == route) activeRoute = null;
        routes.remove(route);
        saveRoutes();
        panel.refresh();
    }

    public void setActiveRoute(Route route) {
        resumeRoute();
        this.activeRoute = route;
        autoAdvanceSections();
        saveActiveRoute();
        panel.refresh();
        skillingNpcHighlighter.rebuild();
    }

    public void addStep(Route route, RouteStep step) {
        addStep(route, step, panel != null ? panel.getSelectedSectionId() : null);
    }

    public void addStep(Route route, RouteStep step, String sectionId) {
        if (sectionId != null) route.addStepToSection(step, sectionId);
        else route.addStepToLastSection(step);
        saveRoutes();
        panel.refresh();
        // Rebuild NPC highlights immediately when a skilling step is added
        if (step.hasHighlight()) {
            skillingNpcHighlighter.rebuild();
        }
    }

    public void removeStep(Route route, RouteStep step) {
        routeHistory.push(route, "Delete step \"" + step.getName() + "\"", getRouteGson());
        route.removeStep(step);
        saveRoutes();
        panel.refresh();
    }

    /**
     * Complete a step only when EVERY trackable component present on it is satisfied.
     * A component that is absent is vacuously satisfied. This is the multi-component rule:
     * e.g. a Bank-items + Skill-goal step completes only after BOTH the items are held
     * AND the skill goal is reached.
     */
    private void tryCompleteStep(RouteStep step) {
        if (step == null) return;
        if (activeRoute != null && step == activeRoute.getTestStep() && activeRoute.isTestPassed()) return;
        boolean itemsOk  = !step.hasItems()     || itemsSatisfied(step);
        boolean skillOk  = !step.hasSkillGoal() || skillGoalSatisfied(step);
        // Location: if worldPoint set, player must have reached it
        // (a catch step's tile is just where the shoal was met; it swims on, so catching anywhere counts)
        boolean locOk    = step.getWorldPoint() == null || step.isLocationReached() || step.hasCatchGoal();
        // Note: if note present, it must be manually marked complete (locationReached used as the ack flag)
        // Kill steps: enough kills, which also stands in for acknowledging the note.
        boolean killsOk  = step.getNpcKillCount() <= 0 || step.getNpcKillProgress() >= step.getNpcKillCount();
        // (a catch step's note, "Wait here for the shoal.", is guidance; the catch completes it)
        boolean noteOk   = !step.hasNote() || step.isLocationReached() || step.getNpcKillCount() > 0
            || step.hasCatchGoal();
        // Dialogue: every option in the sequence picked (arriving alone isn't enough).
        boolean dialogOk = step.getDialogOptions() == null || step.getDialogOptions().trim().isEmpty()
            || (step == lastDialogStep && dialogProgress >= step.getDialogOptions().split(",").length);
        // Quest: when set to complete with the quest, the quest must be finished.
        boolean questOk  = !step.hasQuest() || !step.isQuestComplete() || questFinished(step);
        // Boarding (Sailing): on the boat
        boolean boardOk  = !step.hasBoarding() || step.isBoarded();
        // Catch (Sailing): enough fish caught
        boolean catchOk  = !step.hasCatchGoal() || step.getCatchProgress() >= step.getCatchGoal();
        if (itemsOk && skillOk && locOk && noteOk && killsOk && dialogOk && questOk && boardOk && catchOk) {
            completeStep(step);
        }
    }

    private void takeBaselines(RouteStep step) {
        boolean items = step.countsGainedItems();
        Skill skill = step.skill();
        boolean xp = skill != null && step.getSkillingGoalType() == SkillGoalType.XP_GAIN;
        long account = client.getAccountHash();
        if ((!items && !xp) || account == -1) return;
        boolean fresh = step.getBaselineAccount() != account
            || (items && step.getPickupBaseline() == null) || (xp && step.getSkillingStartXp() <= 0);
        if (!fresh) return;

        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        int nowXp = xp ? client.getSkillExperience(skill) : 0;
        if ((items && inv == null) || (xp && nowXp <= 0)) return; // not loaded yet; next tick
        if (items) step.setPickupBaseline(bankItemManager.snapshotInventory(step.getItemList()));
        if (xp) {
            step.setSkillingStartXp(nowXp);
            step.setSkillingProgress(0);
        }
        step.setBaselineAccount(account);
        saveRoutes();
    }

    private boolean questFinished(RouteStep step) {
        try {
            Quest q = Quest.valueOf(step.getQuestName());
            return q.getState(client) == QuestState.FINISHED;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean itemsSatisfied(RouteStep step) {
        if (!step.hasItems()) return true;
        if (step.getItemMode() == ItemMode.SELL) {
            return step.isSellArmed() && bankItemManager.hasSoldAll(step.getItemList());
        }
        if (step.countsGainedItems()) {
            // Only items picked up / bought since the step started count, never ones already carried.
            return step.getPickupBaseline() != null
                && bankItemManager.hasAllItemsAboveBaseline(step.getItemList(), step.getPickupBaseline());
        }
        return bankItemManager.hasAllItems(step.getItemList());
    }

    private boolean skillGoalSatisfied(RouteStep step) {
        if (!step.hasSkillGoal()) return true;
        Skill skill = step.skill();
        SkillGoalType goalType = step.getSkillingGoalType();
        if (skill == null || goalType == null) return false;
        if (goalType == SkillGoalType.XP_GAIN) {
            return step.getSkillingProgress() >= step.getSkillingGoalValue();
        } else if (goalType == SkillGoalType.XP_TARGET) {
            return client.getSkillExperience(skill) >= step.getSkillingGoalValue();
        } else {
            return client.getRealSkillLevel(skill) >= step.getSkillingGoalValue();
        }
    }

    /**
     * Developer mode (the client started with --developer-mode): make any step the active one to
     * try it out, without ticking the steps before it. Completing it doesn't tick it either; it
     * just says the test passed. {@link #resumeRoute()} goes back to the real active step.
     */
    public void testStep(RouteStep step) {
        if (activeRoute == null || step == null) return;
        resumeRoute();
        step.resetProgress();
        activeRoute.setTestStep(step);
        activeRoute.setTestPassed(false);
        autoAdvanceSections();
        refreshHighlights();
    }

    public void resumeRoute() {
        if (activeRoute == null || activeRoute.getTestStep() == null) return;
        RouteStep step = activeRoute.getTestStep();
        activeRoute.setTestStep(null);
        activeRoute.setTestPassed(false);
        step.resetProgress();
        autoAdvanceSections();
        saveRoutes();
        refreshHighlights();
    }

    private void passStepTest(RouteStep step) {
        activeRoute.setTestPassed(true);
        String name = step.getName();
        clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "Route Planner test passed: \"" + name + "\" would tick itself off here.", null));
        if (panel != null) panel.refresh();
    }

    /** Force overlays/panel to re-read the active step immediately (e.g. after an edit). */
    public void refreshHighlights() {
        if (panel != null) panel.refresh();
        if (skillingNpcHighlighter != null) skillingNpcHighlighter.rebuild();
    }

    /**
     * Read the resource pack's overlay background color (RuneLite config "overlayBackgroundColor",
     * which resource packs set) and derive a matching HUD palette. Instant + exact + pack-aware,
     * and updates whenever the pack changes. Falls back to sprite sampling if the value is absent.
     */
    private void sampleInterfacePalette() {
        Integer argb = configManager.getConfiguration("runelite", "overlayBackgroundColor", Integer.class);
        if (argb != null) {
            Color base = new Color(argb, true); // decode ARGB, keep it opaque for the base
            Color solid = new Color(base.getRed(), base.getGreen(), base.getBlue());
            log.info("HUD match: read overlayBackgroundColor = {},{},{}", solid.getRed(), solid.getGreen(), solid.getBlue());
            applyDerivedPalette(solid);
            return;
        }
        log.info("HUD match: overlayBackgroundColor not set, falling back to sprite sampling");
        trySampleSprite(0);
    }

    /** Sprite IDs to try if the config color is unavailable (fallback path). */
    private static final int[] SAMPLE_SPRITE_IDS = { 297, 1035, 172, 173 };

    private void trySampleSprite(int idx) {
        if (idx >= SAMPLE_SPRITE_IDS.length) {
            log.info("HUD match: no usable sprite color found");
            return;
        }
        final int spriteId = SAMPLE_SPRITE_IDS[idx];
        spriteManager.getSpriteAsync(spriteId, 0, img -> {
            Color avg = averageColor(img);
            if (avg == null) {
                trySampleSprite(idx + 1);
                return;
            }
            applyDerivedPalette(avg);
        });
    }

    /** Mean RGB of the opaque pixels; null if the image is empty/too transparent. */
    private Color averageColor(BufferedImage img) {
        if (img == null) return null;
        long r = 0, g = 0, b = 0, n = 0;
        int w = img.getWidth(), h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a < 128) continue; // skip transparent
                r += (argb >> 16) & 0xFF;
                g += (argb >> 8) & 0xFF;
                b += argb & 0xFF;
                n++;
            }
        }
        if (n == 0) return null;
        return new Color((int)(r / n), (int)(g / n), (int)(b / n));
    }

    private Color lighten(Color c, double f) {
        int r = (int) Math.min(255, c.getRed()   + (255 - c.getRed())   * f);
        int g = (int) Math.min(255, c.getGreen() + (255 - c.getGreen()) * f);
        int b = (int) Math.min(255, c.getBlue()  + (255 - c.getBlue())  * f);
        return new Color(r, g, b);
    }

    private Color darken(Color c, double f) {
        return new Color(
            (int)(c.getRed() * (1 - f)),
            (int)(c.getGreen() * (1 - f)),
            (int)(c.getBlue() * (1 - f)));
    }

    /** Derive a full HUD palette from one sampled base color and write it to config (Theme -> Custom). */
    private void applyDerivedPalette(Color base) {
        Color body    = ColorUtil.colorWithAlpha(base, 64);
        Color border  = ColorUtil.colorWithAlpha(lighten(base, 0.35), 180);
        Color title   = lighten(base, 0.75);
        Color current = lighten(base, 0.9);
        Color next    = lighten(base, 0.55);
        Color have    = new Color(110, 210, 90); // keep green (functional signal)
        Color dim     = lighten(darken(base, 0.1), 0.4);
        Color divider = ColorUtil.colorWithAlpha(lighten(base, 0.4), 45);

        setCol("hudBgColor", body);
        setCol("hudBorderColor", border);
        setCol("hudTitleColor", title);
        setCol("hudCurrentColor", current);
        setCol("hudNextColor", next);
        setCol("hudHaveColor", have);
        setCol("hudDimColor", dim);
        setCol("hudDividerColor", divider);
        configManager.setConfiguration(RoutePlannerConfig.GROUP, "hudTheme", HudTheme.CUSTOM);
        refreshHighlights();
        log.info("HUD match: derived palette from base {},{},{}", base.getRed(), base.getGreen(), base.getBlue());
    }

    private void setCol(String key, Color c) {
        configManager.setConfiguration(RoutePlannerConfig.GROUP, key, c);
    }

    public void completeStep(RouteStep step) {
        if (activeRoute != null && step == activeRoute.getTestStep()) {
            passStepTest(step);
            return;
        }
        step.setCompleted(true);
        autoAdvanceSections();
        // If route is repeatable and all steps are now done, reset and restart
        if (activeRoute != null && activeRoute.isRepeatable()
                && activeRoute.getActiveStep() == null) {
            resetRoute(activeRoute);
            return;
        }
        saveRoutes();
        panel.refresh();
        // The NPC highlighter only re-checks NPCs when told: drop the finished step's highlights now.
        skillingNpcHighlighter.rebuild();
        highlightedStep = activeRoute != null ? activeRoute.getActiveStep() : null;
    }

    /** Collapse any fully-completed OR not-yet-started section, and expand the one holding the
     *  active step. Runs after completing a step, and also once when a route is first activated --
     *  the latter matters because a freshly-selected route (nothing completed anywhere yet) would
     *  otherwise leave every section exactly as it was serialized/imported, which for a multi-
     *  section route (e.g. a large imported guide) means everything shows expanded at once. */
    private void autoAdvanceSections() {
        if (activeRoute == null) return;
        RouteStep active = activeRoute.getActiveStep();
        String activeSectionId = active != null ? active.getSectionId() : null;
        for (RouteSection sec : activeRoute.getSections()) {
            boolean isActive = sec.getId().equals(activeSectionId);
            if (isActive) {
                sec.setCollapsed(false);
                continue;
            }
            boolean hasSteps = sec.getSteps() != null && !sec.getSteps().isEmpty();
            boolean allDone = hasSteps && sec.getSteps().stream().allMatch(RouteStep::isCompleted);
            boolean noneDone = hasSteps && sec.getSteps().stream().noneMatch(RouteStep::isCompleted);
            if (allDone || noneDone) {
                sec.setCollapsed(true);
            }
            // A section that's partially in progress (some steps done, some not) but isn't the
            // active section is left as-is: the user may have manually expanded it on purpose.
        }
    }

    /**
     * Agility steps point at the course's first obstacle. The preset start tiles are estimates, so
     * once that obstacle is in the loaded scene, move the step onto it (and save). Works the same
     * for recorded, hand-made and Hub routes.
     */
    /** The agility step already on its first obstacle; it isn't scanned for again. */
    private RouteStep snappedAgilityStep;

    private void snapAgilityStart(RouteStep step) {
        if (step == snappedAgilityStep) return;
        if (step.isLocationReached() || !"AGILITY".equalsIgnoreCase(step.getSkillingSkill())) return;
        String name = step.getRecipeLabel() != null ? step.getRecipeLabel() : step.getAgilityCourse();
        if (name == null) return;
        AgilityCourse course = null;
        for (AgilityCourse c : AgilityCoursePresets.ALL) {
            if (c.getName().equalsIgnoreCase(name)) { course = c; break; }
        }
        if (course == null || course.getObstacles().isEmpty()) return;
        AgilityObstacle first = course.getObstacles().get(0);

        WorldPoint current = step.getWorldPoint();
        WorldPoint best = null;
        int bestDist = Integer.MAX_VALUE;
        WorldView wv = client.getTopLevelWorldView();
        if (wv == null || wv.isInstance()) return;
        Tile[][] plane = wv.getScene().getTiles()[wv.getPlane()];
        for (Tile[] row : plane) {
            for (Tile t : row) {
                if (t == null) continue;
                // Checked in place: a list per tile meant ~10,000 throwaway lists per scan.
                for (GameObject g : t.getGameObjects()) {
                    WorldPoint at = obstacleAt(g, first);
                    if (at != null && obstacleDistance(current, at) < bestDist) { bestDist = obstacleDistance(current, at); best = at; }
                }
                for (int i = 0; i < 3; i++) {
                    TileObject o = i == 0 ? t.getWallObject() : i == 1 ? t.getDecorativeObject() : t.getGroundObject();
                    WorldPoint at = obstacleAt(o, first);
                    if (at != null && obstacleDistance(current, at) < bestDist) { bestDist = obstacleDistance(current, at); best = at; }
                }
            }
        }
        if (best == null) return; // not in the loaded scene yet; look again later
        snappedAgilityStep = step;
        if (current != null && current.getPlane() == best.getPlane() && current.distanceTo2D(best) <= 1) return;
        log.info("Agility step '{}': start moved from {} to the {} at {}", step.getName(), current, first.getName(), best);
        step.setWorldPoint(best);
        pathfinderOverlay.clearPath();
        saveRoutes();
    }

    private static WorldPoint obstacleAt(TileObject o, AgilityObstacle obstacle) {
        return o != null && obstacle.matchesId(o.getId()) ? o.getWorldLocation() : null;
    }

    private static int obstacleDistance(WorldPoint current, WorldPoint at) {
        return current == null || current.getPlane() != at.getPlane() ? 0 : current.distanceTo2D(at);
    }

    /** Hand the side panel's step card this tick's progress (only while the panel is open). */
    private void pushLive(RouteStep step) {
        if (panel == null || !panel.isShowing()) return;
        if (step == null) { panel.updateLive(null); return; }

        int dist = StepLive.NO_TARGET;
        WorldPoint wp = step.getWorldPoint();
        if (wp != null && lastPlayerLocation != null) {
            dist = PathfinderOverlay.otherFloor(wp, lastPlayerLocation) ? StepLive.OTHER_FLOOR
                : lastPlayerLocation.distanceTo2D(wp);
        }

        String header = null;
        List<BankItemManager.ItemProgress> items = null;
        if (step.hasItems() && step.getItemList() != null) {
            ItemMode mode = step.getItemMode();
            if (mode == ItemMode.SELL) {
                header = "Sell";
                items = new ArrayList<>();
                for (String[] st : bankItemManager.getSellStatusList(step.getItemList())) {
                    items.add(bankItemManager.soldLine(st[0], "SOLD".equals(st[1])));
                }
            } else {
                boolean gathering = step.getSkillingTargetNpc() != null || step.getSkillingTargetObject() != null;
                header = mode == ItemMode.SHOP ? "Buy" : mode == ItemMode.PICKUP ? (gathering ? "Gather" : "Pick up") : "Bring";
                items = bankItemManager.getItemProgress(step.getItemList(),
                    step.countsGainedItems() ? step.getPickupBaseline() : null);
            }
        }

        String skillLabel = null;
        long have = 0, need = 0;
        Skill sk = step.skill();
        SkillGoalType goal = step.getSkillingGoalType();
        if (sk != null && goal != null) {
            String name = sk.getName();
            need = step.getSkillingGoalValue();
            if (goal == SkillGoalType.XP_GAIN) { skillLabel = name + " XP gained"; have = step.getSkillingProgress(); }
            else if (goal == SkillGoalType.XP_TARGET) { skillLabel = name + " XP"; have = client.getSkillExperience(sk); }
            else { skillLabel = name + " level"; have = client.getRealSkillLevel(sk); }
        }
        int dialogDone = step == lastDialogStep ? dialogProgress : 0;
        panel.updateLive(new StepLive(step, dist, header, items, skillLabel, have, need, dialogDone));
    }

    public void resetRoute(Route route) {
        route.resetProgress();
        pathfinderOverlay.clearPath();
        saveRoutes();
        panel.refresh();
    }

    private Gson routeGson;
    private Gson exportGson;

    /** Reads and writes routes compactly: saves and undo snapshots aren't read by people, and
     *  indentation made them 2-3 times bigger. Either form reads back the same. */
    public Gson getRouteGson() {
        if (routeGson == null) {
            routeGson = gson.newBuilder()
                .registerTypeAdapter(WorldPoint.class, new RouteSerializer.WorldPointAdapter())
                .create();
        }
        return routeGson;
    }

    /** Like getRouteGson, but indented, for exported files people open and share. */
    public Gson getExportGson() {
        if (exportGson == null) exportGson = getRouteGson().newBuilder().setPrettyPrinting().create();
        return exportGson;
    }

    /**
     * Flip edit mode. Backed by the "mode" config key so the choice survives restarts; the panel
     * toggle is just the control surface. onConfigChanged refreshes the panel in response.
     */
    public void setMode(RouteMode mode) {
        configManager.setConfiguration(RoutePlannerConfig.GROUP, "mode", mode);
    }

    /** Undo the last structural edit on the active route. @return label undone, or null. */
    public String undoActive() {
        Route r = getActiveRoute();
        if (r == null) return null;
        String label = routeHistory.undo(r, getRouteGson());
        if (label != null) {
            saveRoutes();
            if (panel != null) panel.refresh();
        }
        return label;
    }

    /** Redo the last undone structural edit on the active route. @return label redone, or null. */
    public String redoActive() {
        Route r = getActiveRoute();
        if (r == null) return null;
        String label = routeHistory.redo(r, getRouteGson());
        if (label != null) {
            saveRoutes();
            if (panel != null) panel.refresh();
        }
        return label;
    }

    public void rebuildNpcHighlights() { skillingNpcHighlighter.rebuild(); }

    private static final String ACTIVE_ROUTE_KEY = "activeRoute";

    private void saveActiveRoute() {
        if (activeRoute != null) {
            configManager.setConfiguration(RoutePlannerConfig.GROUP, ACTIVE_ROUTE_KEY, activeRoute.getName());
        } else {
            configManager.unsetConfiguration(RoutePlannerConfig.GROUP, ACTIVE_ROUTE_KEY);
        }
    }

    private void restoreActiveRoute() {
        String savedName = configManager.getConfiguration(RoutePlannerConfig.GROUP, ACTIVE_ROUTE_KEY);
        if (savedName != null && !savedName.isEmpty()) {
            routes.stream()
                .filter(r -> r.getName().equals(savedName))
                .findFirst()
                .ifPresent(r -> activeRoute = r);
        } else if (!routes.isEmpty()) {
            activeRoute = routes.get(0);
        }
    }

    private static final String ROUTES_BACKUP_KEY = "routesBackup";
    /** Set when the saved routes couldn't be read: saving is then off so they aren't overwritten. */
    private boolean routesLoadFailed = false;

    public void saveRoutes() {
        if (routesLoadFailed) {
            log.warn("Route Planner: not saving, the saved routes couldn't be loaded (copy kept under '{}')", ROUTES_BACKUP_KEY);
            return;
        }
        configManager.setConfiguration(RoutePlannerConfig.GROUP, "routes", RouteSerializer.toJson(routes, getRouteGson()));
    }

    private void loadRoutes() {
        String json = configManager.getConfiguration(RoutePlannerConfig.GROUP, "routes");
        try {
            routes = RouteSerializer.fromJson(json, getRouteGson());
            routesLoadFailed = false;
        } catch (Exception e) {
            // Keep the unreadable text and don't save over it, so nothing is lost and a fix can recover it.
            log.error("Route Planner: couldn't load the saved routes; saving is off until this is fixed", e);
            configManager.setConfiguration(RoutePlannerConfig.GROUP, ROUTES_BACKUP_KEY, json);
            routes = new ArrayList<>();
            routesLoadFailed = true;
            clientThread.invokeLater(() -> {
                if (client.getGameState() != GameState.LOGGED_IN) return false; // try again next tick
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "Route Planner couldn't load your saved routes. They're kept safe and nothing will be "
                        + "saved over them; changes made now won't be saved.", null);
                return true;
            });
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        pathfinderOverlay.onGameTick();
        sailingTimers.onGameTick();

        if (client.getLocalPlayer() != null) {
            lastPlayerLocation = Sailing.position(client);
        }

        RouteStep step = activeRoute != null ? activeRoute.getActiveStep() : null;
        // Any other way the step changes (undo, reset, a step marked incomplete, another route): re-light NPCs
        if (step != highlightedStep) {
            highlightedStep = step;
            skillingNpcHighlighter.rebuild();
        }
        if (activeRoute == null) { pushLive(null); return; }
        pushLive(step);
        if (step == null) { lastDialogStep = null; return; }

        // The order matters: baselines before the item check, and boarding before arrival.
        if (client.getTickCount() % 5 == 0) snapAgilityStart(step);
        refreshNpcHighlights(step);
        // Baselines, taken the moment the step becomes active: the inventory for PICKUP/SHOP (so
        // carried-over items don't count; MUST happen before the completion check) and the starting
        // XP for XP_GAIN (so the very first drop counts). They're saved with the step, so a reload
        // carries on; a reset, an imported route or another account takes fresh ones.
        takeBaselines(step);
        // Quest steps set to complete with the quest: check now and then (it runs a client script).
        if (step.hasQuest() && step.isQuestComplete() && client.getTickCount() % 3 == 0) tryCompleteStep(step);
        checkItems(step);
        startKillCount(step);
        trackDialogue(step);
        checkTeleportArrival(step);
        if (checkBoarding(step)) return;
        checkArrival(step);
    }

    /** Rebuild NPC highlights now and then while a step highlights NPCs, so ones that came into
     *  the 30-tile range since they spawned light up (the overlay service only checks at spawn). */
    private void refreshNpcHighlights(RouteStep step) {
        // Kept to an area, NPCs walking in and out of it need a quicker look
        int every = step.isHighlightInAreaOnly() && areaOf(step) != null ? 2 : 10;
        if (step.isHighlightEnabled() && (step.getSkillingTargetNpc() != null || step.getNpcHighlight() != null)
                && client.getTickCount() % every == 0) {
            skillingNpcHighlighter.rebuild();
        }
    }

    private void checkItems(RouteStep step) {
        if (!step.hasItems()) return;
        boolean done;
        if (step.getItemMode() == ItemMode.DEPOSIT_ALL) {
            done = depositDone(step);
        } else {
            // SELL arms once the items are seen in the inventory, and completes only after they're gone.
            if (step.getItemMode() == ItemMode.SELL && !bankItemManager.hasSoldAll(step.getItemList())) {
                step.setSellArmed(true);
            }
            done = itemsSatisfied(step);
        }
        if (done) tryCompleteStep(step);
    }

    /** Any bank counts as being there (banks have many booths). About 3 seconds after it opens,
     *  check the inventory; keep checking while it's open until it's empty. */
    private boolean depositDone(RouteStep step) {
        Widget bankWidget = client.getWidget(InterfaceID.Bankmain.ITEMS);
        boolean bankOpen = bankWidget != null && !bankWidget.isHidden();
        if (!bankOpen || step != depositStep) {
            depositStep = step;
            depositOpenTick = bankOpen ? client.getTickCount() : -1;
        } else if (depositOpenTick < 0) {
            depositOpenTick = client.getTickCount();
        }
        if (bankOpen) step.setLocationReached(true);
        if (!bankOpen || client.getTickCount() - depositOpenTick < DEPOSIT_CHECK_TICKS) return false;
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        return inv != null && Arrays.stream(inv.getItems()).allMatch(i -> i.getId() <= 0);
    }

    /** Reset kill progress + light up the NPC when a note-kill step becomes active. */
    private void startKillCount(RouteStep step) {
        if (step.getNpcHighlight() != null && step.getNpcKillCount() > 0 && step != lastKillStep) {
            step.setNpcKillProgress(0);
            lastKillStep = step;
            skillingNpcHighlighter.rebuild();
        }
    }

    /** Dialogue option highlighting: advance through the sequence as menus open/close. */
    private void trackDialogue(RouteStep step) {
        if (step.getDialogOptions() == null || step.getDialogOptions().trim().isEmpty()) {
            lastDialogStep = null;
            return;
        }
        if (step != lastDialogStep) {
            lastDialogStep = step;
            dialogProgress = 0;
            dialogOptionsVisibleLast = false;
            anyDialogueOpenLast = false;
        }
        Widget optWidget = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
        boolean optionsVisible = optWidget != null && !optWidget.isHidden();
        boolean anyOpen = optionsVisible || isAnyDialogueOpen();
        if (anyOpen && !anyDialogueOpenLast) {
            dialogProgress = 0;
        }
        if (!optionsVisible && dialogOptionsVisibleLast) {
            dialogProgress++;
            if (dialogProgress >= step.getDialogOptions().split(",").length) {
                tryCompleteStep(step);
            }
        }
        dialogOptionsVisibleLast = optionsVisible;
        anyDialogueOpenLast = anyOpen;
    }

    /** Teleport arrival. Landing only finishes the step when the teleport spot IS the goal: no
     *  tile of its own, or a tile at the landing spot. A tile somewhere else still has to be
     *  walked to after landing (e.g. teleport to Varrock, then go to the east bank). */
    private void checkTeleportArrival(RouteStep step) {
        if (step.getTeleportDestination() == null || step.hasSkillGoal() || step.hasItems() || step.hasNote()
                || client.getLocalPlayer() == null) return;
        WorldPoint dest = step.getTeleportDestination();
        WorldPoint goal = step.getWorldPoint();
        boolean house = TeleportItems.isHouse(dest);
        boolean landingIsGoal = goal == null
            || (!house && goal.getPlane() == dest.getPlane() && goal.distanceTo2D(dest) <= 25);
        if (!landingIsGoal) return;
        WorldPoint playerPos = Sailing.position(client);
        boolean landed = house
            ? client.getTopLevelWorldView().isInstance() // the house has no fixed tile
            : playerPos.getPlane() == dest.getPlane() && playerPos.distanceTo2D(dest) <= 25;
        if (landed) {
            if (goal != null) step.setLocationReached(true);
            // Through the full check: chat options or a quest on the step still count.
            tryCompleteStep(step);
        }
    }

    /** Boarding (Sailing): getting on a boat ends the walk to the gangplank. A step that only asks
     *  you to board is then done; otherwise it carries on over the sea. @return true if that completed it */
    private boolean checkBoarding(RouteStep step) {
        if (!step.hasBoarding() || step.isBoarded() || !Sailing.isSailing(client)) return false;
        step.setBoarded(true);
        tryCompleteStep(step);
        return step.isCompleted();
    }

    private void checkArrival(RouteStep step) {
        boolean boardingPending = pathfinderOverlay.isBoardingPending(step);

        // Sticky "reached": once we arrive, nav visuals (line + tile) switch off for the rest of the step
        if (step.getWorldPoint() != null && !step.isLocationReached() && !boardingPending
                && client.getLocalPlayer() != null && isAtStep(step)) {
            step.setLocationReached(true);
            arrivalHoldTicksRemaining = config.arrivalHideDelay(); // 30 = never hide until the step completes
        }

        // Count down the arrival-hold timer each tick; at the max (30) it never counts down.
        if (arrivalHoldTicksRemaining > 0 && arrivalHoldTicksRemaining < 30) arrivalHoldTicksRemaining--;

        if (step.getWorldPoint() != null && !step.hasSkillGoal() && !step.hasItems() && !boardingPending
                && (!step.hasNote() || step.isNoteAutoComplete())) {
            if (isAtStep(step)) {
                // Through the full check: a Talk-to or quest step still waits for its picks/quest.
                tryCompleteStep(step);
            }
        }
    }

    /** The step's area while area steps are on (a beta feature), else null: off, steps go by their tile. */
    public TileArea areaOf(RouteStep step) {
        return step != null && config.betaFeatures() ? step.area() : null;
    }

    /** Whether the step highlights something on tile {@code p}: anywhere, unless it keeps highlights to its area. */
    public boolean highlightAllowed(RouteStep step, WorldPoint p) {
        TileArea area = step != null && step.isHighlightInAreaOnly() ? areaOf(step) : null;
        return area == null || area.contains(p);
    }

    /** As {@link #highlightAllowed(RouteStep, WorldPoint)} for something covering w x h tiles from {@code sw}: any of them. */
    public boolean highlightAllowed(RouteStep step, WorldPoint sw, int w, int h) {
        TileArea area = step != null && step.isHighlightInAreaOnly() ? areaOf(step) : null;
        if (area == null) return true;
        if (sw.getPlane() != area.getPlane()) return false;
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++) if (area.contains(sw.getX() + x, sw.getY() + y)) return true;
        return false;
    }

    /** Anywhere in the step's area, or else within 3 tiles of its tile (from the hull while sailing). */
    private boolean isAtStep(RouteStep step) {
        TileArea area = areaOf(step);
        if (area != null) {
            WorldPoint at = Sailing.position(client);
            return at != null && area.contains(at);
        }
        return Sailing.isWithin(client, step.getWorldPoint(), 3);
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (activeRoute == null) return;
        RouteStep step = activeRoute.getActiveStep();
        if (step == null) return;
        Skill skill = step.skill();
        SkillGoalType goalType = step.getSkillingGoalType();
        if (skill == null || goalType == null || event.getSkill() != skill) return;

        if (goalType == SkillGoalType.XP_GAIN) {
            if (step.getSkillingStartXp() <= 0) {
                step.setSkillingStartXp(event.getXp());
                log.debug("Skilling XP_GAIN start: {}", event.getXp());
                return;
            }
            long gained = event.getXp() - step.getSkillingStartXp();
            step.setSkillingProgress(gained);
            log.debug("Skilling XP gained: {}/{}", gained, step.getSkillingGoalValue());
            if (gained >= step.getSkillingGoalValue()) {
                log.debug("Skilling XP gain goal reached!");
                tryCompleteStep(step);
            }
            // No panel rebuild per XP drop: the step card's live progress already shows it.
        } else if (goalType == SkillGoalType.XP_TARGET) {
            if (event.getXp() >= step.getSkillingGoalValue()) {
                log.debug("Skilling XP target reached: {}", event.getXp());
                tryCompleteStep(step);
            }
        } else if (event.getLevel() >= step.getSkillingGoalValue()) {
            log.debug("Skilling level goal reached: {}", event.getLevel());
            tryCompleteStep(step);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (activeRoute == null) return;
        RouteStep step = activeRoute.getActiveStep();
        if (step == null || step.getTargetId() <= 0) return; // -1 = none; 0 = left out of older saves
        // The kill is one goal; the step's others (items, location, ...) still have to be met.
        if (event.getNpc().getId() == step.getTargetId() && event.getNpc().isDead()) {
            tryCompleteStep(step);
        }
    }

    @Subscribe
    public void onItemDespawned(ItemDespawned event) {
        if (activeRoute == null) return;
        RouteStep step = activeRoute.getActiveStep();
        if (step == null || !step.hasItems()) return;
        if (event.getTile().getWorldLocation().equals(step.getWorldPoint())) {
            tryCompleteStep(step);
        }
    }

    @Provides
    RoutePlannerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(RoutePlannerConfig.class);
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        if (activeRoute == null) return;
        RouteStep step = activeRoute.getActiveStep();
        if (step == null) return;
        if (step.getNpcHighlight() == null || step.getNpcKillCount() <= 0) return;
        if (!(event.getActor() instanceof NPC)) return;
        String name = ((NPC) event.getActor()).getName();
        if (!NpcNames.matches(step.getNpcHighlight(), name)) return;
        step.setNpcKillProgress(step.getNpcKillProgress() + 1);
        log.debug("Note kill {}/{}: {}", step.getNpcKillProgress(), step.getNpcKillCount(), name);
        if (step.getNpcKillProgress() >= step.getNpcKillCount()) {
            tryCompleteStep(step);
        }
    }

    /** Trawling catches (Sailing) count toward the active step's catch goal. */
    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;
        if (activeRoute == null) return;
        RouteStep step = activeRoute.getActiveStep();
        if (step == null || !step.hasCatchGoal()) return;
        CatchMessages.Catch c = CatchMessages.parse(event.getMessage());
        if (c == null || !CatchMessages.sameFish(c.fish, step.getCatchFish())) return;
        step.setCatchProgress(step.getCatchProgress() + c.count);
        tryCompleteStep(step);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            sailingDocks.reset();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!RoutePlannerConfig.GROUP.equals(event.getGroup())) return;
        if ("mode".equals(event.getKey())) {
            if (panel != null) {
                SwingUtilities.invokeLater(panel::refresh);
            }
        }
        if ("pathfinder".equals(event.getKey())) {
            pathfinderOverlay.clearPath();
        }
        if ("hudMatchInterface".equals(event.getKey()) && "true".equals(event.getNewValue())) {
            sampleInterfacePalette();
            configManager.setConfiguration(RoutePlannerConfig.GROUP, "hudMatchInterface", false);
        }
    }
}
