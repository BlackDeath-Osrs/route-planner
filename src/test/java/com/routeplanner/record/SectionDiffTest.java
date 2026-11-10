package com.routeplanner.record;

import com.routeplanner.model.ItemMode;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SectionDiffTest {

    private static RouteStep talk(String name, String npc, int x, int y) {
        RouteStep s = new RouteStep(name, StepType.LOCATION, x < 0 ? null : new WorldPoint(x, y, 0), -1);
        s.setSkillingTargetNpc(npc);
        return s;
    }

    private static RouteStep bank(String name, String items) {
        RouteStep s = new RouteStep(name, StepType.LOCATION, new WorldPoint(3092, 3245, 0), -1);
        s.setItemMode(ItemMode.BANK);
        s.setItemList(items);
        return s;
    }

    @Test
    public void newStepGoesBetweenItsNeighboursAndMissingInfoIsFilled() {
        RouteStep withdraw = bank("Withdraw", "3 Ball of wool, Coins");
        RouteStep ned = talk("Ned: a wig", "Ned", -1, 0); // no tile, no dialog yet
        RouteStep aggie = talk("Aggie: dyes", "Aggie", 3086, 3258);
        aggie.setDialogOptions("1,3");
        RouteStep cheese = new RouteStep("Take the cheese", StepType.LOCATION, new WorldPoint(3086, 3258, 0), -1);
        cheese.setItemMode(ItemMode.PICKUP);
        cheese.setItemList("Cheese");
        List<RouteStep> section = Arrays.asList(withdraw, ned, aggie, cheese);

        RouteStep recWithdraw = bank("Bank", "3 Ball of wool, Coins");
        RouteStep recLeela = talk("Talk to Leela", "Leela", 3113, 3262);
        RouteStep recNed = talk("Talk to Ned", "Ned", 3100, 3258);
        recNed.setDialogOptions("1,2");
        RouteStep recAggie = talk("Talk to Aggie", "Aggie", 3087, 3259);
        recAggie.setDialogOptions("1,2,3");
        List<RouteStep> recorded = Arrays.asList(recWithdraw, recLeela, recNed, recAggie);

        Map<RouteStep, Integer> ticked = new HashMap<>();
        ticked.put(withdraw, 1);

        List<SectionDiff.Row> rows = SectionDiff.compare(section, recorded, ticked);
        assertEquals(5, rows.size());
        assertEquals(SectionDiff.Kind.SAME, rows.get(0).kind);
        assertEquals(SectionDiff.Kind.NEW, rows.get(1).kind);
        assertSame(recLeela, rows.get(1).recorded);

        SectionDiff.Row nedRow = rows.get(2);
        assertEquals(SectionDiff.Kind.CHANGED, nedRow.kind);
        assertSame(ned, nedRow.step);
        assertEquals(2, nedRow.changes.size()); // tile + dialog
        assertTrue(nedRow.changes.stream().allMatch(c -> c.fill));

        SectionDiff.Row aggieRow = rows.get(3);
        assertEquals(SectionDiff.Kind.CHANGED, aggieRow.kind);
        assertEquals(1, aggieRow.changes.size()); // dialog differs; tile is within 3 tiles
        assertFalse(aggieRow.changes.get(0).fill);

        assertEquals(SectionDiff.Kind.UNSEEN, rows.get(4).kind);
        assertSame(cheese, rows.get(4).step);

        nedRow.changes.forEach(c -> c.applyTo(ned, recNed));
        assertEquals(new WorldPoint(3100, 3258, 0), ned.getWorldPoint());
        assertEquals("1,2", ned.getDialogOptions());
        assertEquals("Ned", ned.getSkillingTargetNpc());
    }

    @Test
    public void stepTickedWithNothingRecordedIsUnchangedNotUnseen() {
        RouteStep fletch = new RouteStep("Fletch", "FLETCHING", com.routeplanner.model.SkillGoalType.LEVEL, 10, 0);
        List<SectionDiff.Row> rows = SectionDiff.compare(Collections.singletonList(fletch), Collections.emptyList(),
            Collections.singletonMap(fletch, 0));
        assertEquals(SectionDiff.Kind.SAME, rows.get(0).kind);
    }

    @Test
    public void farApartStepsWithNothingInCommonAreNotPaired() {
        RouteStep a = talk("Talk to Ned", "Ned", 3100, 3258);
        RouteStep b = talk("Talk to Hans", "Hans", 3221, 3219);
        List<SectionDiff.Row> rows = SectionDiff.compare(Collections.singletonList(a), Collections.singletonList(b),
            Collections.emptyMap());
        assertEquals(2, rows.size());
        assertEquals(SectionDiff.Kind.UNSEEN, rows.get(0).kind);
        assertEquals(SectionDiff.Kind.NEW, rows.get(1).kind);
    }

    @Test
    public void quantitiesDontCountAsDifferentItems() {
        assertEquals(SectionDiff.itemNames("3 Ball of wool, coins"), SectionDiff.itemNames("Ball of wool, 2 Coins"));
    }
}
