package com.routeplanner;

import com.google.inject.Provides;
import com.routeplanner.model.Route;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import com.routeplanner.util.RouteSerializer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.npcoverlay.NpcOverlayService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@PluginDescriptor(
    name = "Route Planner",
    description = "Create custom named routes with objectives like a quest helper",
    tags = {"route", "planner", "overlay", "helper"}
)
public class RoutePlannerPlugin extends Plugin {

    @Inject @Getter private Client client;
    @Inject private com.google.gson.Gson gson;
    @Inject @Getter private net.runelite.client.game.SpriteManager spriteManager;
    @Inject @Getter private net.runelite.client.game.ItemManager itemManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private OverlayManager overlayManager;
    @Inject @Getter private net.runelite.client.callback.ClientThread clientThread;
    @Inject @Getter private RoutePlannerConfig config;
    @Inject private ConfigManager configManager;
    @Inject private EventBus eventBus;
    @Inject private RouteOverlay routeOverlay;
    @Inject private MinimapOverlay minimapOverlay;
    @Inject private MinimapArrowOverlay minimapArrowOverlay;
    @Inject private WorldMapPathOverlay worldMapPathOverlay;
    @Inject private RouteHudOverlay routeHudOverlay;
    @Inject @Getter private PathfinderOverlay pathfinderOverlay;
    @Inject @Getter private com.routeplanner.hub.RouteHubCatalog routeHubCatalog;
    @Inject private com.routeplanner.bank.BankOverlay bankOverlay;
    @Inject private com.routeplanner.bank.BankItemManager bankItemManager;
    private com.routeplanner.model.RouteStep lastKillStep = null;
    @Inject private com.routeplanner.dialog.DialogHighlightOverlay dialogHighlightOverlay;
    @Inject private com.routeplanner.skilling.StallHighlightOverlay stallHighlightOverlay;
    @Inject private com.routeplanner.teleport.SpellbookOverlay spellbookOverlay;
    @Inject private com.routeplanner.teleport.TeleportItemOverlay teleportItemOverlay;
    private com.routeplanner.model.RouteStep lastDialogStep = null;
    private int dialogProgress = 0;
    private boolean dialogOptionsVisibleLast = false;
    private boolean anyDialogueOpenLast = false;

    public int getDialogProgress() {
        return dialogProgress;
    }

    private boolean isAnyDialogueOpen() {
        int[] groups = {219, 231, 217, 229, 233};
        for (int g : groups) {
            net.runelite.api.widgets.Widget w = client.getWidget(g, 0);
            if (w != null && !w.isHidden()) return true;
        }
        return false;
    }
    private volatile net.runelite.api.coords.WorldPoint lastPlayerLocation = null;

    public net.runelite.api.coords.WorldPoint getLastPlayerLocation() {
        return lastPlayerLocation;
    }
    @Inject private com.routeplanner.bank.BankFilterButton bankFilterButton;
    @Inject private com.routeplanner.bank.ShopOverlay shopOverlay;
    @Inject private com.routeplanner.bank.GroundItemOverlay groundItemOverlay;
    @Inject @Getter private TileMenuHandler tileMenuHandler;
    @Inject private SkillingNpcHighlighter skillingNpcHighlighter;

    private static final net.runelite.api.Skill[] NPC_GOAL_SKILLS = {
        net.runelite.api.Skill.THIEVING, net.runelite.api.Skill.ATTACK,
        net.runelite.api.Skill.STRENGTH, net.runelite.api.Skill.DEFENCE,
        net.runelite.api.Skill.RANGED, net.runelite.api.Skill.PRAYER,
        net.runelite.api.Skill.MAGIC, net.runelite.api.Skill.HITPOINTS,
        net.runelite.api.Skill.AGILITY, net.runelite.api.Skill.HERBLORE,
        net.runelite.api.Skill.CRAFTING, net.runelite.api.Skill.FLETCHING,
        net.runelite.api.Skill.SLAYER, net.runelite.api.Skill.FARMING,
        net.runelite.api.Skill.RUNECRAFT, net.runelite.api.Skill.HUNTER,
        net.runelite.api.Skill.CONSTRUCTION
    };

