package com.routeplanner.hub;

import java.util.List;

/**
 * One catalog entry from index.json. All fields are read-only data from the network -- nothing
 * here is trusted until it has passed {@link HubRouteEntry#validate()}, which enforces the v1.4
 * plan's bounds (name/description length, step count) and re-runs the link scanner over every
 * free-text field, independently of whatever checks the submitter's editor may or may not have
 * applied.
 */
public class HubRouteEntry {
    public String id;
    public String name;
    public String author;
    public String description;
    public String icon;
    public String file;
    public int steps;
    public List<String> tags;
    public String updated;

    private static final int MAX_NAME_LEN = 100;
    private static final int MAX_DESC_LEN = 120;
    private static final int MAX_STEPS = 1500;

    /**
     * @return null if the entry is safe to display/install, or a short reason if it should be
     * rejected. Never throws -- a malformed catalog entry should be skipped, not crash the panel.
     */
    public String validate() {
        if (id == null || id.isEmpty()) return "missing id";
        if (id.contains("..") || id.contains("/") || id.contains("\\")) return "unsafe id";
        if (name == null || name.isEmpty()) return "missing name";
        if (name.length() > MAX_NAME_LEN) return "name too long";
        if (author == null || author.isEmpty()) return "missing author";
        if (description == null) return "missing description";
        if (description.length() > MAX_DESC_LEN) return "description too long";
        if (file == null || file.isEmpty() || file.contains("..") || file.startsWith("/")) return "unsafe file path";
        if (icon != null && (icon.contains("..") || icon.startsWith("/"))) return "unsafe icon path";
        if (steps < 0 || steps > MAX_STEPS) return "step count out of bounds";

        String linkErr = com.routeplanner.util.RouteTextValidator.checkField("Route name", name);
        if (linkErr == null) linkErr = com.routeplanner.util.RouteTextValidator.checkField("Description", description);
        if (linkErr == null && author != null) linkErr = com.routeplanner.util.RouteTextValidator.checkField("Author", author);
        if (linkErr != null) return linkErr;

        return null;
    }
}
