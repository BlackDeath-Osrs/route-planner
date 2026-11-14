package com.routeplanner;

import lombok.RequiredArgsConstructor;

/** Stroke style for the waypoint line. */
@RequiredArgsConstructor
public enum LineStyle {
    SOLID("Solid"),
    DASHED("Dashed"),
    DOTTED("Dotted"),
    DASH_DOT("Dash-dot"),
    TRACER("Tracer");

    private final String label;
    @Override public String toString() { return label; }
}