    @Getter private List<Route> routes = new ArrayList<>();
    @Getter private final RouteHistory routeHistory = new RouteHistory();
    @Getter private Route activeRoute = null;


    @Getter private RoutePlannerPanel panel;
    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception {
        loadRoutes();
        restoreActiveRoute();

        panel = injector.getInstance(RoutePlannerPanel.class);
        panel.init(this);

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "route_icon.png");
        navButton = NavigationButton.builder()
            .tooltip("Route Planner")
            .icon(icon)
            .priority(7)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);

        overlayManager.add(routeOverlay);
        overlayManager.add(minimapOverlay);
        overlayManager.add(pathfinderOverlay);
        overlayManager.add(minimapArrowOverlay);
        overlayManager.add(worldMapPathOverlay);
        overlayManager.add(routeHudOverlay);
        overlayManager.add(bankOverlay);
        overlayManager.add(shopOverlay);
        overlayManager.add(groundItemOverlay);
        overlayManager.add(dialogHighlightOverlay);
        overlayManager.add(stallHighlightOverlay);
        overlayManager.add(spellbookOverlay);
        overlayManager.add(teleportItemOverlay);
        eventBus.register(bankFilterButton);
        eventBus.register(tileMenuHandler);
        tileMenuHandler.startUp();
        skillingNpcHighlighter.startUp();
        eventBus.register(skillingNpcHighlighter);
    }

    @Override
    protected void shutDown() throws Exception {
        saveRoutes();
        clientToolbar.removeNavigation(navButton);
        overlayManager.remove(routeOverlay);
        overlayManager.remove(minimapOverlay);
        overlayManager.remove(pathfinderOverlay);
        overlayManager.remove(minimapArrowOverlay);
        overlayManager.remove(worldMapPathOverlay);
        overlayManager.remove(routeHudOverlay);
        overlayManager.remove(bankOverlay);
        overlayManager.remove(shopOverlay);
        overlayManager.remove(groundItemOverlay);
        overlayManager.remove(dialogHighlightOverlay);
        overlayManager.remove(stallHighlightOverlay);
        overlayManager.remove(spellbookOverlay);
        overlayManager.remove(teleportItemOverlay);
        eventBus.unregister(bankFilterButton);
        bankFilterButton.reset();
        eventBus.unregister(tileMenuHandler);
        tileMenuHandler.shutDown();
        eventBus.unregister(skillingNpcHighlighter);
        skillingNpcHighlighter.shutDown();
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
        String v = configManager.getConfiguration("routeplanner", HUB_WARNING_COLLAPSED_KEY);
        return "true".equals(v);
    }

    public void setHubWarningCollapsed(boolean collapsed) {
        configManager.setConfiguration("routeplanner", HUB_WARNING_COLLAPSED_KEY, collapsed);
    }

    private static final String STEP_EDITOR_WIDTH_KEY = "stepEditorWidth";
    private static final String STEP_EDITOR_HEIGHT_KEY = "stepEditorHeight";
    private static final int STEP_EDITOR_DEFAULT_WIDTH = 480;
    private static final int STEP_EDITOR_DEFAULT_HEIGHT = 560;

    /** The step editor's last size, remembered per-user so someone who resizes it once (screen/DPI/
     *  font sizes vary a lot) doesn't have to redo it on every single future step edit. Falls back
     *  to a roomier default than the original 320x460, which was cramped even before considering
     *  any per-user resize preference. */
    public java.awt.Dimension getStepEditorSize() {
        Integer w = configManager.getConfiguration("routeplanner", STEP_EDITOR_WIDTH_KEY, Integer.class);
        Integer h = configManager.getConfiguration("routeplanner", STEP_EDITOR_HEIGHT_KEY, Integer.class);
        int width = (w != null && w > 0) ? w : STEP_EDITOR_DEFAULT_WIDTH;
        int height = (h != null && h > 0) ? h : STEP_EDITOR_DEFAULT_HEIGHT;
        return new java.awt.Dimension(width, height);
    }

    public void setStepEditorSize(int width, int height) {
        configManager.setConfiguration("routeplanner", STEP_EDITOR_WIDTH_KEY, width);
        configManager.setConfiguration("routeplanner", STEP_EDITOR_HEIGHT_KEY, height);
    }

    public void deleteRoute(Route route) {
        routeHistory.forget(route);
        if (activeRoute == route) activeRoute = null;
        routes.remove(route);
        saveRoutes();
        panel.refresh();
    }

    public void setActiveRoute(Route route) {
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
        boolean itemsOk = !step.hasItems() || itemsSatisfied(step);
        boolean skillOk = !step.hasSkillGoal() || skillGoalSatisfied(step);
        if (itemsOk && skillOk) {
            completeStep(step);
        }
    }

    private boolean itemsSatisfied(RouteStep step) {
        if (!step.hasItems()) return true;
        if ("SELL".equals(step.getItemMode())) {
            return step.isSellArmed() && bankItemManager.hasSoldAll(step.getItemList());
        }
        return bankItemManager.hasAllItems(step.getItemList());
    }

    private boolean skillGoalSatisfied(RouteStep step) {
        if (!step.hasSkillGoal()) return true;
        if (step.getSkillingSkill() == null || step.getSkillingGoalType() == null) return false;
        net.runelite.api.Skill skill;
        try {
            skill = net.runelite.api.Skill.valueOf(step.getSkillingSkill().toUpperCase());
        } catch (Exception e) {
            return false;
        }
        String goalType = step.getSkillingGoalType();
        if ("XP_GAIN".equals(goalType)) {
            return step.getSkillingProgress() >= step.getSkillingGoalValue();
        } else if ("XP_TARGET".equals(goalType)) {
            return client.getSkillExperience(skill) >= step.getSkillingGoalValue();
        } else if ("LEVEL".equals(goalType)) {
            return client.getRealSkillLevel(skill) >= step.getSkillingGoalValue();
        }
        return false;
    }

    /**
     * Clear all transient per-step progress so an un-completed (or re-activated) step
     * behaves as if fresh: location nav re-arms, SELL re-arms, XP_GAIN re-baselines.
     */
    public void resetStepProgress(RouteStep step) {
        if (step == null) return;
        step.setLocationReached(false);
        step.setSellArmed(false);
        step.setSkillingProgress(0);
        if ("XP_GAIN".equals(step.getSkillingGoalType())) {
            step.setSkillingStartXp(0); // re-capture baseline on next StatChanged
        }
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
            java.awt.Color base = new java.awt.Color(argb, true); // decode ARGB, keep it opaque for the base
            java.awt.Color solid = new java.awt.Color(base.getRed(), base.getGreen(), base.getBlue());
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
            java.awt.Color avg = averageColor(img);
            if (avg == null) {
                trySampleSprite(idx + 1);
                return;
            }
            applyDerivedPalette(avg);
        });
    }

    /** Mean RGB of the opaque pixels; null if the image is empty/too transparent. */
    private java.awt.Color averageColor(java.awt.image.BufferedImage img) {
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
        return new java.awt.Color((int)(r / n), (int)(g / n), (int)(b / n));
    }

    private java.awt.Color lighten(java.awt.Color c, double f) {
        int r = (int) Math.min(255, c.getRed()   + (255 - c.getRed())   * f);
        int g = (int) Math.min(255, c.getGreen() + (255 - c.getGreen()) * f);
        int b = (int) Math.min(255, c.getBlue()  + (255 - c.getBlue())  * f);
        return new java.awt.Color(r, g, b);
    }

    private java.awt.Color darken(java.awt.Color c, double f) {
        return new java.awt.Color(
            (int)(c.getRed() * (1 - f)),
            (int)(c.getGreen() * (1 - f)),
            (int)(c.getBlue() * (1 - f)));
    }

    /** Derive a full HUD palette from one sampled base color and write it to config (Theme -> Custom). */
    private void applyDerivedPalette(java.awt.Color base) {
        java.awt.Color body    = new java.awt.Color(base.getRed(), base.getGreen(), base.getBlue(), 64);
        java.awt.Color border  = new java.awt.Color(lighten(base, 0.35).getRed(), lighten(base, 0.35).getGreen(), lighten(base, 0.35).getBlue(), 180);
        java.awt.Color title   = lighten(base, 0.75);
        java.awt.Color current = lighten(base, 0.9);
        java.awt.Color next    = lighten(base, 0.55);
        java.awt.Color have    = new java.awt.Color(110, 210, 90); // keep green (functional signal)
        java.awt.Color dim     = lighten(darken(base, 0.1), 0.4);
        java.awt.Color divider = new java.awt.Color(lighten(base, 0.4).getRed(), lighten(base, 0.4).getGreen(), lighten(base, 0.4).getBlue(), 45);

        setCol("hudBgColor", body);
        setCol("hudBorderColor", border);
        setCol("hudTitleColor", title);
        setCol("hudCurrentColor", current);
        setCol("hudNextColor", next);
        setCol("hudHaveColor", have);
        setCol("hudDimColor", dim);
        setCol("hudDividerColor", divider);
        configManager.setConfiguration("routeplanner", "hudTheme", com.routeplanner.HudTheme.CUSTOM);
        refreshHighlights();
        log.info("HUD match: derived palette from base {},{},{}", base.getRed(), base.getGreen(), base.getBlue());
    }

    private void setCol(String key, java.awt.Color c) {
        configManager.setConfiguration("routeplanner", key, c);
    }

    public void completeStep(RouteStep step) {
        step.setCompleted(true);
        autoAdvanceSections();
        saveRoutes();
        panel.refresh();
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
        for (com.routeplanner.model.RouteSection sec : activeRoute.getSections()) {
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
            // active section is left as-is -- the user may have manually expanded it on purpose.
        }
    }

    public void resetRoute(Route route) {
        route.resetProgress();
        saveRoutes();
        panel.refresh();
    }

    private com.google.gson.Gson routeGson;
    public com.google.gson.Gson getRouteGson() {
        if (routeGson == null) {
            routeGson = gson.newBuilder()
                .registerTypeAdapter(net.runelite.api.coords.WorldPoint.class, new com.routeplanner.util.RouteSerializer.WorldPointAdapter())
                .setPrettyPrinting()
                .create();
        }
        return routeGson;
    }

    public void saveRoutesPublic() { saveRoutes(); }

    /**
     * Flip edit mode. Backed by the "mode" config key so the choice survives restarts; the panel
     * toggle is just the control surface. onConfigChanged refreshes the panel in response.
     */
    public void setMode(RouteMode mode) {
        configManager.setConfiguration("routeplanner", "mode", mode);
    }

    /** Undo the last structural edit on the active route. @return label undone, or null. */
    public String undoActive() {
        Route r = getActiveRoute();
        if (r == null) return null;
        String label = routeHistory.undo(r, getRouteGson());
        if (label != null) {
            saveRoutesPublic();
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
            saveRoutesPublic();
            if (panel != null) panel.refresh();
        }
        return label;
    }

    public void rebuildNpcHighlights() { skillingNpcHighlighter.rebuild(); }

    private static final String ACTIVE_ROUTE_KEY = "activeRoute";

    private void saveActiveRoute() {
        if (activeRoute != null) {
            configManager.setConfiguration("routeplanner", ACTIVE_ROUTE_KEY, activeRoute.getName());
        } else {
            configManager.unsetConfiguration("routeplanner", ACTIVE_ROUTE_KEY);
        }
    }

    private void restoreActiveRoute() {
        String savedName = configManager.getConfiguration("routeplanner", ACTIVE_ROUTE_KEY);
        if (savedName != null && !savedName.isEmpty()) {
            routes.stream()
                .filter(r -> r.getName().equals(savedName))
                .findFirst()
                .ifPresent(r -> activeRoute = r);
        } else if (!routes.isEmpty()) {
            activeRoute = routes.get(0);
        }
    }

    public void saveRoutes() {
        configManager.setConfiguration("routeplanner", "routes", RouteSerializer.toJson(routes, getRouteGson()));
    }

    private void loadRoutes() {
        String json = configManager.getConfiguration("routeplanner", "routes");
        routes = RouteSerializer.fromJson(json, getRouteGson());
    }

