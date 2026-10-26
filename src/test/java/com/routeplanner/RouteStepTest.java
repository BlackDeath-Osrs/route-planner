package com.routeplanner;

import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import com.routeplanner.skilling.RecipeLabels;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

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
        WorldArea area = s.area();
        assertEquals(3200, area.getX());
        assertEquals(3200, area.getY());
        assertEquals(46, area.getWidth());
        assertEquals(36, area.getHeight());
        assertTrue(area.contains(new WorldPoint(3200, 3200, 0)));
        assertTrue(area.contains(new WorldPoint(3245, 3235, 0)));
        assertFalse(area.contains(new WorldPoint(3246, 3220, 0)));
        assertFalse(area.contains(new WorldPoint(3220, 3220, 1))); // upstairs isn't in it
    }

    @Test
    public void oneTileAreaIsJustThatTile() {
        WorldArea a = AreaDrawer.between(new WorldPoint(3222, 3218, 0), new WorldPoint(3222, 3218, 0));
        assertEquals(1, a.getWidth());
        assertEquals(1, a.getHeight());
        assertTrue(a.contains(new WorldPoint(3222, 3218, 0)));
    }
}
