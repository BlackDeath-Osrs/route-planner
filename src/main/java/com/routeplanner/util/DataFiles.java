package com.routeplanner.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Reads the plugin's tab-separated data files (blank lines and "#" comments are skipped). */
@Slf4j
public final class DataFiles {

    private DataFiles() {}

    /** The rows of a file under /com/routeplanner/data/, each split on tabs. */
    public static List<String[]> rows(String file) {
        return rowsAt("/com/routeplanner/data/" + file);
    }

    /** The rows of any resource, by its full path. Missing files read as empty. */
    public static List<String[]> rowsAt(String path) {
        List<String[]> out = new ArrayList<>();
        try (InputStream is = DataFiles.class.getResourceAsStream(path)) {
            if (is == null) {
                log.warn("Route Planner: {} missing", path);
                return out;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                out.add(line.split("\t", -1));
            }
        } catch (IOException e) {
            log.warn("Route Planner: couldn't read {}", path, e);
        }
        return out;
    }

    /** Every row of a file under /com/routeplanner/data/, made into an entry. */
    public static <T> List<T> load(String file, Function<String[], T> entry) {
        List<T> out = new ArrayList<>();
        for (String[] r : rows(file)) out.add(entry.apply(r));
        return Collections.unmodifiableList(out);
    }

    /** "x y plane" as a tile; null if blank or not three numbers. */
    public static WorldPoint point(String s) {
        if (s == null) return null;
        String[] p = s.trim().split("\\s+");
        if (p.length != 3) return null;
        try {
            return new WorldPoint(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "1,2,3" as numbers. */
    public static int[] ints(String s) {
        String[] p = s.split(",");
        int[] out = new int[p.length];
        for (int i = 0; i < p.length; i++) out[i] = Integer.parseInt(p[i].trim());
        return out;
    }

    public static int num(String s) {
        return Integer.parseInt(s.trim());
    }

    public static double dec(String s) {
        return Double.parseDouble(s.trim());
    }

    /** The column, or "" past the row's end (trailing blank columns may be left off). */
    public static String col(String[] r, int i) {
        return i < r.length ? r[i] : "";
    }
}