@Subscribe
    public void onGameTick(GameTick tick) {
        pathfinderOverlay.onGameTick();

        if (client.getLocalPlayer() != null) {
            lastPlayerLocation = client.getLocalPlayer().getWorldLocation();
        }

        if (activeRoute == null) return;
        RouteStep step = activeRoute.getActiveStep();

        // Rebuild NPC highlights when skilling step is active
        if (step != null && step.getSkillingTargetNpc() != null) {
            // Rebuild every 10 ticks to catch any missed spawns
            if (client.getTickCount() % 10 == 0) {
                skillingNpcHighlighter.rebuild();
            }
        }

        // Check item step completion
        if (step != null && step.hasItems()) {
            boolean done;
            if ("SELL".equals(step.getItemMode())) {
                // Arm once the items are seen in inventory, complete only after they're gone
                if (!bankItemManager.hasSoldAll(step.getItemList())) {
                    step.setSellArmed(true);
                }
                done = step.isSellArmed() && bankItemManager.hasSoldAll(step.getItemList());
            } else if ("PICKUP".equals(step.getItemMode())) {
                // "Have N in inventory" -- same absolute check as BANK; where you got them
                // only matters for highlighting, handled by GroundItemOverlay.
                done = bankItemManager.hasAllItems(step.getItemList());
            } else {
                done = bankItemManager.hasAllItems(step.getItemList());
            }
            if (done) {
                tryCompleteStep(step);
            }
        }

        // Reset kill progress + light up the NPC when a note-kill step becomes active
        if (step != null && step.getNpcHighlight() != null && step.getNpcKillCount() > 0 && step.isHighlightEnabled()) {
            if (step != lastKillStep) {
                step.setNpcKillProgress(0);
                lastKillStep = step;
                skillingNpcHighlighter.rebuild();
            }
        }

        // Dialogue option highlighting: advance through the sequence as menus open/close
        if (step != null && step.getDialogOptions() != null && !step.getDialogOptions().trim().isEmpty()) {
            if (step != lastDialogStep) {
                lastDialogStep = step;
                dialogProgress = 0;
                dialogOptionsVisibleLast = false;
                anyDialogueOpenLast = false;
            }
            net.runelite.api.widgets.Widget optWidget = client.getWidget(219, 1);
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
        } else {
            lastDialogStep = null;
        }

        // Check teleport completion - detect when player is within 25 tiles of destination
        if (step != null && step.getTeleportDestination() != null
                && !step.hasSkillGoal() && !step.hasItems() && !step.hasNote()) {
            net.runelite.api.coords.WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
            int dist = playerPos.distanceTo(step.getTeleportDestination());
            if (dist <= 25) {
                log.info("Teleport step completed - within 25 tiles of destination");
                completeStep(step);
            }
        }


        if (step == null) return;

        // Sticky "reached": once we arrive, nav visuals (line + tile) switch off for the rest of the step
        if (step.getWorldPoint() != null && !step.isLocationReached()
                && client.getLocalPlayer() != null
                && client.getLocalPlayer().getWorldLocation().distanceTo(step.getWorldPoint()) <= 3) {
            step.setLocationReached(true);
        }

        if (step.getWorldPoint() != null
                && !step.hasSkillGoal() && !step.hasItems() && !step.hasNote()) {
            WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
            if (playerPos.distanceTo(step.getWorldPoint()) <= 3) {
                completeStep(step);
            }
        }
    }

    @Subscribe
    public void onStatChanged(net.runelite.api.events.StatChanged event) {
        if (activeRoute == null) return;
        RouteStep step = activeRoute.getActiveStep();
        if (step == null || !step.hasSkillGoal()) return;
        if (step.getSkillingSkill() == null || step.getSkillingGoalType() == null) return;

        // Dynamically resolve skill by name
        net.runelite.api.Skill skill;
        try {
            skill = net.runelite.api.Skill.valueOf(step.getSkillingSkill().toUpperCase());
        } catch (Exception e) {
            return;
        }
        if (event.getSkill() != skill) return;

        String goalType = step.getSkillingGoalType();

        if (goalType.equals("XP_GAIN")) {
            if (step.getSkillingStartXp() <= 0) {
                step.setSkillingStartXp(event.getXp());
                log.info("Skilling XP_GAIN start: {}", event.getXp());
                return;
            }
            long gained = event.getXp() - step.getSkillingStartXp();
            step.setSkillingProgress(gained);
            log.info("Skilling XP gained: {}/{}", gained, step.getSkillingGoalValue());
            if (gained >= step.getSkillingGoalValue()) {
                log.info("Skilling XP gain goal reached!");
                tryCompleteStep(step);
            }
            if (panel != null) panel.refresh();
        } else if (goalType.equals("XP_TARGET")) {
            if (event.getXp() >= step.getSkillingGoalValue()) {
                log.info("Skilling XP target reached: {}", event.getXp());
                tryCompleteStep(step);
            }
        } else if (goalType.equals("LEVEL")) {
            if (event.getLevel() >= step.getSkillingGoalValue()) {
                log.info("Skilling level goal reached: {}", event.getLevel());
                tryCompleteStep(step);
            }
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (getActiveRoute() == null || getActiveRoute().getActiveStep() == null) return;
        if (activeRoute == null) return;
        RouteStep step = activeRoute.getActiveStep();
        
        if (event.getNpc().getId() == step.getTargetId() && event.getNpc().isDead()) {
            completeStep(step);
        }
    }

    @Subscribe
    public void onItemDespawned(ItemDespawned event) {
        if (activeRoute == null) return;
        RouteStep step = activeRoute.getActiveStep();
        if (step == null || !step.hasItems()) return;
        if (event.getTile().getWorldLocation().equals(step.getWorldPoint())) {
            completeStep(step);
        }
    }

    @Provides
    RoutePlannerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(RoutePlannerConfig.class);
    }

    @Subscribe
    public void onActorDeath(net.runelite.api.events.ActorDeath event) {
        if (activeRoute == null) return;
        RouteStep step = activeRoute.getActiveStep();
        if (step == null) return;
        if (step.getNpcHighlight() == null || step.getNpcKillCount() <= 0 || !step.isHighlightEnabled()) return;
        if (!(event.getActor() instanceof net.runelite.api.NPC)) return;
        String name = ((net.runelite.api.NPC) event.getActor()).getName();
        if (name == null) return;
        String target = step.getNpcHighlight();
        boolean match = name.equalsIgnoreCase(target)
            || name.toLowerCase().startsWith(target.toLowerCase() + " ")
            || name.toLowerCase().startsWith(target.toLowerCase() + "(");
        if (!match) return;
        step.setNpcKillProgress(step.getNpcKillProgress() + 1);
        log.info("Note kill {}/{}: {}", step.getNpcKillProgress(), step.getNpcKillCount(), name);
        if (step.getNpcKillProgress() >= step.getNpcKillCount()) {
            completeStep(step);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!"routeplanner".equals(event.getGroup())) return;
        if ("mode".equals(event.getKey())) {
            if (panel != null) {
                javax.swing.SwingUtilities.invokeLater(panel::refresh);
            }
        }
        if ("hudMatchInterface".equals(event.getKey()) && "true".equals(event.getNewValue())) {
            sampleInterfacePalette();
            configManager.setConfiguration("routeplanner", "hudMatchInterface", false);
        }
    }
}
