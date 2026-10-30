package com.routeplanner;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class AreaFillTest {

    /** The outline of a 5x5 square (16 tiles), with its centre 3x3 empty. */
    private static Set<WorldPoint> ring() {
        Set<WorldPoint> out = new LinkedHashSet<>();
        for (int x = 0; x < 5; x++) for (int y = 0; y < 5; y++)
            if (x == 0 || y == 0 || x == 4 || y == 4) out.add(p(x, y));
        return out;
    }

    private static WorldPoint p(int x, int y) {
        return new WorldPoint(3200 + x, 3200 + y, 0);
    }

    @Test
    public void closingARingFillsItsMiddle() {
        Set<WorldPoint> painted = ring();
        List<WorldPoint> added = new ArrayList<>(painted);
        Set<WorldPoint> fill = AreaDrawer.enclosedBy(painted, added, 0);
        assertEquals(9, fill.size());
        assertTrue(fill.contains(p(2, 2)));
    }

    @Test
    public void aRingWithAGapLeaksAndStaysEmpty() {
        Set<WorldPoint> painted = ring();
        painted.remove(p(2, 0));
        assertTrue(AreaDrawer.enclosedBy(painted, new ArrayList<>(painted), 0).isEmpty());
    }

    @Test
    public void aDiagonalRingStillCloses() {
        // A diamond: corners touch only diagonally, like a painted circle
        Set<WorldPoint> painted = new LinkedHashSet<>();
        for (int[] c : new int[][]{{2, 0}, {3, 1}, {4, 2}, {3, 3}, {2, 4}, {1, 3}, {0, 2}, {1, 1}}) painted.add(p(c[0], c[1]));
        Set<WorldPoint> fill = AreaDrawer.enclosedBy(painted, new ArrayList<>(painted), 0);
        // The middle and the tile beside it on each side
        assertEquals(5, fill.size());
        assertTrue(fill.contains(p(2, 2)));
        assertTrue(fill.contains(p(2, 1)));
        assertFalse(fill.contains(p(1, 1))); // part of the ring itself
    }

    @Test
    public void anErasedHoleElsewhereIsLeftAlone() {
        // A filled 5x5 block with a hole erased in it, then a stroke added far off to the east
        Set<WorldPoint> painted = new LinkedHashSet<>();
        for (int x = 0; x < 5; x++) for (int y = 0; y < 5; y++) painted.add(p(x, y));
        painted.remove(p(2, 2));
        List<WorldPoint> added = new ArrayList<>();
        for (int x = 8; x < 11; x++) { painted.add(p(x, 0)); added.add(p(x, 0)); }
        assertTrue(AreaDrawer.enclosedBy(painted, added, 0).isEmpty());
    }
}
