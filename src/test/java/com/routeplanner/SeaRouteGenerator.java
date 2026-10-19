package com.routeplanner;

import com.routeplanner.sailing.SailingDocks;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import org.junit.Assume;
import org.junit.Test;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Makes resources/com/routeplanner/sailing/sea_routes_{raft,skiff,sloop}.tsv: the sea route between every
 * pair of moorings for each boat size, worked out by the plugin's own pathfinder (same clearance and
 * costs as live pathing). Only runs when asked, after a collision map or dock change:
 *
 *   SEA_ROUTES_OUT=src/main/resources/com/routeplanner/sailing ./gradlew test --tests com.routeplanner.SeaRouteGenerator
 */
public class SeaRouteGenerator {

    private static final String[] SIZES = {"raft", "skiff", "sloop"};

    @Test
    public void generate() throws Exception {
        String out = System.getenv("SEA_ROUTES_OUT");
        Assume.assumeTrue("set SEA_ROUTES_OUT=<dir> to generate", out != null && !out.isEmpty());
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class[]{Client.class},
            (p, m, a) -> m.getReturnType() == int.class ? 0 : m.getReturnType() == boolean.class ? false : null);
        RoutePlannerConfig config = (RoutePlannerConfig) Proxy.newProxyInstance(RoutePlannerConfig.class.getClassLoader(),
            new Class[]{RoutePlannerConfig.class}, (p, m, a) -> m.getName().equals("useSailing") || m.getName().equals("betaFeatures") ? true : null);
        SailingDocks docks = new SailingDocks(client, null);
        PathfinderOverlay overlay = new PathfinderOverlay(client, null, config, null, docks);
        Method load = PathfinderOverlay.class.getDeclaredMethod("loadCollisionMap");
        load.setAccessible(true);
        load.invoke(overlay);

        List<WorldPoint> moorings = new ArrayList<>();
        for (SailingDocks.Dock d : docks.getDocks()) moorings.add(d.sea);
        Path dir = Paths.get(out);
        Files.createDirectories(dir);
        for (int size = 0; size < SIZES.length; size++) {
            int width = size + 1;
            long started = System.currentTimeMillis();
            int routes = 0;
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("sea_routes_" + SIZES[size] + ".tsv")))) {
                w.println("# Sea routes between Sailing moorings (see docks.tsv) for a " + SIZES[size] + " (" + width
                    + " tile" + (width > 1 ? "s" : "") + " wide), made by SeaRouteGenerator (tests) with the plugin's pathfinder.");
                w.println("# From mooring\tTo mooring\tSea distance (tiles)\tTurning points");
                for (int i = 0; i < moorings.size(); i++) {
                    Set<WorldPoint> later = new LinkedHashSet<>(moorings.subList(i + 1, moorings.size()));
                    if (later.isEmpty()) break;
                    Map<WorldPoint, List<WorldPoint>> paths = overlay.seaPathsFrom(moorings.get(i), later, width);
                    for (WorldPoint to : later) {
                        List<WorldPoint> path = paths.get(to);
                        if (path == null) continue;
                        StringBuilder turns = new StringBuilder();
                        for (WorldPoint t : overlay.turningPoints(path)) {
                            if (turns.length() > 0) turns.append(';');
                            turns.append(t.getX()).append(' ').append(t.getY());
                        }
                        WorldPoint from = moorings.get(i);
                        w.println(from.getX() + " " + from.getY() + " " + from.getPlane() + "\t" + to.getX() + " " + to.getY() + " "
                            + to.getPlane() + "\t" + (path.size() - 1) + "\t" + turns);
                        routes++;
                    }
                }
            }
            System.out.println("SEA ROUTES " + SIZES[size] + ": " + routes + " in " + (System.currentTimeMillis() - started) / 1000 + "s");
        }
    }
}
