package com.routeplanner;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

/**
 * Optional bridge to the Shortest Path plugin's cross-plugin API
 * (https://github.com/Skretzo/shortest-path/blob/master/docs/Cross-plugin-communication.md).
 *
 * Plugin Hub plugins can't share classes, so this talks to Shortest Path purely through
 * PluginMessage events: a "query" asks it to compute a path without drawing anything, and it
 * answers with a "result" carrying the same id. The answer may be posted from Shortest Path's
 * own pathfinding thread, so the handler only stashes it in a volatile field; everything else
 * here runs on the client thread via PathfinderOverlay.onGameTick.
 *
 * When Shortest Path isn't installed/enabled, or stops answering (e.g. an older version
 * without query support), isActive() goes false and the built-in A* is used instead.
 */
@Slf4j
@Singleton
public class ShortestPathBridge {

    private static final String NAMESPACE = "shortestpath";
    private static final String PLUGIN_NAME = "Shortest Path";
    private static final String ID_PREFIX = "routeplanner-";
    /** Pathfinder updates (every 2 game ticks) to wait for an answer before giving up. */
    private static final int RESPONSE_TIMEOUT_UPDATES = 4;
    /** Pathfinder updates to wait after a timeout before trying Shortest Path again (~2 min). */
    private static final int RETRY_AFTER_TIMEOUT_UPDATES = 100;

    /** An answer to one of our queries, already converted to plain route-planner types. */
    public static final class Result {
        final String id;
        final List<WorldPoint> path;
        final boolean reached;
        final boolean cancelled;
        /** Shortest Path's own description of each jump on the path (stairs, teleports...). */
        final List<com.routeplanner.transport.Transport> jumps;

        Result(String id, List<WorldPoint> path, boolean reached, boolean cancelled,
               List<com.routeplanner.transport.Transport> jumps) {
            this.id = id;
            this.path = path;
            this.reached = reached;
            this.cancelled = cancelled;
            this.jumps = jumps;
        }
    }

    private final EventBus eventBus;
    private final PluginManager pluginManager;
    private final RoutePlannerConfig config;

    private int nextId = 0;
    private String pendingId;
    private WorldPoint pendingTarget;
    private int updatesWaiting;
    private int timeoutCooldown;
    private volatile Result latest;

    @Inject
    public ShortestPathBridge(EventBus eventBus, PluginManager pluginManager, RoutePlannerConfig config) {
        this.eventBus = eventBus;
        this.pluginManager = pluginManager;
        this.config = config;
    }

    /** True when the user picked Shortest Path and it's installed, enabled and answering. */
    public boolean isActive() {
        return config.pathfinder() == PathfinderMode.SHORTEST_PATH
            && timeoutCooldown == 0
            && isShortestPathEnabled();
    }

    private boolean isShortestPathEnabled() {
        for (Plugin p : pluginManager.getPlugins()) {
            PluginDescriptor d = p.getClass().getAnnotation(PluginDescriptor.class);
            if (d != null && PLUGIN_NAME.equalsIgnoreCase(d.name())) {
                return pluginManager.isPluginEnabled(p);
            }
        }
        return false;
    }

    /**
     * Advance timers once per pathfinder update. Returns true if a pending query just timed out,
     * meaning the caller should rebuild with the built-in pathfinder right away.
     */
    public boolean tick() {
        if (timeoutCooldown > 0) timeoutCooldown--;
        if (pendingId == null) return false;
        if (++updatesWaiting <= RESPONSE_TIMEOUT_UPDATES) return false;
        log.info("Route Planner: Shortest Path didn't answer a path query, using built-in pathfinder");
        reset();
        timeoutCooldown = RETRY_AFTER_TIMEOUT_UPDATES;
        return true;
    }

    /** Ask Shortest Path for a path. A no-op if we're already waiting on the same target. */
    public void request(WorldPoint start, WorldPoint target) {
        if (pendingId != null && target.equals(pendingTarget)) return;
        pendingId = ID_PREFIX + (nextId++);
        pendingTarget = target;
        updatesWaiting = 0;
        latest = null;

        Map<String, Object> data = new HashMap<>();
        data.put("id", pendingId);
        data.put("start", start);
        data.put("target", target);
        if (config.recordTrace()) log.info("[RP-TRACE] SP ASK {} from {} to {}", pendingId, start, target);
        eventBus.post(new PluginMessage(NAMESPACE, "query", data));
    }

    /** Returns the answer to the pending query for this target once it has arrived, else null. */
    public Result takeResult(WorldPoint target) {
        Result r = latest;
        if (r == null || pendingId == null || !pendingId.equals(r.id)) return null;
        boolean forTarget = target.equals(pendingTarget);
        reset();
        return forTarget ? r : null;
    }

    /** Forget any in-flight query, so a late answer to it is ignored. */
    public void reset() {
        pendingId = null;
        pendingTarget = null;
        updatesWaiting = 0;
        latest = null;
    }

    /**
     * Shortest Path's "transports" list: per jump its origin, destination, objectInfo ("Climb-down
     * Ladder 2147", none for a teleport) and displayInfo ("Guardians of the Rift Minigame Teleport").
     */
    private static List<com.routeplanner.transport.Transport> jumps(Object raw) {
        List<com.routeplanner.transport.Transport> out = new ArrayList<>();
        if (!(raw instanceof List)) return out;
        for (Object o : (List<?>) raw) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            if (!(m.get("origin") instanceof WorldPoint) || !(m.get("destination") instanceof WorldPoint)) continue;
            String object = m.get("objectInfo") instanceof String ? ((String) m.get("objectInfo")).trim() : "";
            String display = m.get("displayInfo") instanceof String ? ((String) m.get("displayInfo")).trim() : "";
            int objectId = -1;
            String action = object;
            java.util.regex.Matcher id = java.util.regex.Pattern.compile("^(.*\\S)\\s+(\\d+)$").matcher(object);
            if (id.matches()) {
                action = id.group(1);
                objectId = Integer.parseInt(id.group(2));
            }
            boolean teleport = object.isEmpty();
            String text = teleport ? (display.isEmpty() ? "Teleport" : "Teleport: " + display)
                : (action.isEmpty() ? display : action);
            out.add(new com.routeplanner.transport.Transport((WorldPoint) m.get("origin"),
                (WorldPoint) m.get("destination"), text.isEmpty() ? null : text, objectId, teleport));
        }
        return out;
    }

    @Subscribe
    public void onPluginMessage(PluginMessage message) {
        if (!NAMESPACE.equals(message.getNamespace()) || !"result".equals(message.getName())) return;
        Map<String, Object> data = message.getData();
        Object id = data.get("id");
        if (!(id instanceof String) || !((String) id).startsWith(ID_PREFIX)) return;

        List<WorldPoint> path = new ArrayList<>();
        Object rawPath = data.get("path");
        if (rawPath instanceof List) {
            for (Object o : (List<?>) rawPath) {
                if (o instanceof WorldPoint) path.add((WorldPoint) o);
            }
        }
        boolean reached = Boolean.TRUE.equals(data.get("reached"));
        boolean cancelled = "CANCELLED".equals(data.get("reason"));
        if (config.recordTrace()) {
            log.info("[RP-TRACE] SP REPLY {} reached={} reason={} tiles={} end={} closest={} jumps={} transports={}", id,
                reached, data.get("reason"), path.size(), path.isEmpty() ? null : path.get(path.size() - 1),
                data.get("closest"), PathfinderOverlay.describeJumps(path), data.get("transports"));
        }
        latest = new Result((String) id, path, reached, cancelled, jumps(data.get("transports")));
    }
}
