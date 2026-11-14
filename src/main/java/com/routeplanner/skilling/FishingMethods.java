package com.routeplanner.skilling;

import lombok.RequiredArgsConstructor;
import com.routeplanner.util.DataFiles;
import java.util.*;

public class FishingMethods {

    @RequiredArgsConstructor
    public static class Method {
        public final String name;
        public final String gear;     // items to bring (comma separated)
        public final String catches;  // example fish, shown in the picker
        @Override public String toString() { return name + " \u2014 " + catches; }
    }

    public static final List<Method> METHODS = DataFiles.load("fishing.tsv", r -> new Method(r[0], r[1], r[2]));

    public static Method getByLabel(String label) {
        return METHODS.stream().filter(m -> m.toString().equals(label)).findFirst().orElse(null);
    }
}
