package com.routeplanner.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class QuestOrderTest {

    private static RouteStep step(String name, String check) {
        RouteStep s = new RouteStep(name, StepType.LOCATION, null, -1);
        s.setProgressCheck(check);
        return s;
    }

    private static List<String> names(List<RouteStep> steps) {
        List<String> out = new ArrayList<>();
        steps.forEach(s -> out.add(s.getName()));
        return out;
    }

    @Test
    public void blocksMoveWithTheirStage() {
        List<RouteStep> steps = Arrays.asList(
            step("walk to thug", null),
            step("thug", null), step("operator 17", "varbit 334 >= 17"),
            step("ali", "varbit 334 >= 2"),
            step("walk", null), step("bandit", "varbit 334 >= 3"),
            step("leave", null));
        assertEquals(Arrays.asList("ali", "walk", "bandit", "walk to thug", "thug", "operator 17", "leave"),
            names(QuestOrder.sorted(steps)));
    }

    @Test
    public void otherQuestsAndBitChecksStayPut() {
        List<RouteStep> steps = Arrays.asList(
            step("feud 5", "varbit 334 >= 5"),
            step("dig 3", "varp 131 >= 3"),
            step("diary", "varp 1178 bit 9"),
            step("feud 4", "varbit 334 >= 4"),
            step("dig 2", "varp 131 >= 2"));
        assertEquals(Arrays.asList("feud 4", "dig 2", "diary", "feud 5", "dig 3"),
            names(QuestOrder.sorted(steps)));
    }

    @Test
    public void inOrderOrEqualStagesIsLeftAlone() {
        assertNull(QuestOrder.sorted(Arrays.asList(
            step("a", "varp 131 >= 2"), step("b", null), step("c", "varp 131 >= 2"), step("d", "varp 131 >= 3"))));
    }
}
