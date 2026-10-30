package com.routeplanner.model;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A step's area: any set of tiles on one floor, a plain rectangle or a painted room shape.
 * Stored as the rectangles that make it up ("plane|x,y,w,h;x,y,w,h"), which keeps big areas short.
 */
public final class TileArea {

    private final int plane;
    private final Set<Long> tiles;
    private final List<int[]> rects;      // x, y, w, h: the tiles as rectangles, for drawing fills
    private final List<int[]> edges;      // x0, y0, x1, y1 in tile-corner coordinates: the outline
    private final int minX, minY, maxX, maxY;

    private TileArea(int plane, Set<Long> tiles) {
        this.plane = plane;
        this.tiles = Collections.unmodifiableSet(tiles);
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (long t : tiles) {
            int x = x(t), y = y(t);
            x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
        }
        minX = x0; minY = y0; maxX = x1; maxY = y1;
        rects = toRects(tiles);
        edges = toEdges(tiles);
    }

    /** The tiles given (world x, y), on {@code plane}; null when there are none. */
    public static TileArea of(int plane, Collection<WorldPoint> points) {
        Set<Long> set = new HashSet<>();
        for (WorldPoint p : points) if (p.getPlane() == plane) set.add(key(p.getX(), p.getY()));
        return set.isEmpty() ? null : new TileArea(plane, set);
    }

    public static TileArea of(WorldArea r) {
        Set<Long> set = new HashSet<>();
        for (int x = r.getX(); x < r.getX() + r.getWidth(); x++)
            for (int y = r.getY(); y < r.getY() + r.getHeight(); y++) set.add(key(x, y));
        return new TileArea(r.getPlane(), set);
    }

