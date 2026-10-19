package com.routeplanner;

import com.routeplanner.sailing.SailingDocks;
import com.routeplanner.transport.Transport;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Routes over the sea (a beta feature, switched on here) with the bundled collision map, docks and
 * sea routes, as a level 99 sailor whose boats' docks aren't known yet (so any dock can be boarded from).
 */
public class SailingRouteTest {

    private static SailingDocks docks;
    private static PathfinderOverlay overlay;
    private static Method buildPath;
    private static Method smoothSeaLegs;

    @BeforeClass
    public static void load() throws Exception {
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class[]{Client.class},
            (proxy, m, args) -> m.getName().equals("getRealSkillLevel") ? 99
                : m.getReturnType() == int.class ? 0 : m.getReturnType() == boolean.class ? false : null);
        RoutePlannerConfig config = (RoutePlannerConfig) Proxy.newProxyInstance(
            RoutePlannerConfig.class.getClassLoader(), new Class[]{RoutePlannerConfig.class},
            (proxy, m, args) -> m.getName().equals("useSailing") || m.getName().equals("betaFeatures") ? true : null);
        docks = new SailingDocks(client, null);
        docks.onGameTick();
        overlay = new PathfinderOverlay(client, null, config, null, docks);

        Method load = PathfinderOverlay.class.getDeclaredMethod("loadCollisionMap");
        load.setAccessible(true);
        load.invoke(overlay);
        buildPath = PathfinderOverlay.class.getDeclaredMethod("buildPath", WorldPoint.class, WorldPoint.class, boolean.class);
        buildPath.setAccessible(true);
        smoothSeaLegs = PathfinderOverlay.class.getDeclaredMethod("smoothSeaLegs", List.class, boolean.class);
        smoothSeaLegs.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    private static List<WorldPoint> route(WorldPoint from, WorldPoint to, boolean onBoat) throws Exception {
        List<WorldPoint> path = (List<WorldPoint>) buildPath.invoke(overlay, from, to, onBoat);
        return (List<WorldPoint>) smoothSeaLegs.invoke(overlay, path, onBoat);
    }

    /** The dock crossings along a path, in order, as their action texts. */
    private static List<String> crossings(List<WorldPoint> path) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i + 1 < path.size(); i++) {
            Transport t = docks.crossing(path.get(i), path.get(i + 1));
            if (t != null) out.add(t.actionText);
        }
        return out;
    }

    private static void assertArrives(List<WorldPoint> path, WorldPoint target) {
        assertFalse("no path", path.isEmpty());
        assertTrue("ends at " + path.get(path.size() - 1), path.get(path.size() - 1).distanceTo(target) <= 1);
    }

    @Test
    public void walksToADockSailsAndDisembarksOnAnIsland() throws Exception {
        WorldPoint pandemonium = new WorldPoint(3069, 2987, 0);
        List<WorldPoint> path = route(new WorldPoint(3222, 3218, 0), pandemonium, false);
        assertArrives(path, pandemonium);
        List<String> c = crossings(path);
        assertEquals(c.toString(), 2, c.size());
        assertTrue(c.get(0), c.get(0).startsWith(SailingDocks.BOARD));
        assertEquals(SailingDocks.DISEMBARK + " at The Pandemonium", c.get(1));
    }

    @Test
    public void sailsAroundTheCoastFromOpenSea() throws Exception {
        WorldPoint lunarDock = new WorldPoint(2151, 3881, 0);
        List<WorldPoint> path = route(new WorldPoint(3000, 3100, 0), lunarDock, true);
        assertArrives(path, lunarDock);
        assertEquals("[" + SailingDocks.DISEMBARK + " at Lunar Isle]", crossings(path).toString());
    }

    @Test
    public void sailedLegsAreUnbrokenTileByTile() throws Exception {
        List<WorldPoint> path = route(new WorldPoint(3000, 3100, 0), new WorldPoint(2151, 3881, 0), true);
        for (int i = 0; i + 1 < path.size(); i++) {
            WorldPoint a = path.get(i), b = path.get(i + 1);
            if (docks.crossing(a, b) != null || docks.seaLinksFrom(a) != null) continue;
            assertTrue(a + " -> " + b, Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())) <= 1);
        }
    }

    /** The fewest tiles from the shore the route comes, away from its ends and the docks, for a boat this wide. */
    @SuppressWarnings("unchecked")
    private static int closestToShore(WorldPoint from, WorldPoint to, int boatWidth) throws Exception {
        java.lang.reflect.Field width = PathfinderOverlay.class.getDeclaredField("boatWidthTiles");
        width.setAccessible(true);
        Method clearance = PathfinderOverlay.class.getDeclaredMethod("clearance", int.class, int.class, int.class);
        clearance.setAccessible(true);
        width.setInt(overlay, boatWidth);
        try {
            List<WorldPoint> path = route(from, to, true);
            assertArrives(path, to);
            int closest = Integer.MAX_VALUE;
            for (WorldPoint p : path) {
                int x = p.getX(), y = p.getY();
                if (Math.max(Math.abs(x - from.getX()), Math.abs(y - from.getY())) <= 6
                    || Math.max(Math.abs(x - to.getX()), Math.abs(y - to.getY())) <= 6
                    || docks.nearMooring(x, y, 6)) continue;
                closest = Math.min(closest, (int) clearance.invoke(overlay, x, y, 0));
            }
            return closest;
        } finally {
            width.setInt(overlay, 1);
        }
    }

    @Test
    public void aSloopKeepsWellClearOfTheShore() throws Exception {
        // East of Rellekka, where the line used to ride the coast
        assertTrue(closestToShore(new WorldPoint(2699, 4075, 0), new WorldPoint(2479, 3854, 0), 3) >= 4);
    }

    @Test
    public void aSloopGoesRoundTheNarrowChannelByEtceteria() throws Exception {
        // From Etceteria west: a 6-8 tile channel between the islands and a small island to the south.
        // A raft takes it; a sloop goes round the small island instead.
        WorldPoint from = new WorldPoint(2615, 3841, 0), to = new WorldPoint(2479, 3854, 0);
        assertTrue("raft", closestToShore(from, to, 1) < 4);
        assertTrue("sloop", closestToShore(from, to, 3) >= 4);
    }

    @Test
    public void docksAtWyrmscraig() throws Exception {
        WorldPoint inland = new WorldPoint(2580, 2296, 0);
        List<WorldPoint> path = route(new WorldPoint(3000, 3100, 0), inland, true);
        assertArrives(path, inland);
        assertEquals("[" + SailingDocks.DISEMBARK + " at Wyrmscraig]", crossings(path).toString());
    }

    @Test
    public void sailsThroughTheCaveMouthIntoWyrmscraigCavern() throws Exception {
        WorldPoint caveFloor = new WorldPoint(2585, 8606, 0);
        List<WorldPoint> path = route(new WorldPoint(3000, 3100, 0), caveFloor, true);
        assertArrives(path, caveFloor);
        assertEquals("[" + SailingDocks.DISEMBARK + " at Wyrmscraig Cavern]", crossings(path).toString());
        assertTrue("goes through the cave mouth", path.contains(new WorldPoint(2557, 2202, 0))
            && path.contains(new WorldPoint(2572, 8606, 0)));
    }

    @Test
    public void walkingRoutesDontTouchTheSea() throws Exception {
        WorldPoint varrock = new WorldPoint(3213, 3424, 0);
        List<WorldPoint> path = route(new WorldPoint(3222, 3218, 0), varrock, false);
        assertArrives(path, varrock);
        assertTrue(crossings(path).isEmpty());
    }
}
