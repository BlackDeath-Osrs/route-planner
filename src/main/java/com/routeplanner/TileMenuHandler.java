package com.routeplanner;

import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.swing.*;

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
    private final net.runelite.client.game.ItemManager itemManager;

    // Items collected via shift+right-click in shops, pending bundling into one step
    private final java.util.List<String> pendingShopItems = new java.util.ArrayList<>();
    // Item name under the cursor when the menu was opened
    private String lastRightClickedShopItem = null;

    @Inject
    public TileMenuHandler(Client client, RoutePlannerPlugin plugin,
                           net.runelite.client.game.ItemManager itemManager) {
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
        com.routeplanner.model.RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null) return;
        if (step.getSkillingTargetNpc() == null) return;

        net.runelite.api.MenuEntry entry = event.getMenuEntry();
        if (!(entry.getActor() instanceof NPC)) return;
        NPC npc = (NPC) entry.getActor();
        if (npc.getName() == null) return;

        String npcName = npc.getName();
        String targetName = step.getSkillingTargetNpc();
        boolean nameMatch = npcName.equalsIgnoreCase(targetName)
            || npcName.toLowerCase().startsWith(targetName.toLowerCase() + " ")
            || npcName.toLowerCase().startsWith(targetName.toLowerCase() + "(");
        if (!nameMatch) return;

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
                net.runelite.api.Tile tile = client.getTopLevelWorldView().getSelectedSceneTile();
                if (tile != null) {
                    for (net.runelite.api.GameObject obj : tile.getGameObjects()) {
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
                client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                    "<col=00ff00>Route Planner:</col> added " + lastRightClickedShopItem
                    + " (" + pendingShopItems.size() + " selected)", null);
            }
            event.consume();
            return;
        }

        if (!event.getMenuTarget().contains(MENU_TARGET)) return;

        if (opt.startsWith(SET_TRANSITION_OBJECT)) {
            com.routeplanner.model.RouteStep activeStep = plugin.getActiveRoute() == null ? null
                : plugin.getActiveRoute().getActiveStep();
            if (activeStep == null) return;
            net.runelite.api.Tile tile = client.getTopLevelWorldView().getSelectedSceneTile();
            if (tile == null) return;
            for (net.runelite.api.GameObject obj : tile.getGameObjects()) {
                if (obj != null) {
                    activeStep.setTransitionObjectId(obj.getId());
                    plugin.saveRoutes();
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                        "<col=00ff00>Route Planner:</col> transition object ID set to " + obj.getId(), null);
                    log.info("Transition object ID set to {}", obj.getId());
                    break;
                }
            }
        } else if (opt.equals(SET_TRANSITION_POINT)) {
            net.runelite.api.Tile selectedTile = client.getTopLevelWorldView().getSelectedSceneTile();
            if (selectedTile == null) return;
            com.routeplanner.model.RouteStep activeStep = plugin.getActiveRoute() == null ? null
                : plugin.getActiveRoute().getActiveStep();
            if (activeStep == null) return;
            net.runelite.api.coords.WorldPoint raw = selectedTile.getWorldLocation();
            activeStep.setTransitionPoint(raw);
            plugin.saveRoutes();
            client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                "<col=00ff00>Route Planner:</col> transition point set to " + raw, null);
            log.info("Transition point set to {}", raw);
        } else if (opt.equals(ADD_LOCATION_STEP)) {
            net.runelite.api.Tile selectedTile = client.getTopLevelWorldView().getSelectedSceneTile();
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
            client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                "<col=ff5555>Route Planner:</col> item selection cleared", null);
            event.consume();
        }
    }

    private void createItemStepFromSelection() {
        if (pendingShopItems.isEmpty() || plugin.getActiveRoute() == null) return;
        final java.util.List<String> snapshot = new java.util.ArrayList<>(pendingShopItems);
        pendingShopItems.clear();
        SwingUtilities.invokeLater(() -> {
            String itemList = String.join(", ", snapshot);
            String firstName = snapshot.get(0);
            String stepName = "Buy: " + firstName
                + (snapshot.size() > 1 ? " +" + (snapshot.size() - 1) + " more" : "");
            RouteStep step = new RouteStep(stepName, StepType.ITEM, null, -1);
            step.setItemList(itemList);
            step.setItemMode("SHOP");
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
            new StepEditorDialog(plugin, plugin.getActiveRoute(), null, tile).setVisible(true);
            log.info("Opened step editor seeded at {}", tile);
        });
    }
}
