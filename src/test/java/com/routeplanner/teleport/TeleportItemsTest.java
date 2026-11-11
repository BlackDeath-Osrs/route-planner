package com.routeplanner.teleport;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class TeleportItemsTest {

    @Test
    public void dataAddsTheMissingTeleports() {
        assertTrue(TeleportItems.ALL.size() > 200);
        assertNotNull(find("Xeric's talisman"));
        assertNotNull(find("Karamja gloves"));
        assertNotNull(find("Pendant of ates"));
        assertNotNull(find("Varrock tablet"));
    }

    @Test
    public void namesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (TeleportItem it : TeleportItems.ALL) {
            assertTrue("duplicate " + it.getDisplayName(), seen.add(it.getDisplayName()));
        }
    }

    @Test
    public void handMadeNamesStayAndAreNotDuplicated() {
        assertNotNull(TeleportItems.getByDisplayName("Glory: Edgeville"));
        long edgeville = TeleportItems.ALL.stream()
            .filter(t -> t.matchesItemId(11978) && t.getDestination().distanceTo2D(new net.runelite.api.coords.WorldPoint(3087, 3496, 0)) <= 10)
            .count();
        assertEquals(1, edgeville);
    }

    @Test
    public void houseTeleportsUseTheHouseMarker() {
        TeleportItem tab = TeleportItems.getByDisplayName("House tab: House");
        assertTrue(TeleportItems.isHouse(tab.getDestination()));
        assertEquals(0, TeleportItems.ALL.stream().filter(t -> t.getDisplayName().equals("Teleport to House tablet")).count());
    }

    @Test
    public void itemIds() {
        assertArrayEquals(new int[]{13121, 13122}, TeleportItems.itemIds("13121=1||13122=1"));
    }

    private static TeleportItem find(String prefix) {
        return TeleportItems.ALL.stream().filter(t -> t.getDisplayName().startsWith(prefix)).findFirst().orElse(null);
    }
}
