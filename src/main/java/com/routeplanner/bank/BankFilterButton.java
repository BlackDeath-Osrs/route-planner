package com.routeplanner.bank;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.bank.BankSearch;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Slf4j
@Singleton
public class BankFilterButton {

    private static final String FILTER_TEXT = "Route Filter";
    // Bank interface group ID
    private static final int BANK_GROUP_ID = 12;

    @Inject private Client client;
    @Inject private RoutePlannerPlugin plugin;
    @Inject private BankItemManager bankItemManager;
    @Inject private BankSearch bankSearch;
    @Inject private net.runelite.client.game.ItemManager itemManager;

    private boolean filterActive = false;
    private Widget filterButton = null;
    private final java.util.Map<Integer, int[]> originalPositions = new java.util.HashMap<>();

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == ScriptID.BANKMAIN_BUILD) {
            addFilterButton();
            if (filterActive) applyItemFilter();
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == BANK_GROUP_ID) {
            filterButton = null;
            filterActive = false;
            originalPositions.clear();
            addFilterButton();
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event) {
        if (event.getGroupId() == BANK_GROUP_ID) {
            filterButton = null;
            filterActive = false;
        }
    }

    private void addFilterButton() {
        if (plugin.getActiveRoute() == null) return;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null || !step.hasItems()) return;

        Widget bankContainer = client.getWidget(ComponentID.BANK_CONTAINER);
        if (bankContainer == null) return;

        if (filterButton != null && !filterButton.isHidden()) return;

        filterButton = bankContainer.createChild(-1, WidgetType.TEXT);
        filterButton.setText(filterActive
            ? "<col=ff9900>[" + FILTER_TEXT + " ON]</col>"
            : "<col=aaaaaa>[" + FILTER_TEXT + "]</col>");
        filterButton.setOriginalX(340);
        filterButton.setOriginalY(8);
        filterButton.setOriginalWidth(100);
        filterButton.setOriginalHeight(15);
        filterButton.setFontId(495);
        filterButton.setHasListener(true);
        filterButton.setAction(0, "Toggle");
        filterButton.setName(FILTER_TEXT);
        filterButton.setOnOpListener((JavaScriptCallback) e -> toggleFilter());
        filterButton.revalidate();
    }

    private void toggleFilter() {
        filterActive = !filterActive;
        if (filterButton != null) {
            filterButton.setText(filterActive
                ? "<col=ff9900>[" + FILTER_TEXT + " ON]</col>"
                : "<col=aaaaaa>[" + FILTER_TEXT + "]</col>");
        }

        if (filterActive) {
            applyItemFilter();
        } else {
            bankSearch.reset(true);
        }
    }

    private void applyItemFilter() {
        RouteStep step = plugin.getActiveRoute() != null
            ? plugin.getActiveRoute().getActiveStep() : null;
        if (step == null || step.getItemList() == null) return;

        List<List<String>> groups = bankItemManager.parseItemList(step.getItemList());
        if (groups.isEmpty()) return;

        // Build set of all required item names (lowercased)
        java.util.Set<String> requiredNames = new java.util.HashSet<>();
        for (List<String> group : groups) {
            for (String alt : group) {
                requiredNames.add(bankItemManager.parseItemName(alt).toLowerCase().trim());
            }
        }


        Widget itemContainer = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
        if (itemContainer == null) return;
        Widget[] items = itemContainer.getDynamicChildren();
        if (items == null) return;

        // Save positions first time
        if (originalPositions.isEmpty()) {
            for (Widget item : items) {
                if (item == null) continue;
                originalPositions.put(item.getIndex(),
                    new int[]{item.getOriginalX(), item.getOriginalY()});
            }
        }

        // Sort: required first, hide rest
        java.util.List<Widget> required = new java.util.ArrayList<>();
        for (Widget item : items) {
            if (item == null || item.getItemId() <= 0) continue;
            String name = itemManager.getItemComposition(item.getItemId()).getName();
            if (name == null) { item.setHidden(true); continue; }
            if (requiredNames.contains(name.toLowerCase().trim())) {
                required.add(item);
            } else {
                item.setHidden(true);
            }
        }

        int cols = 8, itemW = 42, itemH = 36, startX = 51, startY = 0;
        for (int i = 0; i < required.size(); i++) {
            Widget item = required.get(i);
            item.setHidden(false);
            item.setOriginalX(startX + (i % cols) * itemW);
            item.setOriginalY(startY + (i / cols) * itemH);
            item.revalidate();
        }
    }

    public void reset() {
        if (filterActive) {
            bankSearch.reset(true);
        }
        filterActive = false;
        filterButton = null;
        originalPositions.clear();
    }
}
