package com.routeplanner;

/** Stroke style for the waypoint line. */
public enum LineStyle {
    SOLID("Solid"),
    DASHED("Dashed"),
    DOTTED("Dotted"),
    DASH_DOT("Dash-dot"),
    TRACER("Tracer");

    private final String label;
    LineStyle(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
