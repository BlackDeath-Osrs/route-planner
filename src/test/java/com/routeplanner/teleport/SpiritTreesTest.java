package com.routeplanner.teleport;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpiritTreesTest {

    @Test
    public void destinationsLoad() {
        assertTrue(SpiritTrees.destinations().contains("Grand Exchange"));
        assertTrue(SpiritTrees.destinations().contains("Tree Gnome Village"));
        assertEquals(new WorldPoint(3185, 3508, 0), SpiritTrees.destination("grand exchange"));
        assertTrue(TeleportItems.isHouse(SpiritTrees.destination("Your house")));
        assertNull(SpiritTrees.destination("Varrock"));
    }

    @Test
    public void menuNamesLoseTheirKey() {
        assertEquals("Grand Exchange", SpiritTrees.menuName("4: Grand Exchange"));
        assertEquals("Hosidius", SpiritTrees.menuName("A: Hosidius"));
    }

    @Test
    public void destinationFromLanding() {
        assertEquals("Grand Exchange", SpiritTrees.destinationAt(new WorldPoint(3184, 3510, 0)));
        assertNull(SpiritTrees.destinationAt(new WorldPoint(3222, 3218, 0)));
    }

    @Test
    public void nearestTreeSkipsPlantedOnes() {
        // Beside the Grand Exchange tree
        SpiritTrees.Tree ge = SpiritTrees.nearest(new WorldPoint(3180, 3505, 0));
        assertNotNull(ge);
        assertFalse(ge.planted);
        assertTrue(ge.tileNearest(new WorldPoint(3180, 3505, 0)).distanceTo2D(new WorldPoint(3185, 3508, 0)) <= 6);
        // Next to the Farming Guild's planted tree: a public tree is picked instead
        SpiritTrees.Tree guild = SpiritTrees.nearest(new WorldPoint(1253, 3750, 0));
        assertNotNull(guild);
        assertFalse(guild.planted);
    }

    @Test
    public void treeNames() {
        assertTrue(SpiritTrees.isTreeName("spirit tree"));
        assertTrue(SpiritTrees.isTreeName("spiritual fairy tree"));
        assertFalse(SpiritTrees.isTreeName("fairy ring"));
    }

    @Test
    public void grownPlantedTreesCount() {
        java.util.Set<String> grown = new java.util.HashSet<>();
        for (SpiritTrees.Tree t : SpiritTrees.plantedTrees()) grown.add(t.key());
        assertEquals(5, SpiritTrees.plantedTrees().size());
        // By the Farming Guild's patch, with a tree grown there: that one
        SpiritTrees.Tree guild = SpiritTrees.nearest(new WorldPoint(1253, 3750, 0), grown);
        assertTrue(guild.planted);
    }
}
