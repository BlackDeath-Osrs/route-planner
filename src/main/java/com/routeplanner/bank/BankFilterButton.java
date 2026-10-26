package com.routeplanner.bank;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.RouteStep;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Route Planner tab in the bank: a game-style square button with the plugin's icon, at the
 * top right of the bank like Quest Helper's. Clicking it shows only the current step's items in
 * the bank grid and puts the step in the bank title; any other tab, "View all items" or a
 * search turns it off, the same as switching between real bank tabs.
 */
@Slf4j
@Singleton
public class BankFilterButton {

    private static final String TAB_NAME = "Route Planner";
    private static final int BANK_GROUP_ID = 12;

    // The game's own small square button, as used by its bank buttons (and Quest Helper's tab)
    private static final int BUTTON_SPRITE = 195;
    private static final int BUTTON_SPRITE_SELECTED = 196;
    private static final int TAB_SOUND = 2266;
    /** Our icon, registered under an id the game doesn't use. */
    private static final int ICON_SPRITE = -21470;

    // Placement: Quest Helper's tab sits at x=408; ours goes just left of it, or there if it's absent.
    private static final int SIZE = 25;
    private static final int QUEST_HELPER_X = 408;
    private static final int Y = 5;

    // The bank grid (same spacing as the game's own layout)
    private static final int COLUMNS = 8;
    private static final int ITEM_SPACING_X = 48;
    private static final int ITEM_SPACING_Y = 36;
    private static final int ITEM_ROW_START = 51;

    @Inject private Client client;
    @Inject private RoutePlannerPlugin plugin;
    @Inject private BankItemManager bankItemManager;
    @Inject private BankSearch bankSearch;
    @Inject private ItemManager itemManager;

