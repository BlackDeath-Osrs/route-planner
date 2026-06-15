package com.routeplanner.skilling;

import java.util.*;

public class FiremakingLogs {

    public static class Log {
        public final String name;
        public final int level;
        public final double xp;
        public final String item;  // bank item to highlight
        public Log(String name, int level, double xp, String item) {
            this.name = name; this.level = level; this.xp = xp; this.item = item;
        }
        @Override public String toString() { return name + " (Lv " + level + ")"; }
    }

    public static final List<Log> LOGS = Arrays.asList(
        new Log("Normal",   1,  40.0,  "Logs"),
        new Log("Oak",      15, 60.0,  "Oak logs"),
        new Log("Willow",   30, 90.0,  "Willow logs"),
        new Log("Teak",     35, 105.0, "Teak logs"),
        new Log("Maple",    45, 135.0, "Maple logs"),
        new Log("Mahogany", 50, 157.5, "Mahogany logs"),
        new Log("Yew",      60, 202.5, "Yew logs"),
        new Log("Magic",    75, 303.8, "Magic logs"),
        new Log("Redwood",  90, 350.0, "Redwood logs")
    );

    public static Log getByLabel(String label) {
        return LOGS.stream().filter(l -> l.toString().equals(label)).findFirst().orElse(null);
    }
}
