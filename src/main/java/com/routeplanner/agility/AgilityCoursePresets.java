package com.routeplanner.agility;

import com.routeplanner.util.DataFiles;
import net.runelite.api.coords.WorldPoint;
import java.util.*;

/**
 * The rooftop and other agility courses, from agility_courses.tsv: a "course" row (with the map
 * region it's recognised by and the tile to walk to, both optional), then its "obstacle" rows.
 */
public class AgilityCoursePresets {

    private static final Map<Integer, AgilityCourse> BY_REGION = new HashMap<>();
    private static final Map<String, WorldPoint> STARTS = new HashMap<>();

    public static final List<AgilityCourse> ALL = load();

    private static List<AgilityCourse> load() {
        List<AgilityCourse> courses = new ArrayList<>();
        List<AgilityObstacle> obstacles = null;
        for (String[] r : DataFiles.rows("agility_courses.tsv")) {
            if ("course".equals(r[0])) {
                obstacles = new ArrayList<>();
                AgilityCourse c = new AgilityCourse(r[1], DataFiles.num(r[2]), DataFiles.dec(r[3]), obstacles);
                courses.add(c);
                if (!DataFiles.col(r, 4).isEmpty()) BY_REGION.put(DataFiles.num(r[4]), c);
                WorldPoint start = DataFiles.point(DataFiles.col(r, 5));
                if (start != null) STARTS.put(c.getName(), start);
            } else if ("obstacle".equals(r[0]) && obstacles != null) {
                obstacles.add(new AgilityObstacle(r[1], DataFiles.point(DataFiles.col(r, 3)), DataFiles.ints(r[2])));
            }
        }
        return Collections.unmodifiableList(courses);
    }

    /** The course a map region belongs to, or null. */
    public static AgilityCourse getCourseForRegion(int regionId) {
        return BY_REGION.get(regionId);
    }

    /** The tile to walk to for a course, or null when none is known. */
    public static WorldPoint startLocation(String courseName) {
        return courseName == null ? null : STARTS.get(courseName);
    }
}
