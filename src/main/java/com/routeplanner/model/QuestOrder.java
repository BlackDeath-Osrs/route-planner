package com.routeplanner.model;

import java.util.*;

/**
 * Puts a section's steps in quest-stage order. A quest's stage only goes up, so steps checking the
 * same quest var must come in rising order. Each step with a progress check closes a block holding
 * it and the steps without one before it (what was done to reach that check); blocks of the same
 * quest are sorted by stage within the places they already take, and everything else stays put.
 */
public final class QuestOrder {

    private QuestOrder() {}

    /** The sorted steps, or null if they were already in order. */
    public static List<RouteStep> sorted(List<RouteStep> steps) {
        List<List<RouteStep>> blocks = new ArrayList<>();
        List<RouteStep> block = new ArrayList<>();
        for (RouteStep s : steps) {
            block.add(s);
            if (s.progress() != null) {
                blocks.add(block);
                block = new ArrayList<>();
            }
        }
        if (!block.isEmpty()) blocks.add(block);

        Map<String, List<Integer>> slotsByVar = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            ProgressCheck c = stage(last(blocks.get(i)));
            if (c != null) slotsByVar.computeIfAbsent(c.kind + " " + c.id, k -> new ArrayList<>()).add(i);
        }
        List<List<RouteStep>> out = new ArrayList<>(blocks);
        boolean moved = false;
        for (List<Integer> slots : slotsByVar.values()) {
            List<List<RouteStep>> quest = new ArrayList<>();
            for (int i : slots) quest.add(blocks.get(i));
            quest.sort(Comparator.comparingInt(b -> stage(last(b)).atLeast)); // stable: equal stages keep their order
            for (int k = 0; k < slots.size(); k++) {
                moved |= quest.get(k) != blocks.get(slots.get(k));
                out.set(slots.get(k), quest.get(k));
            }
        }
        if (!moved) return null;
        List<RouteStep> flat = new ArrayList<>(steps.size());
        out.forEach(flat::addAll);
        return flat;
    }

    /** The step's "var >= stage" check; bit checks have no order. */
    private static ProgressCheck stage(RouteStep s) {
        ProgressCheck c = s.progress();
        return c != null && c.bit < 0 ? c : null;
    }

    private static RouteStep last(List<RouteStep> block) {
        return block.get(block.size() - 1);
    }
}
