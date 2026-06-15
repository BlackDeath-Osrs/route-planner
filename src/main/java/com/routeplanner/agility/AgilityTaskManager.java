package com.routeplanner.agility;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Slf4j
@Singleton
public class AgilityTaskManager {

    private final Client client;
    private com.routeplanner.RoutePlannerPanel panel;

    @Getter @Setter
    private AgilityTask activeTask = null;

    // Single flat map: TileObject -> obstacle index (mirrors AgilityPlugin approach)
    @Getter
    private final Map<TileObject, Integer> obstacleMap = new LinkedHashMap<>();

    private long lastAgilityXp = -1;
    private boolean needsInitialScan = false;

    @Inject
    public AgilityTaskManager(Client client) {
        this.client = client;
    }

    public void setPanel(com.routeplanner.RoutePlannerPanel panel) {
        this.panel = panel;
    }

    // Returns the TileObject for a given obstacle index, or null
    public TileObject getObstacleObject(int index) {
        for (Map.Entry<TileObject, Integer> e : obstacleMap.entrySet()) {
            if (e.getValue() == index) return e.getKey();
        }
        return null;
    }

@Subscribe
    public void onStatChanged(StatChanged event) {
        if (activeTask == null) return;
        if (event.getSkill() != Skill.AGILITY) return;

        long newXp = event.getXp();
        if (lastAgilityXp == -1) {
            lastAgilityXp = newXp;
            return;
        }

        if (newXp > lastAgilityXp) {
            lastAgilityXp = newXp;
            log.info("Agility XP gained - advancing from obstacle {}",
                activeTask.getCurrentObstacle() != null
                    ? activeTask.getCurrentObstacle().getName() : "?");
            activeTask.advanceObstacle();
            if (panel != null) panel.refresh();

            int level = client.getRealSkillLevel(Skill.AGILITY);
            if (activeTask.isComplete(level, newXp)) {
                log.info("Agility task complete!");
                // Complete the route step via the plugin
                if (panel != null) {
                    com.routeplanner.RoutePlannerPlugin plugin = panel.getPlugin();
                    if (plugin != null && plugin.getActiveRoute() != null) {
                        com.routeplanner.model.RouteStep agilityStep =
                            plugin.getActiveRoute().getActiveStep();
                        if (agilityStep != null &&
                                agilityStep.getType() == com.routeplanner.model.StepType.AGILITY) {
                            plugin.completeStep(agilityStep);
                        }
                    }
                }
                activeTask = null;
                if (panel != null) panel.refresh();
            }
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned e) {
        if (activeTask == null) return;
        tryRegister(e.getGameObject());
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned e) {
        if (activeTask == null) return;
        obstacleMap.remove(e.getGameObject());
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned e) {
        if (activeTask == null) return;
        tryRegister(e.getWallObject());
    }

    @Subscribe
    public void onWallObjectDespawned(WallObjectDespawned e) {
        if (activeTask == null) return;
        obstacleMap.remove(e.getWallObject());
    }

    @Subscribe
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned e) {
        if (activeTask == null) return;
        tryRegister(e.getDecorativeObject());
    }

    @Subscribe
    public void onDecorativeObjectDespawned(DecorativeObjectDespawned e) {
        if (activeTask == null) return;
        obstacleMap.remove(e.getDecorativeObject());
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned e) {
        if (activeTask == null) return;
        tryRegister(e.getGroundObject());
    }

    @Subscribe
    public void onGroundObjectDespawned(GroundObjectDespawned e) {
        if (activeTask == null) return;
        obstacleMap.remove(e.getGroundObject());
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (activeTask == null) return;
        if (obstacleMap.size() < activeTask.getCourse().getObstacles().size()) {
            scanScene();
        }
    }

    private void tryRegister(TileObject obj) {
        if (obj == null || activeTask == null) return;
        List<AgilityObstacle> obstacles = activeTask.getCourse().getObstacles();
        for (int i = 0; i < obstacles.size(); i++) {
            AgilityObstacle obstacle = obstacles.get(i);
            if (!obstacle.matchesId(obj.getId())) continue;

            // If obstacle has a WorldPoint, only register if on the same plane
            if (obstacle.getLocation() != null) {
                if (obj.getWorldLocation().getPlane() != obstacle.getLocation().getPlane()) continue;
                // Also check proximity (within 5 tiles) to avoid wrong location
                if (obj.getWorldLocation().distanceTo2D(obstacle.getLocation()) > 10) continue;
            }

            obstacleMap.put(obj, i);
            log.debug("Registered {} id={} index={} at {}",
                obj.getClass().getSimpleName(), obj.getId(), i, obj.getWorldLocation());
            return;
        }
    }

    public void startTask(AgilityCourse course, GoalType goalType, long goalValue) {
        activeTask = new AgilityTask(course, goalType, goalValue);
        obstacleMap.clear();
        lastAgilityXp = client.getSkillExperience(Skill.AGILITY);
        needsInitialScan = true;
        scanScene();
        log.info("Agility task started: {} found {}/{} obstacles xp={}",
            course.getName(), obstacleMap.size(), course.getObstacles().size(), lastAgilityXp);

        // Save agility step to active route if one exists
        if (panel != null) {
            com.routeplanner.RoutePlannerPlugin plugin = panel.getPlugin();
            if (plugin != null && plugin.getActiveRoute() != null) {
                // Remove any existing agility step first
                plugin.getActiveRoute().removeStepsIf(
                    s -> s.getType() == com.routeplanner.model.StepType.AGILITY);
                // Add new agility step
                com.routeplanner.model.RouteStep agilityStep = new com.routeplanner.model.RouteStep(
                    course.getName() + " (" + goalValue + " " + goalType.name() + ")",
                    course.getName(),
                    goalType.name(),
                    goalValue
                );
                plugin.getActiveRoute().addStepToLastSection(agilityStep);
                plugin.saveRoutesPublic();
                panel.refresh();
            }
        }
    }

    public void stopTask() {
        activeTask = null;
        obstacleMap.clear();
        lastAgilityXp = -1;
    }

    private void scanScene() {
        if (activeTask == null || client.getTopLevelWorldView() == null) return;
        Scene scene = client.getTopLevelWorldView().getScene();
        if (scene == null) return;
        Tile[][][] tiles = scene.getTiles();
        for (int p = 0; p < 4; p++) {
            for (int x = 0; x < 104; x++) {
                for (int y = 0; y < 104; y++) {
                    Tile tile = tiles[p][x][y];
                    if (tile == null) continue;
                    for (GameObject obj : tile.getGameObjects()) tryRegister(obj);
                    tryRegister(tile.getWallObject());
                    tryRegister(tile.getDecorativeObject());
                    tryRegister(tile.getGroundObject());
                }
            }
        }
    }
}
