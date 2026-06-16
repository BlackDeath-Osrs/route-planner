package com.routeplanner.util;

import com.google.gson.*;
import com.routeplanner.model.Route;
import net.runelite.api.coords.WorldPoint;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class RouteSerializer {

    private RouteSerializer() {
    }

    public static String toJson(List<Route> routes, Gson gson) {
        return gson.toJson(routes);
    }

    public static List<Route> fromJson(String json, Gson gson) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type listType = new com.google.gson.reflect.TypeToken<List<Route>>(){}.getType();
            List<Route> loaded = gson.fromJson(json, listType);
            if (loaded != null) { for (Route r : loaded) r.migrateIfNeeded(); }
            return loaded;
        } catch (Exception e) {
            return new ArrayList<>();
        }
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
