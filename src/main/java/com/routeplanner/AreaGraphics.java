package com.routeplanner;

import com.routeplanner.model.TileArea;
import com.routeplanner.util.Strokes;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.ColorUtil;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/** Draws a step's area: an outline with a faint fill, on the ground and on the minimap. */
final class AreaGraphics {

    /** The minimap's drawn circle in each window layout: fixed, resizable classic, resizable modern. */
    private static final int[] MINIMAPS = {
        InterfaceID.Toplevel.MINIMAP, InterfaceID.ToplevelOsrsStretch.MINIMAP, InterfaceID.ToplevelPreEoc.MINIMAP
    };

    private AreaGraphics() {}

    static Color fill(Color c) {
        return ColorUtil.colorWithAlpha(c, Math.min(c.getAlpha(), 160) / 4);
    }

    static Color outline(Color c) {
        return ColorUtil.colorWithAlpha(c, Math.max(c.getAlpha(), 200));
    }

    /** The area on the ground, where it's in the loaded scene and on the floor being shown. */
    static void drawGround(Graphics2D g, Client client, TileArea area, Color color) {
        WorldView wv = client.getTopLevelWorldView();
        if (wv == null || area.getPlane() != wv.getPlane()) return;
        int plane = wv.getPlane();
        // Only the part inside the scene: an area can be bigger than what's loaded
        int sx0 = wv.getBaseX(), sx1 = wv.getBaseX() + wv.getSizeX();
        int sy0 = wv.getBaseY(), sy1 = wv.getBaseY() + wv.getSizeY();

        g.setColor(fill(color));
        for (int[] r : area.rects()) {
            int x0 = Math.max(r[0], sx0), x1 = Math.min(r[0] + r[2], sx1);
            int y0 = Math.max(r[1], sy0), y1 = Math.min(r[1] + r[3], sy1);
            for (int x = x0; x < x1; x++) {
                for (int y = y0; y < y1; y++) {
                    LocalPoint lp = LocalPoint.fromWorld(wv, x, y);
                    Polygon poly = lp == null ? null : Perspective.getCanvasTilePoly(client, lp);
                    if (poly != null) g.fillPolygon(poly);
                }
            }
        }

        // The outline, a point per tile corner so it follows the ground over hills
        g.setColor(outline(color));
        g.setStroke(Strokes.round(2));
        for (int[] e : area.edges()) {
            int x0 = clamp(e[0], sx0, sx1), x1 = clamp(e[2], sx0, sx1);
            int y0 = clamp(e[1], sy0, sy1), y1 = clamp(e[3], sy0, sy1);
            // A side wholly outside the scene squashes to its border: skip it
            boolean horizontal = e[1] == e[3];
            if (horizontal ? (e[1] < sy0 || e[1] > sy1 || x0 == x1) : (e[0] < sx0 || e[0] > sx1 || y0 == y1)) continue;
            edge(g, client, wv, plane, x0, y0, x1, y1);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** One straight side, from tile corner (x0, y0) to (x1, y1) in world coordinates. */
    private static void edge(Graphics2D g, Client client, WorldView wv, int plane, int x0, int y0, int x1, int y1) {
        Path2D.Float path = new Path2D.Float();
        boolean drawing = false;
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int wx = x0 + Integer.signum(x1 - x0) * i, wy = y0 + Integer.signum(y1 - y0) * i;
            LocalPoint lp = new LocalPoint((wx - wv.getBaseX()) * Perspective.LOCAL_TILE_SIZE,
                (wy - wv.getBaseY()) * Perspective.LOCAL_TILE_SIZE, wv);
            Point p = Perspective.localToCanvas(client, lp.getX(), lp.getY(), Perspective.getTileHeight(client, lp, plane));
            if (p == null) { drawing = false; continue; }
            if (drawing) path.lineTo(p.getX(), p.getY());
            else path.moveTo(p.getX(), p.getY());
            drawing = true;
        }
        g.draw(path);
    }

    /** The area on the minimap, clipped to its circle. */
    static void drawMinimap(Graphics2D g, Client client, TileArea area, Color color) {
        WorldView wv = client.getTopLevelWorldView();
        Widget map = minimap(client);
        if (wv == null || map == null || area.getPlane() != wv.getPlane()) return;

        Path2D.Float fill = new Path2D.Float();
        for (int[] r : area.rects()) {
            if (!addCorners(fill, client, wv, new int[]{r[0], r[0] + r[2], r[0] + r[2], r[0]},
                new int[]{r[1], r[1], r[1] + r[3], r[1] + r[3]})) return;
        }
        Path2D.Float outline = new Path2D.Float();
        for (int[] e : area.edges()) {
            Point a = minimapPoint(client, wv, e[0], e[1]), b = minimapPoint(client, wv, e[2], e[3]);
            if (a == null || b == null) return;
            outline.moveTo(a.getX(), a.getY());
            outline.lineTo(b.getX(), b.getY());
        }

        Rectangle b = map.getBounds();
        Shape oldClip = g.getClip();
        g.clip(new Ellipse2D.Double(b.x, b.y, b.width, b.height));
        g.setColor(fill(color));
        g.fill(fill);
        g.setColor(outline(color));
        g.setStroke(Strokes.round(1));
        g.draw(outline);
        g.setClip(oldClip);
    }

    /** Adds one closed shape through these tile corners; false if any is off the minimap's reach. */
    private static boolean addCorners(Path2D.Float path, Client client, WorldView wv, int[] xs, int[] ys) {
        for (int i = 0; i < xs.length; i++) {
            Point p = minimapPoint(client, wv, xs[i], ys[i]);
            if (p == null) return false;
            if (i == 0) path.moveTo(p.getX(), p.getY());
            else path.lineTo(p.getX(), p.getY());
        }
        path.closePath();
        return true;
    }

    private static Point minimapPoint(Client client, WorldView wv, int x, int y) {
        LocalPoint lp = new LocalPoint((x - wv.getBaseX()) * Perspective.LOCAL_TILE_SIZE,
            (y - wv.getBaseY()) * Perspective.LOCAL_TILE_SIZE, wv);
        return Perspective.localToMinimap(client, lp, Integer.MAX_VALUE);
    }

    private static Widget minimap(Client client) {
        for (int id : MINIMAPS) {
            Widget w = client.getWidget(id);
            if (w != null && !w.isHidden()) return w;
        }
        return null;
    }
}
