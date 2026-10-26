package com.routeplanner;

import com.routeplanner.model.ItemMode;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import com.routeplanner.util.NpcNames;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class TileMenuHandler {

    private static final String ADD_LOCATION_STEP = "Add Location Step";
    private static final String SET_TRANSITION_POINT = "Set Transition Point";
    private static final String SET_TRANSITION_OBJECT = "Set Transition Object";
    private static final String ADD_TO_ITEM_STEP = "Add to Item Step";
    private static final String CREATE_ITEM_STEP = "Create Item Step";
    private static final String CLEAR_SELECTION = "Clear Item Selection";
    private static final String MENU_TARGET = "Route Planner";
    // Shop stock container widget: group 300, child 16
    private static final int SHOP_STOCK_WIDGET_ID = (300 << 16) | 16;

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final ItemManager itemManager;

    // Items collected via shift+right-click in shops, pending bundling into one step
    private final List<String> pendingShopItems = new ArrayList<>();
    // Item name under the cursor when the menu was opened
    private String lastRightClickedShopItem = null;

    @Inject
    public TileMenuHandler(Client client, RoutePlannerPlugin plugin,
                           ItemManager itemManager) {
        this.client = client;
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    public void startUp() {
    }

    public void shutDown() {
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (plugin.getActiveRoute() == null) return;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null) return;
        if (step.getSkillingTargetNpc() == null) return;
        // Only pickpocketing wants its option on left click; Talk-to and kill steps
        // highlight an NPC too but keep the game's own menu order.
        if (!"THIEVING".equalsIgnoreCase(step.getSkillingSkill())) return;

        MenuEntry entry = event.getMenuEntry();
        if (!(entry.getActor() instanceof NPC)) return;
        NPC npc = (NPC) entry.getActor();
        if (npc.getName() == null) return;

        if (!NpcNames.matches(step.getSkillingTargetNpc(), npc.getName())) return;

        String option = entry.getOption();
        // Deprioritize everything except Pickpocket and Examine
        if (option.equalsIgnoreCase("Pickpocket")) {
            entry.setDeprioritized(false);
        } else if (!option.equalsIgnoreCase("Examine") && !option.equalsIgnoreCase("Cancel")) {
            entry.setDeprioritized(true);
        }
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        if (plugin.getActiveRoute() == null) return;

        boolean hasWalkHere = false;
        for (MenuEntry entry : event.getMenuEntries()) {
            if (entry.getOption().equals("Walk here")) hasWalkHere = true;
        }

        if (hasWalkHere && client.isKeyPressed(KeyCode.KC_SHIFT)) {
            client.createMenuEntry(-1)
                .setOption(ADD_LOCATION_STEP)
                .setTarget("<col=00ff00>" + MENU_TARGET + "</col>")
                .setType(MenuAction.RUNELITE)
                .setDeprioritized(false);
            if (plugin.getActiveRoute() != null && plugin.getActiveRoute().getActiveStep() != null) {
                client.createMenuEntry(-1)
                    .setOption(SET_TRANSITION_POINT)
                    .setTarget("<col=00ff00>" + MENU_TARGET + "</col>")
                    .setType(MenuAction.RUNELITE)
                    .setDeprioritized(false);
                // Also offer object ID grab if a game object is under the cursor
                Tile tile = client.getTopLevelWorldView().getSelectedSceneTile();
                if (tile != null) {
                    for (GameObject obj : tile.getGameObjects()) {
                        if (obj != null) {
                            client.createMenuEntry(-1)
                                .setOption(SET_TRANSITION_OBJECT + " (ID:" + obj.getId() + ")")
                                .setTarget("<col=00ff00>" + MENU_TARGET + "</col>")
                                .setType(MenuAction.RUNELITE)
                                .setDeprioritized(false);
                            break;
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String opt = event.getMenuOption();

        // Add to Item Step uses the item name as target, not MENU_TARGET
        if (opt.equals(ADD_TO_ITEM_STEP)) {
            if (lastRightClickedShopItem != null
                    && !pendingShopItems.contains(lastRightClickedShopItem)) {
                pendingShopItems.add(lastRightClickedShopItem);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "<col=00ff00>Route Planner:</col> added " + lastRightClickedShopItem
                    + " (" + pendingShopItems.size() + " selected)", null);
            }
            event.consume();
            return;
        }

        if (!event.getMenuTarget().contains(MENU_TARGET)) return;

        if (opt.startsWith(SET_TRANSITION_OBJECT)) {
            RouteStep activeStep = plugin.getActiveRoute() == null ? null
                : plugin.getActiveRoute().getActiveStep();
            if (activeStep == null) return;
            Tile tile = client.getTopLevelWorldView().getSelectedSceneTile();
            if (tile == null) return;
            for (GameObject obj : tile.getGameObjects()) {
                if (obj != null) {
                    activeStep.setTransitionObjectId(obj.getId());
                    plugin.saveRoutes();
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                        "<col=00ff00>Route Planner:</col> transition object ID set to " + obj.getId(), null);
                    log.info("Transition object ID set to {}", obj.getId());
                    break;
                }
            }
        } else if (opt.equals(SET_TRANSITION_POINT)) {
            Tile selectedTile = client.getTopLevelWorldView().getSelectedSceneTile();
            if (selectedTile == null) return;
            RouteStep activeStep = plugin.getActiveRoute() == null ? null
                : plugin.getActiveRoute().getActiveStep();
            if (activeStep == null) return;
            WorldPoint raw = selectedTile.getWorldLocation();
            activeStep.setTransitionPoint(raw);
            plugin.saveRoutes();
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "<col=00ff00>Route Planner:</col> transition point set to " + raw, null);
            log.info("Transition point set to {}", raw);
        } else if (opt.equals(ADD_LOCATION_STEP)) {
            Tile selectedTile = client.getTopLevelWorldView().getSelectedSceneTile();
            if (selectedTile == null) {
                log.warn("Route Planner: no selected tile found");
                return;
            }
            promptAddLocationStep(selectedTile.getWorldLocation());
        } else if (opt.startsWith(CREATE_ITEM_STEP)) {
            createItemStepFromSelection();
            event.consume();
        } else if (opt.equals(CLEAR_SELECTION)) {
            pendingShopItems.clear();
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "<col=ff5555>Route Planner:</col> item selection cleared", null);
            event.consume();
        }
    }

    private void createItemStepFromSelection() {
        if (pendingShopItems.isEmpty() || plugin.getActiveRoute() == null) return;
        final List<String> snapshot = new ArrayList<>(pendingShopItems);
        pendingShopItems.clear();
        SwingUtilities.invokeLater(() -> {
            String itemList = String.join(", ", snapshot);
            String firstName = snapshot.get(0);
            String stepName = "Buy: " + firstName
                + (snapshot.size() > 1 ? " +" + (snapshot.size() - 1) + " more" : "");
            RouteStep step = new RouteStep(stepName, StepType.ITEM, null, -1);
            step.setItemList(itemList);
            step.setItemMode(ItemMode.SHOP);
            plugin.addStep(plugin.getActiveRoute(), step);
            log.info("Created shop item step with {} items: {}", snapshot.size(), itemList);
        });
    }

    private void promptAddLocationStep(WorldPoint tile) {
        SwingUtilities.invokeLater(() -> {
            if (plugin.getActiveRoute() == null) {
                JOptionPane.showMessageDialog(null, "Select or create a route first.");
                return;
            }
            new QuickStepDialog(plugin, plugin.getActiveRoute(), null, tile).setVisible(true);
            log.info("Opened step editor seeded at {}", tile);
        });
    }
}
