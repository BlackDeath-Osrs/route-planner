package com.routeplanner;

import com.google.gson.Gson;
import com.routeplanner.model.Route;
import com.routeplanner.util.RouteSerializer;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot-based undo/redo for structural edits to a route (delete step, reorder step).
 * Step contents are not tracked -- the step editor already handles those.
 *
 * <p>Each route keeps its own stacks, keyed by object <em>identity</em> rather than equals().
 * Route is a Lombok {@code @Data} class, so its hashCode shifts whenever its contents change and
 * two routes with identical contents would collide; an IdentityHashMap sidesteps both problems.
 */
public class RouteHistory {
    private static final int MAX_DEPTH = 30;

    private static final class Entry {
        final String label;
        final String json;
        Entry(String label, String json) { this.label = label; this.json = json; }
    }

    private final Map<Route, Deque<Entry>> undo = new IdentityHashMap<>();
    private final Map<Route, Deque<Entry>> redo = new IdentityHashMap<>();

    /** Snapshot the route as it stands right now, before the caller mutates it. */
    public void push(Route route, String label, Gson gson) {
        if (route == null) return;
        String json = snapshot(route, gson);
        if (json == null) return;
        Deque<Entry> stack = stack(undo, route);
        stack.push(new Entry(label, json));
        while (stack.size() > MAX_DEPTH) stack.removeLast();
        stack(redo, route).clear();
    }

    public boolean canUndo(Route route) { return !isEmpty(undo, route); }
    public boolean canRedo(Route route) { return !isEmpty(redo, route); }

    public String peekUndoLabel(Route route) { return peekLabel(undo, route); }
    public String peekRedoLabel(Route route) { return peekLabel(redo, route); }

    /** @return label of the action undone, or null if there was nothing to undo. */
    public String undo(Route route, Gson gson) {
        return move(route, gson, undo, redo);
    }

    /** @return label of the action redone, or null if there was nothing to redo. */
    public String redo(Route route, Gson gson) {
        return move(route, gson, redo, undo);
    }

    /** Drop a route's history, e.g. once the route itself has been deleted. */
    public void forget(Route route) {
        undo.remove(route);
        redo.remove(route);
    }

    private String move(Route route, Gson gson, Map<Route, Deque<Entry>> fromMap,
                        Map<Route, Deque<Entry>> toMap) {
        if (route == null || isEmpty(fromMap, route)) return null;
        Deque<Entry> from = stack(fromMap, route);
        Entry e = from.peek();
        String current = snapshot(route, gson);
        if (current == null) return null;
        if (!restore(route, e.json, gson)) return null;
        from.pop();
        Deque<Entry> to = stack(toMap, route);
        to.push(new Entry(e.label, current));
        while (to.size() > MAX_DEPTH) to.removeLast();
        return e.label;
    }

    private Deque<Entry> stack(Map<Route, Deque<Entry>> m, Route route) {
        return m.computeIfAbsent(route, r -> new ArrayDeque<>());
    }

    private boolean isEmpty(Map<Route, Deque<Entry>> m, Route route) {
        if (route == null) return true;
        Deque<Entry> d = m.get(route);
        return d == null || d.isEmpty();
    }

    private String peekLabel(Map<Route, Deque<Entry>> m, Route route) {
        if (isEmpty(m, route)) return null;
        return m.get(route).peek().label;
    }

    private String snapshot(Route route, Gson gson) {
        try {
            return RouteSerializer.toJson(Collections.singletonList(route), gson);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Copy a snapshot's contents back into the existing Route instance. The object itself is never
     * swapped: the plugin's active-route reference and this class's identity keys both rely on the
     * instance staying put.
     */
    private boolean restore(Route route, String json, Gson gson) {
        try {
            List<Route> parsed = RouteSerializer.fromJson(json, gson);
            if (parsed == null || parsed.isEmpty()) return false;
            Route snap = parsed.get(0);
            route.setName(snap.getName());
            route.setSections(snap.getSections());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
