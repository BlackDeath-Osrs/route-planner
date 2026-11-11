package com.routeplanner;

import com.routeplanner.model.ItemMode;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MigrateUseUpTest {

    private static RouteStep step(String name, String items, String npc, ItemMode mode) {
        RouteStep s = new RouteStep(name, StepType.LOCATION, null, -1);
        s.setItemList(items);
        s.setNpcHighlight(npc);
        s.setItemMode(mode);
        return s;
    }

    @Test
    public void oldNotingAndCleaningStepsBecomeUseUp() {
        RouteStep note = step("Note White berries with the Tool Leprechaun", "White berries", "Tool Leprechaun", null);
        note.migrateUseUp();
        assertEquals(ItemMode.USE_UP, note.getItemMode());

        RouteStep clean = step("Clean the grimy herbs at Catherby", "Grimy *", null, ItemMode.BANK);
        clean.migrateUseUp();
        assertEquals(ItemMode.USE_UP, clean.getItemMode());
    }

    @Test
    public void otherItemStepsAreLeftAlone() {
        RouteStep bank = step("Withdraw", "Spade, White berries", null, ItemMode.BANK);
        bank.migrateUseUp();
        assertEquals(ItemMode.BANK, bank.getItemMode());

        RouteStep shop = step("Note things", "Rake", "Tool Leprechaun", ItemMode.SHOP);
        shop.migrateUseUp();
        assertEquals(ItemMode.SHOP, shop.getItemMode());
    }

    @Test
    public void oldPatchStepsLookUpTheirPatch() {
        net.runelite.api.coords.WorldPoint at = new net.runelite.api.coords.WorldPoint(1239, 3728, 0);
        RouteStep herb = new RouteStep("Harvest and replant the herb patch at Farming Guild", StepType.LOCATION, at, -1);
        herb.migrateFarmPatch();
        assertEquals(-1, herb.getFarmPatchId());
        org.junit.Assert.assertTrue(herb.isFarmReplant());

        RouteStep bush = new RouteStep("Tend the bush patch", StepType.LOCATION, at, -1);
        bush.migrateFarmPatch();
        assertEquals(-1, bush.getFarmPatchId());
        org.junit.Assert.assertFalse(bush.isFarmReplant());

        RouteStep tele = new RouteStep("Teleport: Ardy cloak 2+: Farm patch", StepType.LOCATION, at, -1);
        tele.migrateFarmPatch();
        assertEquals(0, tele.getFarmPatchId());
    }
}
