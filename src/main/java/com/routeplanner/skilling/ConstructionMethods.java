package com.routeplanner.skilling;

import lombok.RequiredArgsConstructor;
import com.routeplanner.util.DataFiles;
import java.util.*;

public class ConstructionMethods {

    @RequiredArgsConstructor
    public static class Method {
        public final String name;
        public final String gear;
        @Override public String toString() { return name; }
    }

    public static final List<Method> METHODS = DataFiles.load("construction.tsv", r -> new Method(r[0], r[1]));

    public static Method getByLabel(String label) {
        return METHODS.stream().filter(m -> m.name.equals(label)).findFirst().orElse(null);
    }
}
