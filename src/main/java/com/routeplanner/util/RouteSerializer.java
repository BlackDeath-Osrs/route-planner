package com.routeplanner.util;

import com.google.gson.*;
import com.routeplanner.model.Route;
import com.routeplanner.model.RouteStep;
import net.runelite.api.coords.WorldPoint;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class RouteSerializer {

    public static Gson buildGson() {
        return new GsonBuilder()
            .registerTypeAdapter(net.runelite.api.coords.WorldPoint.class,
                new WorldPointAdapter())
            .setPrettyPrinting()
            .create();
    }
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(WorldPoint.class, new WorldPointAdapter())
        .create();

    public static String toJson(List<Route> routes) {
        return GSON.toJson(routes);
    }

    public static List<Route> fromJson(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type listType = new com.google.gson.reflect.TypeToken<List<Route>>(){}.getType();
            java.util.List<Route> loaded = GSON.fromJson(json, listType);
            if (loaded != null) { for (Route r : loaded) r.migrateIfNeeded(); }
            return loaded;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    static class WorldPointAdapter implements JsonSerializer<WorldPoint>, JsonDeserializer<WorldPoint> {
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
