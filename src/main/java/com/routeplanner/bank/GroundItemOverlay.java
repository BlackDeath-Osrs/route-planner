package com.routeplanner.bank;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.ItemMode;
import com.routeplanner.model.RouteStep;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemLayer;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Highlights matching ground items on tiles for an ITEM step in PICKUP mode.
 */
@Slf4j
@Singleton
public class GroundItemOverlay extends Overlay {

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final BankItemManager bankItemManager;
    private final ItemManager itemManager;

    @Inject
    public GroundItemOverlay(Client client, RoutePlannerPlugin plugin,
                             BankItemManager bankItemManager, ItemManager itemManager) {
        this.client = client;
        this.plugin = plugin;
        this.bankItemManager = bankItemManager;
        this.itemManager = itemManager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    // The step's item list, parsed once rather than every frame
    private String targetsFor;
    private Set<String> targets = Collections.emptySet();

    /** Target item names (lowercased) from the step's item list, "/" alternatives included. */
    private Set<String> targets(String itemList) {
        if (Objects.equals(itemList, targetsFor)) return targets;
        Set<String> names = new HashSet<>();
        for (List<String> group : bankItemManager.parseItemList(itemList)) {
            for (String alt : group) {
                String n = bankItemManager.parseItemName(alt);
                if (n != null) names.add(n.toLowerCase());
            }
        }
        targetsFor = itemList;
        targets = names;
        return names;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getActiveRoute() == null) return null;
        RouteStep step = plugin.getActiveRoute().getActiveStep();
        if (step == null || !step.hasItems()) return null;
        if (step.getItemMode() != ItemMode.PICKUP) return null;

        Set<String> targets = targets(step.getItemList());
        if (targets.isEmpty()) return null;

        Scene scene = client.getScene();
        if (scene == null) return null;
        Tile[][][] tiles = scene.getTiles();
        int z = client.getPlane();
        if (z < 0 || z >= tiles.length) return null;
        Tile[][] plane = tiles[z];

        for (Tile[] row : plane) {
            if (row == null) continue;
            for (Tile tile : row) {
                if (tile == null) continue;
                List<TileItem> groundItems = tile.getGroundItems();
                if (groundItems == null || groundItems.isEmpty()) continue;

                String matchName = null;
                for (TileItem gi : groundItems) {
                    if (gi == null) continue;
                    String name = itemManager.getItemComposition(gi.getId()).getName();
                    if (name != null && targets.stream().anyMatch(t -> BankItemManager.nameMatches(t, name))) {
                        matchName = name;
                        break;
                    }
                }
                if (matchName == null) continue;

                LocalPoint lp = tile.getLocalLocation();
                if (lp == null) continue;
                if (!plugin.highlightAllowed(step, tile.getWorldLocation())) continue;

                ItemLayer itemLayer = tile.getItemLayer();
                if (itemLayer != null) {
                    Shape clickbox = itemLayer.getClickbox();
                    if (clickbox != null) {
                        graphics.setColor(new Color(255, 140, 0, 70));
                        graphics.fill(clickbox);
                        graphics.setColor(new Color(255, 140, 0, 230));
                        graphics.setStroke(new BasicStroke(2));
                        graphics.draw(clickbox);
                    }
                }

                Point textLoc =
                    Perspective.getCanvasTextLocation(client, graphics, lp, matchName, 0);
                if (textLoc != null) {
                    graphics.setColor(Color.BLACK);
                    graphics.drawString(matchName, textLoc.getX() + 1, textLoc.getY() + 1);
                    graphics.setColor(new Color(255, 200, 0));
                    graphics.drawString(matchName, textLoc.getX(), textLoc.getY());
                }
            }
        }
        return null;
    }

}
