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

    @Test
    public void exactNameNearbyDropsLongerNames() {
        java.util.List<String> t = NpcNames.parse("Shantay");
        java.util.Set<String> none = java.util.Collections.emptySet();
        java.util.Set<String> shantay = java.util.Collections.singleton("shantay");
        assertEquals("shantay", NpcNames.exactTarget(t, "Shantay"));
        assertNull(NpcNames.exactTarget(t, "Shantay Guard"));
        assertTrue(NpcNames.matches(t, "Shantay Guard", none));
        assertFalse(NpcNames.matches(t, "Shantay Guard", shantay));
        assertTrue(NpcNames.matches(t, "Shantay", shantay));
    }
}