    private boolean filterActive;
    private RouteStep filteredStep;
    private Widget background;
    private Widget icon;
    private boolean iconRegistered;
    private BufferedImage iconSource; // the plugin icon the sprite was made from
    /** Each grid widget's x, y and hidden flag before we filtered, to put back exactly. */
    private final Map<Integer, int[]> saved = new HashMap<>();
    private String savedTitle;

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == BANK_GROUP_ID) {
            background = null;
            icon = null;
            filterActive = false;
            filteredStep = null;
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event) {
        if (event.getGroupId() == BANK_GROUP_ID) {
            background = null;
            icon = null;
            filterActive = false;
            filteredStep = null;
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() != ScriptID.BANKMAIN_BUILD) return;
        saved.clear(); // the bank just laid itself out again; that's the new state to come back to
        savedTitle = null;
        createTab();
        if (filterActive) applyItemFilter();
    }

    /** The step changed while the bank is open: show or hide the tab, and drop a stale filter. */
    @Subscribe
    public void onGameTick(GameTick tick) {
        if (background == null) return;
        RouteStep step = stepWithItems();
        boolean show = step != null;
        if (background.isHidden() == show) {
            background.setHidden(!show);
            if (icon != null) icon.setHidden(!show);
        }
        if (filterActive && step != filteredStep) {
            if (step == null) turnOff(true);
            else applyItemFilter();
        }
    }

    /** Another tab, "View all items" or the search box replaces our view, like any bank tab. */
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!filterActive) return;
        String option = event.getMenuOption();
        if (option == null || isOurTab(event)) return;
        if (option.startsWith("View tab") || option.startsWith("View tag tab") || option.equals("View all items")
            || option.startsWith("Potion store") || option.equals("Search")) {
            turnOff(false);
        }
    }

    /** Our own tab's click (matched by widget, since the menu text isn't reliable to compare). */
    private boolean isOurTab(MenuOptionClicked event) {
        if (background != null && event.getParam1() == InterfaceID.Bankmain.UNIVERSE
            && event.getParam0() == background.getIndex()) return true;
        String target = event.getMenuTarget();
        return target != null && Text.removeTags(target).contains(TAB_NAME);
    }

    private RouteStep stepWithItems() {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        return step != null && step.getItemList() != null && !step.getItemList().trim().isEmpty() ? step : null;
    }

    private void createTab() {
        Widget parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
        if (parent == null || parent.isHidden()) return;
        int x = questHelperTabPresent(parent) ? QUEST_HELPER_X - SIZE - 3 : QUEST_HELPER_X;
        registerIcon();
        if (background != null && stillThere(parent)) {
            // Quest Helper may have added its tab after ours: keep to its left
            if (background.getOriginalX() != x) {
                background.setOriginalX(x);
                background.revalidate();
                if (icon != null) { icon.setOriginalX(x + 3); icon.revalidate(); }
            }
            refreshSprites();
            return;
        }
        registerIcon();

        background = parent.createChild(-1, WidgetType.GRAPHIC);
        background.setOriginalWidth(SIZE);
        background.setOriginalHeight(SIZE);
        background.setOriginalX(x);
        background.setOriginalY(Y);
        background.setSpriteId(filterActive ? BUTTON_SPRITE_SELECTED : BUTTON_SPRITE);
        background.setAction(0, "View tab");
        background.setName("<col=ff9040>" + TAB_NAME + "</col>");
        background.setHasListener(true);
        background.setOnOpListener((JavaScriptCallback) e -> toggle());
        background.revalidate();

        icon = parent.createChild(-1, WidgetType.GRAPHIC);
        icon.setOriginalWidth(SIZE - 6);
        icon.setOriginalHeight(SIZE - 6);
        icon.setOriginalX(x + 3);
        icon.setOriginalY(Y + 3);
        icon.setSpriteId(ICON_SPRITE);
        icon.revalidate();

        boolean show = stepWithItems() != null;
        background.setHidden(!show);
        icon.setHidden(!show);
    }

    /** The bank can rebuild its interface and drop children we added; check ours survived. */
    private boolean stillThere(Widget parent) {
        Widget[] kids = parent.getDynamicChildren();
        if (kids == null) return false;
        for (Widget w : kids) {
            if (w != null && w.getName() != null && w.getName().contains(TAB_NAME)) return true;
        }
        return false;
    }

    /** Quest Helper adds a tab named "quest-helper" at the same spot; make room for it. */
    private static boolean questHelperTabPresent(Widget parent) {
        Widget[] kids = parent.getDynamicChildren();
        if (kids == null) return false;
        for (Widget w : kids) {
            if (w != null && w.getName() != null && w.getName().contains("quest-helper")) return true;
        }
        return false;
    }

    /** The plugin's icon as a game sprite, so it sits on the button like the game's own icons. */
    private void registerIcon() {
        BufferedImage img = plugin.getRouteIcon();
        if (img == null) img = ImageUtil.loadImageResource(RoutePlannerPlugin.class, "route_icon.png");
        if (iconRegistered && img == iconSource) return; // still current (re-made once the orb loads)
        iconSource = img;
        try {
            BufferedImage scaled = ImageUtil.resizeImage(img, SIZE - 6, SIZE - 6);
            SpritePixels sprite = ImageUtil.getImageSpritePixels(scaled, client);
            client.getSpriteOverrides().put(ICON_SPRITE, sprite);
            iconRegistered = true;
        } catch (RuntimeException e) {
            log.warn("Route Planner: couldn't load the bank tab icon", e);
        }
    }

    private void refreshSprites() {
        if (background != null) {
            background.setSpriteId(filterActive ? BUTTON_SPRITE_SELECTED : BUTTON_SPRITE);
            background.revalidate();
        }
    }

    private void toggle() {
        log.debug("Route Planner bank tab clicked, filter was {}", filterActive ? "on" : "off");
        client.playSoundEffect(TAB_SOUND);
        if (filterActive) {
            turnOff(true);
        } else {
            filterActive = true;
            refreshSprites();
            applyItemFilter();
        }
    }

    /** @param resetBank put the normal bank view back (not needed when another tab takes over). */
    private void turnOff(boolean resetBank) {
        filterActive = false;
        filteredStep = null;
        refreshSprites();
        restore();
        if (resetBank) bankSearch.reset(true);
    }

    /** Put every grid item and the title back the way the bank had them. */
    private void restore() {
        Widget itemContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (itemContainer != null && !saved.isEmpty()) {
            Widget[] items = itemContainer.getDynamicChildren();
            if (items != null) {
                for (Widget item : items) {
                    if (item == null) continue;
                    int[] s = saved.get(item.getIndex());
                    if (s == null) continue;
                    item.setOriginalX(s[0]);
                    item.setOriginalY(s[1]);
                    item.setHidden(s[2] == 1);
                    item.revalidate();
                }
            }
            itemContainer.revalidateScroll();
        }
        Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
        if (title != null && savedTitle != null) title.setText(savedTitle);
        saved.clear();
        savedTitle = null;
        log.debug("Route Planner bank tab: bank view restored");
    }

    private void applyItemFilter() {
        RouteStep step = stepWithItems();
        if (step == null) return;
        filteredStep = step;

        Set<String> requiredNames = new HashSet<>();
        for (List<String> group : bankItemManager.parseItemList(step.getItemList())) {
            for (String alt : group) requiredNames.add(bankItemManager.parseItemName(alt).toLowerCase().trim());
        }

        Widget itemContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (itemContainer == null) return;
        Widget[] items = itemContainer.getDynamicChildren();
        if (items == null) return;

        if (saved.isEmpty()) {
            for (Widget item : items) {
                if (item == null) continue;
                saved.put(item.getIndex(), new int[]{item.getOriginalX(), item.getOriginalY(), item.isHidden() ? 1 : 0});
            }
            Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
            if (title != null) savedTitle = title.getText();
        }

        // Keep this step's items, in bank order; hide everything else
        List<Widget> keep = new ArrayList<>();
        for (Widget item : items) {
            if (item == null) continue;
            int[] before = saved.get(item.getIndex());
            boolean wasHidden = before != null ? before[2] == 1 : item.isHidden();
            if (item.getItemId() <= 0 || wasHidden) { item.setHidden(true); continue; }
            String name = itemManager.getItemComposition(item.getItemId()).getName();
            boolean wanted = name != null && requiredNames.stream().anyMatch(r -> BankItemManager.nameMatches(r, name));
            if (wanted) keep.add(item);
            else item.setHidden(true);
        }
        for (int i = 0; i < keep.size(); i++) {
            Widget item = keep.get(i);
            item.setHidden(false);
            item.setOriginalX(ITEM_ROW_START + (i % COLUMNS) * ITEM_SPACING_X);
            item.setOriginalY((i / COLUMNS) * ITEM_SPACING_Y);
            item.revalidate();
        }
        int rows = (keep.size() + COLUMNS - 1) / COLUMNS;
        itemContainer.setScrollY(0);
        itemContainer.setScrollHeight(Math.max(itemContainer.getHeight(), rows * ITEM_SPACING_Y));
        itemContainer.revalidateScroll();

        Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
        if (title != null) title.setText("Route: " + step.getName());
    }

    public void reset() {
        if (filterActive) {
            restore();
            bankSearch.reset(true);
        }
        filterActive = false;
        filteredStep = null;
        background = null;
        icon = null;
        if (iconRegistered) {
            client.getSpriteOverrides().remove(ICON_SPRITE);
            iconRegistered = false;
        }
    }
}
