package com.routeplanner;

import com.google.gson.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.List;

/**
 * Checks a route file the way a player walks it: from where each step's path ends to the next step's
 * tile, with the bundled collision map and transports. Prints steps nobody can reach (NO PATH) and
 * steps whose line stops short (CLOSE(n), n tiles off). Only runs when given a route:
 * ROUTE=path/to/route.json ./gradlew test --tests com.routeplanner.RouteTileCheck -i | grep RESULT
 * Add SHOW=<step number> to also print that step's jumps (stairs, transports).
 */
public class RouteTileCheck {
    @Test
    @SuppressWarnings("unchecked")
    public void check() throws Exception {
        org.junit.Assume.assumeTrue("set ROUTE to a route file", System.getenv("ROUTE") != null);
        SailingRouteTest.load();
        java.lang.reflect.Field f = SailingRouteTest.class.getDeclaredField("overlay"); f.setAccessible(true);
        Object overlay = f.get(null);
        Method bp = PathfinderOverlay.class.getDeclaredMethod("buildPath", WorldPoint.class, WorldPoint.class, boolean.class);
        bp.setAccessible(true);
        JsonObject route = new JsonParser().parse(new String(Files.readAllBytes(Paths.get(System.getenv("ROUTE"))))).getAsJsonObject();
        WorldPoint prev = null; int n = 0;
        for (JsonElement se : route.getAsJsonArray("sections")) {
            JsonObject sec = se.getAsJsonObject();
            for (JsonElement ste : sec.getAsJsonArray("steps")) {
                JsonObject st = ste.getAsJsonObject(); n++;
                WorldPoint from = prev;
                if (st.has("teleportDestination")) { JsonObject t = st.getAsJsonObject("teleportDestination"); from = new WorldPoint(t.get("x").getAsInt(), t.get("y").getAsInt(), t.get("plane").getAsInt()); }
                if (!st.has("worldPoint")) { if (st.has("teleportDestination")) prev = from; continue; }
                JsonObject w = st.getAsJsonObject("worldPoint");
                WorldPoint to = new WorldPoint(w.get("x").getAsInt(), w.get("y").getAsInt(), w.get("plane").getAsInt());
                if (from != null && !from.equals(to)) {
                    List<WorldPoint> p = (List<WorldPoint>) bp.invoke(overlay, from, to, false);
                    String tag = sec.get("name").getAsString() + " | " + st.get("name").getAsString() + " " + to.getX() + "," + to.getY() + "," + to.getPlane();
                    if (p.isEmpty()) { System.out.println("RESULT NO PATH   #" + n + " " + tag + " (from " + from.getX() + "," + from.getY() + "," + from.getPlane() + ")"); prev = to; continue; }
                    if (String.valueOf(n).equals(System.getenv("SHOW"))) {
                        // SHOW=<step number>: print the path's jumps (stairs, transports, teleports)
                        for (int i = 1; i < p.size(); i++) {
                            if (p.get(i - 1).distanceTo(p.get(i)) > 1) System.out.println("RESULT   jump " + p.get(i - 1) + " -> " + p.get(i));
                        }
                    }
                    int off = p.get(p.size() - 1).distanceTo(to);
                    if (off > 1) System.out.println("RESULT CLOSE(" + off + ") #" + n + " " + tag);
                    prev = p.get(p.size() - 1);   // where the player actually ends up
                    continue;
                }
                prev = to;
            }
        }
        System.out.println("RESULT checked " + n + " steps");
    }
}
