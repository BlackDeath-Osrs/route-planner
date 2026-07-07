package com.routeplanner.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

@Data
public class Route {
    private String name;
    private List<RouteSection> sections = new ArrayList<>();

    // Legacy flat-step format; migrated into a default section on load.
    @SerializedName("steps")
    private List<RouteStep> legacySteps;

    public Route(String name) {
        this.name = name;
    }

    public List<RouteStep> getAllSteps() {
        List<RouteStep> all = new ArrayList<>();
        if (sections != null) {
            for (RouteSection sec : sections) {
                if (sec.getSteps() != null) all.addAll(sec.getSteps());
            }
        }
        return all;
    }

    /** Flattened read accessor used across the codebase. */
    public List<RouteStep> getSteps() {
        return getAllSteps();
    }

    public void migrateIfNeeded() {
        if (sections == null) sections = new ArrayList<>();
        if (sections.isEmpty() && legacySteps != null && !legacySteps.isEmpty()) {
            RouteSection def = new RouteSection(UUID.randomUUID().toString(), "Steps");
            def.getSteps().addAll(legacySteps);
            sections.add(def);
        }
        legacySteps = null;
        // v1.2: old agility steps (StepType.AGILITY, now removed) deserialize with type==null.
        // Convert them to the new skilling form (skill=AGILITY) + course start location.
        for (RouteSection sec : sections) {
            if (sec.getSteps() == null) continue;
            for (RouteStep st : sec.getSteps()) {
                if (st.getType() == null && st.getAgilityCourse() != null) {
                    st.setType(StepType.SKILLING);
                    st.setSkillingSkill("AGILITY");
                    st.setSkillingGoalType(st.getAgilityGoalType() != null ? st.getAgilityGoalType() : "LEVEL");
                    st.setSkillingGoalValue(st.getAgilityGoalValue());
                    net.runelite.api.coords.WorldPoint loc =
                        com.routeplanner.agility.AgilityCoursePresets.startLocation(st.getAgilityCourse());
                    if (loc != null && st.getWorldPoint() == null) st.setWorldPoint(loc);
                    if (st.getName() == null || st.getName().isEmpty()) {
                        st.setName("Agility: " + st.getAgilityCourse());
                    }
                }
            }
        }
    }

    public RouteSection findSection(String id) {
        if (id == null || sections == null) return null;
        for (RouteSection s : sections) if (id.equals(s.getId())) return s;
        return null;
    }

    public RouteSection ensureLastSection() {
        if (sections == null) sections = new ArrayList<>();
        if (sections.isEmpty()) {
            sections.add(new RouteSection(UUID.randomUUID().toString(), "Section 1"));
        }
        return sections.get(sections.size() - 1);
    }

    public void addStepToSection(RouteStep step, String sectionId) {
        RouteSection sec = findSection(sectionId);
        if (sec == null) sec = ensureLastSection();
        step.setSectionId(sec.getId());
        sec.getSteps().add(step);
    }

    public void addStepToLastSection(RouteStep step) {
        RouteSection sec = ensureLastSection();
        step.setSectionId(sec.getId());
        sec.getSteps().add(step);
    }

    public void removeStep(RouteStep step) {
        if (sections == null) return;
        for (RouteSection sec : sections) {
            if (sec.getSteps() != null && sec.getSteps().remove(step)) return;
        }
    }

    public void removeStepsIf(Predicate<RouteStep> pred) {
        if (sections == null) return;
        for (RouteSection sec : sections) {
            if (sec.getSteps() != null) sec.getSteps().removeIf(pred);
        }
    }

    public RouteStep getActiveStep() {
        if (sections == null) return null;
        for (RouteSection sec : sections) {
            if (sec.getSteps() == null) continue;
            for (RouteStep s : sec.getSteps()) if (!s.isCompleted()) return s;
        }
        return null;
    }

    public boolean isComplete() {
        return getAllSteps().stream().allMatch(RouteStep::isCompleted);
    }

    public void resetProgress() {
        getAllSteps().forEach(s -> s.setCompleted(false));
    }
}