    /** Reads {@link #encode()}'s text; null when blank or unreadable. */
    public static TileArea decode(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            int bar = s.indexOf('|');
            int plane = Integer.parseInt(s.substring(0, bar));
            Set<Long> set = new HashSet<>();
            for (String r : s.substring(bar + 1).split(";")) {
                if (r.isEmpty()) continue;
                String[] p = r.split(",");
                int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]);
                int w = Integer.parseInt(p[2]), h = Integer.parseInt(p[3]);
                for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) set.add(key(x + i, y + j));
            }
            return set.isEmpty() ? null : new TileArea(plane, set);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public String encode() {
        StringBuilder sb = new StringBuilder().append(plane).append('|');
        for (int[] r : rects) {
            if (sb.charAt(sb.length() - 1) != '|') sb.append(';');
            sb.append(r[0]).append(',').append(r[1]).append(',').append(r[2]).append(',').append(r[3]);
        }
        return sb.toString();
    }

    public int getPlane() { return plane; }
    public int size() { return tiles.size(); }

    public boolean contains(WorldPoint p) {
        return p != null && p.getPlane() == plane && tiles.contains(key(p.getX(), p.getY()));
    }

    public boolean contains(int x, int y) {
        return tiles.contains(key(x, y));
    }

    /** The box around every tile. */
    public WorldArea bounds() {
        return new WorldArea(minX, minY, maxX - minX + 1, maxY - minY + 1, plane);
    }

    /** True when the tiles fill their box exactly (a plain rectangle). */
    public boolean isRectangle() {
        return tiles.size() == (maxX - minX + 1) * (maxY - minY + 1);
    }

    /** The area's tile nearest the middle of its box (a room shaped like an L may not cover its middle). */
    public WorldPoint centre() {
        int cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
        long best = 0;
        int bestDist = Integer.MAX_VALUE;
        for (long t : tiles) {
            int d = Math.abs(x(t) - cx) + Math.abs(y(t) - cy);
            if (d < bestDist || (d == bestDist && t < best)) { bestDist = d; best = t; }
        }
        return new WorldPoint(x(best), y(best), plane);
    }

    /** Every tile, as world points. */
    public List<WorldPoint> points() {
        List<WorldPoint> out = new ArrayList<>(tiles.size());
        for (long t : tiles) out.add(new WorldPoint(x(t), y(t), plane));
        return out;
    }

    /** The tiles as rectangles (x, y, w, h); together they cover the area exactly once. */
    public List<int[]> rects() { return rects; }

    /** The outline as straight segments between tile corners (x0, y0, x1, y1), holes included. */
    public List<int[]> edges() { return edges; }

    // ------------------------------------------------------------------ helpers

    private static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }
    private static int x(long key) { return (int) (key >> 32); }
    private static int y(long key) { return (int) key; }

    /** Runs of tiles along each row, then runs with the same span on following rows stacked into one. */
    private static List<int[]> toRects(Set<Long> tiles) {
        TreeMap<Integer, TreeSet<Integer>> rows = new TreeMap<>();
        for (long t : tiles) rows.computeIfAbsent(y(t), k -> new TreeSet<>()).add(x(t));
        List<int[]> done = new ArrayList<>();
        List<int[]> open = new ArrayList<>();   // rects still growing upward, keyed by their span
        Integer prevY = null;
        for (int y : rows.keySet()) {
            List<int[]> spans = new ArrayList<>();
            Integer start = null, last = null;
            for (int x : rows.get(y)) {
                if (start != null && x == last + 1) { last = x; continue; }
                if (start != null) spans.add(new int[]{start, last});
                start = x; last = x;
            }
            spans.add(new int[]{start, last});

            List<int[]> next = new ArrayList<>();
            boolean adjacent = prevY != null && y == prevY + 1;
            for (int[] s : spans) {
                int[] grown = null;
                if (adjacent) {
                    for (int[] r : open) {
                        if (r[0] == s[0] && r[2] == s[1] - s[0] + 1) { grown = r; break; }
                    }
                }
                if (grown != null) { open.remove(grown); grown[3]++; next.add(grown); }
                else next.add(new int[]{s[0], y, s[1] - s[0] + 1, 1});
            }
            done.addAll(open);
            open = next;
            prevY = y;
        }
        done.addAll(open);
        return Collections.unmodifiableList(done);
    }

    /** Tile sides with no area tile across them, joined into straight runs. */
    private static List<int[]> toEdges(Set<Long> tiles) {
        // Horizontal sides by the y of their line, vertical sides by the x of theirs
        TreeMap<Integer, TreeSet<Integer>> hBelow = new TreeMap<>(), hAbove = new TreeMap<>();
        TreeMap<Integer, TreeSet<Integer>> vLeft = new TreeMap<>(), vRight = new TreeMap<>();
        for (long t : tiles) {
            int x = x(t), y = y(t);
            if (!tiles.contains(key(x, y - 1))) hBelow.computeIfAbsent(y, k -> new TreeSet<>()).add(x);
            if (!tiles.contains(key(x, y + 1))) hAbove.computeIfAbsent(y + 1, k -> new TreeSet<>()).add(x);
            if (!tiles.contains(key(x - 1, y))) vLeft.computeIfAbsent(x, k -> new TreeSet<>()).add(y);
            if (!tiles.contains(key(x + 1, y))) vRight.computeIfAbsent(x + 1, k -> new TreeSet<>()).add(y);
        }
        List<int[]> out = new ArrayList<>();
        runs(hBelow, out, true);
        runs(hAbove, out, true);
        runs(vLeft, out, false);
        runs(vRight, out, false);
        return Collections.unmodifiableList(out);
    }

    private static void runs(TreeMap<Integer, TreeSet<Integer>> lines, List<int[]> out, boolean horizontal) {
        for (int line : lines.keySet()) {
            Integer start = null, last = null;
            for (int v : lines.get(line)) {
                if (start != null && v == last + 1) { last = v; continue; }
                if (start != null) out.add(segment(line, start, last + 1, horizontal));
                start = v; last = v;
            }
            out.add(segment(line, start, last + 1, horizontal));
        }
    }

    private static int[] segment(int line, int from, int to, boolean horizontal) {
        return horizontal ? new int[]{from, line, to, line} : new int[]{line, from, line, to};
    }
}
