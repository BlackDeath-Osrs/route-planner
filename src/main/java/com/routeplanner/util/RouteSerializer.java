package com.routeplanner.util;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.routeplanner.model.*;
import net.runelite.api.coords.WorldPoint;
import java.lang.reflect.Type;
import java.util.*;

public class RouteSerializer {

    private RouteSerializer() {
    }

    public static String toJson(List<Route> routes, Gson gson) {
        return gson.toJson(routes);
    }

    /**
     * Parses saved routes. Empty input is an empty list, but text that doesn't parse throws: an
     * empty list there would look like "no routes" and the next save would overwrite them.
     */
    public static List<Route> fromJson(String json, Gson gson) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        Type listType = new TypeToken<List<Route>>(){}.getType();
        List<Route> loaded = gson.fromJson(json, listType);
        if (loaded == null) return new ArrayList<>();
        for (Route r : loaded) r.migrateIfNeeded();
        return loaded;
    }

    /**
     * A copy of the route to give to other people (export, the Route Hub): the steps and their ticks,
     * without this player's progress on them (account, inventory counts, XP, kills, catches).
     */
    public static Route shareableCopy(Route route, Gson gson) {
        Route copy = gson.fromJson(gson.toJson(route), Route.class);
        copy.migrateIfNeeded();
        for (RouteStep step : copy.getAllSteps()) {
            step.resetProgress();
            step.setSkillingStartXp(0);
        }
        return copy;
    }

    public static class WorldPointAdapter implements JsonSerializer<WorldPoint>, JsonDeserializer<WorldPoint> {
        @Override
        public JsonElement serialize(WorldPoint src, Type typeOfSrc, JsonSerializationContext ctx) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", src.getX());
            obj.addProperty("y", src.getY());
            obj.addProperty("plane", src.getPlane());
            return obj;
        }

        @Override
        public WorldPoint deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) {
            JsonObject obj = json.getAsJsonObject();
            return new WorldPoint(
                obj.get("x").getAsInt(),
                obj.get("y").getAsInt(),
                obj.get("plane").getAsInt()
            );
        }
    }
}
