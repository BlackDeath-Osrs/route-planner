package com.routeplanner;

import com.routeplanner.util.NpcNames;
import org.junit.Test;

import static org.junit.Assert.*;

public class NpcNamesTest {

    @Test
    public void matchesAnyListedNpcOrAlternative() {
        String target = "Rommik, Shop assistant/Shop keeper";
        assertTrue(NpcNames.matches(target, "Rommik"));
        assertTrue(NpcNames.matches(target, "shop keeper"));
        assertTrue(NpcNames.matches(target, "Shop assistant"));
        assertFalse(NpcNames.matches(target, "Banker"));
    }

    @Test
    public void matchesNamesThatStartWithTheTarget() {
        assertTrue(NpcNames.matches("Guard", "Guard captain"));
        assertTrue(NpcNames.matches("Goblin", "Goblin(2)"));
        assertFalse(NpcNames.matches("Man", "Mankind"));
        assertFalse(NpcNames.matches("Guard", null));
        assertFalse(NpcNames.matches("", "Guard"));
    }
}
