package com.routeplanner.teleport;

import com.routeplanner.model.RouteStep;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Reading the Portal Nexus teleport menu. Each row is a clickable background with its text
 * drawn by a separate widget on the same line, so rows are matched up by position rather than
 * by relying on the menu's internal layout.
 */
public final class PortalNexus {

    private PortalNexus() {}

    public static final int MENU_GROUP = InterfaceID.TelenexusTeleport.UNIVERSE >>> 16;

    private static final String NEXUS_NAME = "portal nexus";

    /**
     * For a step that leaves the house by the nexus or a portal-chamber portal, a test for that
     * object's (lowercase) name; null for any other step.
     */
    public static Predicate<String> houseObjectName(RouteStep step) {
        if (step == null) return null;
        String dest = step.getTeleportNexus();
        if ("NEXUS".equals(step.getTeleportMethod())) return name -> name.startsWith(NEXUS_NAME);
        if ("PORTAL".equals(step.getTeleportMethod()) && dest != null && !dest.trim().isEmpty()) {
            String portal = dest.trim().toLowerCase() + " portal";
            return portal::equals;
        }
        return null;
    }

    /** Game objects on the player's floor whose (lowercase) name matches; built furniture by its impostor. */
    public static List<GameObject> findObjects(Client client, Predicate<String> nameMatches) {
        List<GameObject> found = new ArrayList<>();
        Scene scene = client.getScene();
        if (scene == null) return found;
        Tile[][][] tiles = scene.getTiles();
        int z = client.getPlane();
        if (z < 0 || z >= tiles.length) return found;
        for (Tile[] row : tiles[z]) {
            if (row == null) continue;
            for (Tile tile : row) {
                if (tile == null) continue;
                GameObject[] objects = tile.getGameObjects();
                if (objects == null) continue;
                for (GameObject obj : objects) {
                    if (obj == null) continue;
                    // GameObjects span multiple tiles; only count them from the anchor tile
                    if (!obj.getSceneMinLocation().equals(tile.getSceneLocation())) continue;
                    ObjectComposition comp = client.getObjectDefinition(obj.getId());
                    if (comp == null) continue;
                    // Built furniture often uses an impostor (varbit-driven) definition.
                    if (comp.getImpostorIds() != null) {
                        ObjectComposition imp = comp.getImpostor();
                        if (imp != null) comp = imp;
                    }
                    String name = comp.getName();
                    if (name != null && nameMatches.test(name.toLowerCase())) found.add(obj);
                }
            }
        }
        return found;
    }

    /** Menu text looks like "<col=..>1</col> : Varrock"; keep just the destination name. */
    public static String cleanText(String raw) {
        if (raw == null) return null;
        String t = Text.removeTags(raw).trim().replaceFirst("^\\w\\s*[:)]\\s*", "").trim();
        return t.isEmpty() ? null : t;
    }

    /** The destination named on the same line as a clicked menu row, or null. */
    public static String rowText(Client client, Widget clicked) {
        if (clicked == null) return null;
        String own = cleanText(clicked.getText());
        if (own != null) return own;
        Rectangle row = clicked.getBounds();
        if (row == null || row.height <= 0) return null;
        String[] best = {null};
        forEachText(client, w -> {
            Rectangle b = w.getBounds();
            int centerY = b.y + b.height / 2;
            if (centerY < row.y || centerY > row.y + row.height) return;
            String t = cleanText(w.getText());
            // A row can also show its hotkey on its own; the longest text is the name.
            if (t != null && (best[0] == null || t.length() > best[0].length())) best[0] = t;
        });
        return best[0];
    }

    /** Every visible widget with text in the open nexus menu (nothing if it's closed). */
    public static void forEachText(Client client, Consumer<Widget> action) {
        Widget root = client.getWidget(InterfaceID.TelenexusTeleport.UNIVERSE);
        if (root != null && !root.isHidden()) visit(root, action);
    }

    private static void visit(Widget w, Consumer<Widget> action) {
        if (w == null || w.isHidden()) return;
        String text = w.getText();
        if (text != null && !text.isEmpty()) action.accept(w);
        for (Widget[] kids : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()}) {
            if (kids == null) continue;
            for (Widget k : kids) visit(k, action);
        }
    }
}
