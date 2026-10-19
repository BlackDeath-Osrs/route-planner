package com.routeplanner;

import com.routeplanner.sailing.CatchMessages;
import org.junit.Test;

import static org.junit.Assert.*;

public class CatchMessagesTest {

    @Test
    public void readsThePlayersAndACrewmatesCatch() {
        CatchMessages.Catch mine = CatchMessages.parse("You catch three giant krill.");
        assertEquals(3, mine.count);
        assertEquals("Giant krill", mine.fish);
        CatchMessages.Catch crew = CatchMessages.parse("Jobless Jim catches a haddock.");
        assertEquals(1, crew.count);
        assertEquals("Haddock", crew.fish);
        assertEquals(12, CatchMessages.parse("<col=ef1020>You catch 12 yellowfin.</col>").count);
    }

    @Test
    public void ignoresOtherMessages() {
        assertNull(CatchMessages.parse("You catch some shrimps."));
        assertNull(CatchMessages.parse("Your net is full."));
        assertNull(CatchMessages.parse("Trawler's trust: you catch an extra giant krill."));
    }

    @Test
    public void sameFishIgnoresCaseAndPlural() {
        assertTrue(CatchMessages.sameFish("Giant krill", "giant krill"));
        assertTrue(CatchMessages.sameFish("Haddock", "haddocks"));
        assertFalse(CatchMessages.sameFish("Haddock", "Halibut"));
    }
}
