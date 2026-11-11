package com.routeplanner.teleport;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.*;

public class QuetzalsTest {

    @Test
    public void sitesLoad() {
        assertEquals(14, Quetzals.sites().size());
        assertEquals(new WorldPoint(1389, 2901, 0), Quetzals.destination("aldarin"));
        assertEquals("Civitas illa Fortis", Quetzals.site("civitas illa fortis"));
        assertNull(Quetzals.destination("Varrock"));
    }

    @Test
    public void siteFromLanding() {
        assertEquals("Hunter Guild", Quetzals.siteAt(new WorldPoint(1587, 3051, 0)));
        assertNull(Quetzals.siteAt(new WorldPoint(3222, 3218, 0)));
    }

    @Test
    public void nearestRenu() {
        assertEquals(new WorldPoint(1697, 3140, 0), Quetzals.nearestRenu(new WorldPoint(1690, 3135, 0)));
    }

    @Test
    public void whistles() {
        assertTrue(Quetzals.isWhistle(29271));
        assertTrue(Quetzals.isWhistle(33120));
        assertFalse(Quetzals.isWhistle(772));
    }
}
