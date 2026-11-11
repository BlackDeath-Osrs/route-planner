package com.routeplanner.teleport;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.*;

public class FairyRingsTest {

    @Test
    public void codes() {
        assertEquals("CKR", FairyRings.normalize("c k r"));
        assertEquals("CKR", FairyRings.normalize("CKR"));
        assertEquals("ZANARIS", FairyRings.normalize("Zanaris"));
        assertNull(FairyRings.normalize("CKA"));   // third dial is P/Q/R/S
        assertNull(FairyRings.normalize("IKR"));   // first dial is A-D
        assertNull(FairyRings.normalize(null));
        assertEquals("C K R", FairyRings.spaced("ckr"));
    }

    @Test
    public void dataLoads() {
        assertTrue(FairyRings.codes().size() > 40);
        assertTrue(FairyRings.rings().size() > 40);
        assertEquals(new WorldPoint(2801, 3003, 0), FairyRings.destination("CKR"));
        assertNotNull(FairyRings.destination("ZANARIS"));
        assertNull(FairyRings.destination("AAA"));
        // Every ring has its object id
        for (FairyRings.Ring r : FairyRings.rings()) assertTrue(r.objectId > 0);
    }

    @Test
    public void nearestRing() {
        // Just north of the Wizards' Tower: the ring south of it (DIS's own ring)
        FairyRings.Ring r = FairyRings.nearest(new WorldPoint(3110, 3165, 0));
        assertNotNull(r);
        assertEquals(new WorldPoint(3108, 3149, 0), r.tile);
    }

    @Test
    public void menuOptions() {
        assertEquals("CKR", FairyRings.lastDestinationCode("Last-destination (CKR)"));
        assertEquals("BIP", FairyRings.lastDestinationCode("last-destination (bip)"));
        assertNull(FairyRings.lastDestinationCode("Configure"));
        assertTrue(FairyRings.isRingName("fairy ring"));
        assertTrue(FairyRings.isRingName("spiritual fairy tree"));
        assertFalse(FairyRings.isRingName("spirit tree"));
    }

    @org.junit.Test
    public void travelLogEntries() {
        org.junit.Assert.assertEquals(net.runelite.api.gameval.InterfaceID.FairyringsLog.AIP, FairyRings.logEntry("AIP"));
        org.junit.Assert.assertEquals(net.runelite.api.gameval.InterfaceID.FairyringsLog.CKR, FairyRings.logEntry("C K R"));
        org.junit.Assert.assertEquals(net.runelite.api.gameval.InterfaceID.FairyringsLog.BLS, FairyRings.logEntry("bls"));
        org.junit.Assert.assertEquals(net.runelite.api.gameval.InterfaceID.FairyringsLog.DLS, FairyRings.logEntry("DLS"));
        org.junit.Assert.assertEquals(-1, FairyRings.logEntry("Zanaris"));
    }

    @org.junit.Test
    public void codeFromLanding() {
        org.junit.Assert.assertEquals("DJR", FairyRings.codeLandingAt(new net.runelite.api.coords.WorldPoint(1455, 3658, 0)));
        org.junit.Assert.assertEquals("CKR", FairyRings.codeLandingAt(new net.runelite.api.coords.WorldPoint(2803, 3004, 0)));
        org.junit.Assert.assertNull(FairyRings.codeLandingAt(new net.runelite.api.coords.WorldPoint(3222, 3218, 0)));
    }
}
