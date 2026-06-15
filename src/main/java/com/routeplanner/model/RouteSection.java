package com.routeplanner.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

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
}
