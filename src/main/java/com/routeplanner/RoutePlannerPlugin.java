package com.routeplanner;

import com.google.inject.Provides;
import com.routeplanner.model.Route;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import com.routeplanner.agility.AgilityOverlay;
import com.routeplanner.agility.AgilityTaskManager;
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
    @Inject @Getter private net.runelite.client.game.SpriteManager spriteManager;
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
    @Inject private com.routeplanner.agility.MarkOfGraceOverlay markOfGraceOverlay;
    @Inject private com.routeplanner.bank.BankOverlay bankOverlay;
    @Inject private com.routeplanner.bank.BankItemManager bankItemManager;
    private com.routeplanner.model.RouteStep lastKillStep = null;
    private com.routeplanner.model.RouteStep lastPickupStep = null;
    @Inject private com.routeplanner.dialog.DialogHighlightOverlay dialogHighlightOverlay;
    @Inject private com.routeplanner.skilling.StallHighlightOverlay stallHighlightOverlay;
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
    @Inject @Getter private AgilityTaskManager agilityTaskManager;
    @Inject private SkillingNpcHighlighter skillingNpcHighlighter;
    @Inject private AgilityOverlay agilityOverlay;

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
        overlayManager.add(markOfGraceOverlay);
        eventBus.register(markOfGraceOverlay);
        overlayManager.add(bankOverlay);
        overlayManager.add(shopOverlay);
        overlayManager.add(groundItemOverlay);
        overlayManager.add(dialogHighlightOverlay);
        overlayManager.add(stallHighlightOverlay);
        eventBus.register(bankFilterButton);
        eventBus.register(tileMenuHandler);
        tileMenuHandler.startUp();
        eventBus.register(agilityTaskManager);
        skillingNpcHighlighter.startUp();
        eventBus.register(skillingNpcHighlighter);
        overlayManager.add(agilityOverlay);
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
        overlayManager.remove(markOfGraceOverlay);
        eventBus.unregister(markOfGraceOverlay);
        overlayManager.remove(bankOverlay);
        overlayManager.remove(shopOverlay);
        overlayManager.remove(groundItemOverlay);
        overlayManager.remove(dialogHighlightOverlay);
        overlayManager.remove(stallHighlightOverlay);
        eventBus.unregister(bankFilterButton);
        bankFilterButton.reset();
        eventBus.unregister(tileMenuHandler);
        tileMenuHandler.shutDown();
        eventBus.unregister(agilityTaskManager);
        eventBus.unregister(skillingNpcHighlighter);
        skillingNpcHighlighter.shutDown();
        overlayManager.remove(agilityOverlay);
    }

    public void createRoute(String name) {
        routes.add(new Route(name));
        saveRoutes();
        panel.refresh();
    }

    public void deleteRoute(Route route) {
        if (activeRoute == route) activeRoute = null;
        routes.remove(route);
        saveRoutes();
        panel.refresh();
    }

    public void setActiveRoute(Route route) {
        this.activeRoute = route;
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
        if ((step.getType() == StepType.SKILLING && step.getSkillingTargetNpc() != null)
                || (step.getType() == StepType.NOTE && step.getNpcHighlight() != null)) {
            skillingNpcHighlighter.rebuild();
        }
    }

    public void removeStep(Route route, RouteStep step) {
        // Stop agility task if we're removing an agility step
        if (step.getType() == StepType.AGILITY && agilityTaskManager.getActiveTask() != null) {
            agilityTaskManager.stopTask();
        }
        route.removeStep(step);
        saveRoutes();
        panel.refresh();
    }

    public void completeStep(RouteStep step) {
        step.setCompleted(true);
        if (step.getType() == StepType.AGILITY && agilityTaskManager.getActiveTask() != null) {
            agilityTaskManager.stopTask();
        }
        autoAdvanceSections();
        saveRoutes();
        panel.refresh();
    }

    /** Collapse any fully-completed section and expand the one holding the active step. */
    private void autoAdvanceSections() {
        if (activeRoute == null) return;
        RouteStep active = activeRoute.getActiveStep();
        String activeSectionId = active != null ? active.getSectionId() : null;
        for (com.routeplanner.model.RouteSection sec : activeRoute.getSections()) {
            boolean allDone = sec.getSteps() != null && !sec.getSteps().isEmpty()
                && sec.getSteps().stream().allMatch(RouteStep::isCompleted);
            if (allDone) {
                sec.setCollapsed(true);
            } else if (sec.getId().equals(activeSectionId)) {
                sec.setCollapsed(false);
            }
        }
    }

    public void resetRoute(Route route) {
        route.resetProgress();
        saveRoutes();
        panel.refresh();
    }

    public void saveRoutesPublic() { saveRoutes(); }

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

    private void saveRoutes() {
        configManager.setConfiguration("routeplanner", "routes", RouteSerializer.toJson(routes));
    }

    private void loadRoutes() {
        String json = configManager.getConfiguration("routeplanner", "routes");
        routes = RouteSerializer.fromJson(json);
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
        if (step != null && step.getType() == StepType.SKILLING
                && step.getSkillingTargetNpc() != null) {
            // Rebuild every 10 ticks to catch any missed spawns
            if (client.getTickCount() % 10 == 0) {
                skillingNpcHighlighter.rebuild();
            }
        }

        // Check item step completion
        if (step != null && step.getType() == StepType.ITEM
                && step.getItemList() != null) {
            boolean done;
            if ("SELL".equals(step.getItemMode())) {
                // Arm once the items are seen in inventory, complete only after they're gone
                if (!bankItemManager.hasSoldAll(step.getItemList())) {
                    step.setSellArmed(true);
                }
                done = step.isSellArmed() && bankItemManager.hasSoldAll(step.getItemList());
            } else if ("PICKUP".equals(step.getItemMode())) {
                // Count only items picked up while THIS step is active (snapshot baseline on activation)
                if (step != lastPickupStep) {
                    if (lastPickupStep != null) lastPickupStep.setPickupBaseline(null);
                    step.setPickupBaseline(bankItemManager.snapshotPickupBaseline(step.getItemList()));
                    lastPickupStep = step;
                } else if (step.getPickupBaseline() == null) {
                    step.setPickupBaseline(bankItemManager.snapshotPickupBaseline(step.getItemList()));
                }
                done = bankItemManager.hasPickedUpAll(step.getItemList(), step.getPickupBaseline());
            } else {
                done = bankItemManager.hasAllItems(step.getItemList());
            }
            if (done) {
                completeStep(step);
            }
        }

        // Reset kill progress + light up the NPC when a note-kill step becomes active
        if (step != null && step.getType() == StepType.NOTE
                && step.getNpcHighlight() != null && step.getNpcKillCount() > 0) {
            if (step != lastKillStep) {
                step.setNpcKillProgress(0);
                lastKillStep = step;
                skillingNpcHighlighter.rebuild();
            }
        }

        // Dialogue option highlighting: advance through the sequence as menus open/close
        if (step != null && step.getType() == StepType.NOTE
                && step.getDialogOptions() != null && !step.getDialogOptions().trim().isEmpty()) {
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
                    completeStep(step);
                }
            }
            dialogOptionsVisibleLast = optionsVisible;
            anyDialogueOpenLast = anyOpen;
        } else {
            lastDialogStep = null;
        }

        // Check teleport completion - detect when player is within 25 tiles of destination
        if (step != null && step.getType() == StepType.TELEPORT
                && step.getTeleportDestination() != null) {
            net.runelite.api.coords.WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
            int dist = playerPos.distanceTo(step.getTeleportDestination());
            if (dist <= 25) {
                log.info("Teleport step completed - within 25 tiles of destination");
                completeStep(step);
            }
        }

        // Auto-start agility task when it becomes the active step
        if (step != null && step.getType() == StepType.AGILITY
                && !step.isCompleted()
                && agilityTaskManager.getActiveTask() == null) {
            com.routeplanner.agility.AgilityCourse course =
                com.routeplanner.agility.AgilityCoursePresets.ALL.stream()
                    .filter(c -> c.getName().equals(step.getAgilityCourse()))
                    .findFirst().orElse(null);
            if (course != null) {
                try {
                    com.routeplanner.agility.GoalType goalType =
                        com.routeplanner.agility.GoalType.valueOf(step.getAgilityGoalType());
                    agilityTaskManager.startTask(course, goalType, step.getAgilityGoalValue());
                    log.info("Auto-started agility task: {}", course.getName());
                } catch (Exception e) {
                    log.warn("Failed to auto-start agility task", e);
                }
            }
            return;
        }

        if (step == null) return;

        if (step.getType() == StepType.LOCATION) {
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
        if (step == null || step.getType() != StepType.SKILLING) return;
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
                completeStep(step);
            }
            if (panel != null) panel.refresh();
        } else if (goalType.equals("XP_TARGET")) {
            if (event.getXp() >= step.getSkillingGoalValue()) {
                log.info("Skilling XP target reached: {}", event.getXp());
                completeStep(step);
            }
        } else if (goalType.equals("LEVEL")) {
            if (event.getLevel() >= step.getSkillingGoalValue()) {
                log.info("Skilling level goal reached: {}", event.getLevel());
                completeStep(step);
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
        if (step == null || step.getType() != StepType.ITEM) return;
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
        if (step == null || step.getType() != StepType.NOTE) return;
        if (step.getNpcHighlight() == null || step.getNpcKillCount() <= 0) return;
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
        if ("routeplanner".equals(event.getGroup()) && "mode".equals(event.getKey())) {
            if (panel != null) {
                javax.swing.SwingUtilities.invokeLater(panel::refresh);
            }
        }
    }
}
