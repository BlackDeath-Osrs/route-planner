package com.routeplanner;

import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import com.routeplanner.model.TileArea;
import com.routeplanner.skilling.RecipeLabels;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class RouteStepTest {

    private static RouteStep step() {
        return new RouteStep("Step", StepType.LOCATION, null, -1);
    }

    @Test
    public void bankAndInventoryItemsCountAsHighlights() {
        assertFalse(step().hasHighlight());

        RouteStep bank = step();
        bank.setBankHighlightItems("Raw shrimps");
        assertTrue(bank.hasHighlight());

        RouteStep potion = step();
        potion.setHerblorePotion("Attack potion");
        assertTrue(potion.hasHighlight());

        RouteStep inventory = step();
        inventory.setInventoryHighlightItems("Cocktail shaker, Lemon");
        assertTrue(inventory.hasHighlight());

        RouteStep blank = step();
        blank.setInventoryHighlightItems("   ");
        assertFalse(blank.hasHighlight());
    }

    @Test
    public void aCookingRecipeHighlightsItsRawFood() {
        String[] labels = RecipeLabels.labelsFor("Cooking");
        assertEquals("(none)", labels[0]);
        RouteStep s = step();
        RecipeLabels.apply(s, "Cooking", labels[1]);
        assertNotNull(s.getBankHighlightItems());
        assertEquals(labels[1], s.getRecipeLabel());
        assertTrue(s.hasHighlight());

        RecipeLabels.apply(s, "Cooking", "(none)");
        assertNull(s.getBankHighlightItems());
        assertNull(s.getRecipeLabel());
    }

    @Test
    public void areaCoversBothCornersInAnyOrder() {
        RouteStep s = step();
        assertFalse(s.hasArea());
        assertNull(s.area());

        // Drawn from the north-east corner to the south-west one
        s.setAreaFrom(new WorldPoint(3245, 3235, 0));
        s.setAreaTo(new WorldPoint(3200, 3200, 0));
        assertTrue(s.area().isRectangle());
        WorldArea area = s.area().bounds();
        assertEquals(3200, area.getX());
        assertEquals(3200, area.getY());
        assertEquals(46, area.getWidth());
        assertEquals(36, area.getHeight());
        assertTrue(s.area().contains(new WorldPoint(3200, 3200, 0)));
        assertTrue(s.area().contains(new WorldPoint(3245, 3235, 0)));
        assertFalse(s.area().contains(new WorldPoint(3246, 3220, 0)));
        assertFalse(s.area().contains(new WorldPoint(3220, 3220, 1))); // upstairs isn't in it
    }

    @Test
    public void oneTileAreaIsJustThatTile() {
        WorldArea a = AreaDrawer.between(new WorldPoint(3222, 3218, 0), new WorldPoint(3222, 3218, 0));
        assertEquals(1, a.getWidth());
        assertEquals(1, a.getHeight());
        assertTrue(a.contains(new WorldPoint(3222, 3218, 0)));
    }

    /** An L-shaped room: a 4x2 floor with a 2x2 wing on its west end. */
    private static TileArea lRoom() {
        List<WorldPoint> tiles = new ArrayList<>();
        for (int x = 0; x < 4; x++) for (int y = 0; y < 2; y++) tiles.add(new WorldPoint(3200 + x, 3200 + y, 0));
        for (int x = 0; x < 2; x++) for (int y = 2; y < 4; y++) tiles.add(new WorldPoint(3200 + x, 3200 + y, 0));
        return TileArea.of(0, tiles);
    }

    @Test
    public void paintedAreaKeepsItsShape() {
        TileArea l = lRoom();
        assertEquals(12, l.size());
        assertFalse(l.isRectangle());
        assertTrue(l.contains(new WorldPoint(3201, 3203, 0)));
        assertFalse(l.contains(new WorldPoint(3203, 3203, 0))); // the missing corner of the L
        // Its rectangles cover each tile once
        int covered = 0;
        for (int[] r : l.rects()) covered += r[2] * r[3];
        assertEquals(12, covered);
        // An L has six straight sides
        assertEquals(6, l.edges().size());
        assertTrue(l.contains(l.centre()));
    }

    @Test
    public void holeGetsItsOwnOutline() {
        List<WorldPoint> tiles = new ArrayList<>();
        for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) if (x != 1 || y != 1) tiles.add(new WorldPoint(3200 + x, 3200 + y, 0));
        TileArea ring = TileArea.of(0, tiles);
        assertFalse(ring.contains(new WorldPoint(3201, 3201, 0)));
        assertEquals(8, ring.edges().size()); // 4 outside, 4 around the hole
    }

    @Test
    public void paintedAreaSurvivesSaving() {
        RouteStep s = step();
        s.setArea(lRoom());
        assertNotNull(s.getAreaTiles());
        // Older versions read the corners: they get the box around the L
        assertEquals(new WorldPoint(3200, 3200, 0), s.getAreaFrom());
        assertEquals(new WorldPoint(3203, 3203, 0), s.getAreaTo());

        TileArea back = TileArea.decode(s.getAreaTiles());
        assertEquals(new HashSet<>(lRoom().points()), new HashSet<>(back.points()));
        assertEquals(12, s.area().size());

        // A plain rectangle is kept as just its corners
        s.setArea(TileArea.of(new WorldArea(3200, 3200, 3, 2, 1)));
        assertNull(s.getAreaTiles());
        assertEquals(6, s.area().size());
        assertEquals(1, s.area().getPlane());

        s.setArea(null);
        assertFalse(s.hasArea());
        assertNull(TileArea.decode("garbage"));
    }
}
