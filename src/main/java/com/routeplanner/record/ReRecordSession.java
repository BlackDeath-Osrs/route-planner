package com.routeplanner.record;

import com.routeplanner.model.Route;
import com.routeplanner.model.RouteSection;
import com.routeplanner.model.RouteStep;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Re-recording one section: the section's ticks from before, and which of its steps the route
 * ticked off while recording (with how many steps had been recorded at that moment, which tells
 * the comparison roughly which recorded step goes with it).
 */
public class ReRecordSession {
    @Getter private final Route route;
    @Getter private final RouteSection section;
    /** The section's steps as they were when the re-record started, in order. */
    @Getter private final List<RouteStep> originalSteps;
    private final Map<RouteStep, Boolean> priorCompleted = new HashMap<>();
    @Getter private final Map<RouteStep, Integer> tickedAt = new HashMap<>();

    public ReRecordSession(Route route, RouteSection section) {
        this.route = route;
        this.section = section;
        this.originalSteps = new ArrayList<>(section.getSteps());
        for (RouteStep s : originalSteps) priorCompleted.put(s, s.isCompleted());
    }

    /** The route ticked {@code step} off when {@code draftCount} steps had been recorded. */
    public void ticked(RouteStep step, int draftCount) {
        if (priorCompleted.containsKey(step)) tickedAt.putIfAbsent(step, draftCount);
    }

    public boolean wasTicked(RouteStep step) {
        return tickedAt.containsKey(step);
    }

    /** Ticked before the re-record started, or ticked off during it. */
    public boolean doneBeforeOrDuring(RouteStep step) {
        return Boolean.TRUE.equals(priorCompleted.get(step)) || tickedAt.containsKey(step);
    }

    /** Put the section's ticks back (keeping anything ticked off while recording). */
    public void restoreTicks() {
        for (RouteStep s : originalSteps) s.setCompleted(doneBeforeOrDuring(s));
    }
}
