package com.routeplanner;

import com.routeplanner.sailing.ShoalRoutes;
import org.junit.Test;

import static org.junit.Assert.*;

public class ShoalRoutesTest {

    @Test
    public void everyBundledShoalHasARouteAndStops() {
        for (int id = 14; id <= 29; id++) {
            ShoalRoutes.Route route = ShoalRoutes.get(id);
            assertNotNull("shoal " + id, route);
            assertTrue("shoal " + id + " path", route.path.size() > 10);
            assertFalse("shoal " + id + " stops", route.stops.isEmpty());
        }
        assertNull(ShoalRoutes.get(1));
    }

    @Test
    public void everyShoalHasAStopTime() {
        assertEquals("marlin", 50, ShoalRoutes.stopTicks(ShoalRoutes.get(14)));
        assertEquals("giant krill", 150, ShoalRoutes.stopTicks(ShoalRoutes.get(29)));
        for (int id = 14; id <= 29; id++) assertTrue("shoal " + id, ShoalRoutes.stopTicks(ShoalRoutes.get(id)) > 0);
    }

    @Test
    public void findsTheRouteByWhereTheShoalIsWhenTheIdDoesntMatch() {
        // On shoal 14's path (the marlin route east of Rellekka), under some other world view id
        net.runelite.api.coords.WorldPoint at = new net.runelite.api.coords.WorldPoint(2718, 4050, 0);
        assertSame(ShoalRoutes.get(14), ShoalRoutes.find(99, at, 10));
        assertSame(ShoalRoutes.get(14), ShoalRoutes.find(14, at, 10));
        assertNull(ShoalRoutes.find(99, new net.runelite.api.coords.WorldPoint(3222, 3218, 0), 10));
    }
}
