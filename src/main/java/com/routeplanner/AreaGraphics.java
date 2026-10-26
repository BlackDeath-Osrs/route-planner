package com.routeplanner;

import com.routeplanner.util.Strokes;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
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
    static void drawGround(Graphics2D g, Client client, WorldArea area, Color color) {
        WorldView wv = client.getTopLevelWorldView();
        if (wv == null || area.getPlane() != wv.getPlane()) return;
        int plane = wv.getPlane();
        // Only the part inside the scene: an area can be bigger than what's loaded
        int x0 = Math.max(area.getX(), wv.getBaseX()), x1 = Math.min(area.getX() + area.getWidth(), wv.getBaseX() + wv.getSizeX());
        int y0 = Math.max(area.getY(), wv.getBaseY()), y1 = Math.min(area.getY() + area.getHeight(), wv.getBaseY() + wv.getSizeY());
        if (x0 >= x1 || y0 >= y1) return;

        g.setColor(fill(color));
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                LocalPoint lp = LocalPoint.fromWorld(wv, x, y);
                Polygon poly = lp == null ? null : Perspective.getCanvasTilePoly(client, lp);
                if (poly != null) g.fillPolygon(poly);
            }
        }

        // The edge, a point per tile corner so it follows the ground over hills
        g.setColor(outline(color));
        g.setStroke(Strokes.round(2));
        int ax0 = area.getX(), ax1 = area.getX() + area.getWidth();
        int ay0 = area.getY(), ay1 = area.getY() + area.getHeight();
        if (ay0 >= y0) edge(g, client, wv, plane, x0, ay0, x1, ay0);
        if (ay1 <= y1) edge(g, client, wv, plane, x0, ay1, x1, ay1);
        if (ax0 >= x0) edge(g, client, wv, plane, ax0, y0, ax0, y1);
        if (ax1 <= x1) edge(g, client, wv, plane, ax1, y0, ax1, y1);
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
    static void drawMinimap(Graphics2D g, Client client, WorldArea area, Color color) {
        WorldView wv = client.getTopLevelWorldView();
        Widget map = minimap(client);
        if (wv == null || map == null || area.getPlane() != wv.getPlane()) return;

        int[] xs = {area.getX(), area.getX() + area.getWidth(), area.getX() + area.getWidth(), area.getX()};
        int[] ys = {area.getY(), area.getY(), area.getY() + area.getHeight(), area.getY() + area.getHeight()};
        Polygon poly = new Polygon();
        for (int i = 0; i < 4; i++) {
            LocalPoint lp = new LocalPoint((xs[i] - wv.getBaseX()) * Perspective.LOCAL_TILE_SIZE,
                (ys[i] - wv.getBaseY()) * Perspective.LOCAL_TILE_SIZE, wv);
            Point p = Perspective.localToMinimap(client, lp, Integer.MAX_VALUE);
            if (p == null) return;
            poly.addPoint(p.getX(), p.getY());
        }

        Rectangle b = map.getBounds();
        Shape oldClip = g.getClip();
        g.clip(new Ellipse2D.Double(b.x, b.y, b.width, b.height));
        g.setColor(fill(color));
        g.fill(poly);
        g.setColor(outline(color));
        g.setStroke(Strokes.round(1));
        g.draw(poly);
        g.setClip(oldClip);
    }

    private static Widget minimap(Client client) {
        for (int id : MINIMAPS) {
            Widget w = client.getWidget(id);
            if (w != null && !w.isHidden()) return w;
        }
        return null;
    }
}
