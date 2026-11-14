package com.routeplanner.model;

import lombok.Data;
import java.util.*;

@Data
public class RouteSection {
    private String id;
    private String name;
    private boolean collapsed;
    private List<RouteStep> steps = new ArrayList<>();

    public RouteSection() {}

    public RouteSection(String id, String name) {
        this.id = id;
        this.name = name;
        this.collapsed = false;
    }

    /** Sections are told apart by identity, like routes and steps. */
    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
