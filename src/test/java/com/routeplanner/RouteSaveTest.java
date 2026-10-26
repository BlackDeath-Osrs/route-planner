package com.routeplanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.routeplanner.model.ItemMode;
import com.routeplanner.model.Route;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.SkillGoalType;
import com.routeplanner.model.StepType;
import com.routeplanner.util.RouteSerializer;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;

public class RouteSaveTest {

    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(WorldPoint.class, new RouteSerializer.WorldPointAdapter())
        .create();

    @Test
    public void emptySaveIsNoRoutes() {
        assertTrue(RouteSerializer.fromJson(null, gson).isEmpty());
        assertTrue(RouteSerializer.fromJson("", gson).isEmpty());
    }

    @Test(expected = RuntimeException.class)
    public void unreadableSaveThrowsInsteadOfLookingEmpty() {
        RouteSerializer.fromJson("[{\"name\": \"Broken\", \"sections\": [", gson);
    }

    @Test
    public void routesSurviveARoundTrip() {
        Route route = new Route("Test");
        route.addStepToLastSection(new RouteStep("Walk", StepType.LOCATION, new WorldPoint(3200, 3200, 0), -1));
        String json = RouteSerializer.toJson(Collections.singletonList(route), gson);
        List<Route> back = RouteSerializer.fromJson(json, gson);
        assertEquals(1, back.size());
        assertEquals("Walk", back.get(0).getSteps().get(0).getName());
        assertEquals(new WorldPoint(3200, 3200, 0), back.get(0).getSteps().get(0).getWorldPoint());
    }

    @Test
    public void oldSavesLoadTheirModesAsEnums() {
        // Saved before the enums: the same names as plain strings, and a step without itemMode.
        String json = "[{\"name\":\"Old\",\"sections\":[{\"id\":\"s1\",\"name\":\"Steps\",\"steps\":["
            + "{\"name\":\"Buy\",\"itemMode\":\"SHOP\",\"itemList\":\"Spade\"},"
            + "{\"name\":\"Train\",\"skillingSkill\":\"WOODCUTTING\",\"skillingGoalType\":\"XP_GAIN\"},"
            + "{\"name\":\"Bring\",\"itemList\":\"Spade\"}]}]}]";
        List<RouteStep> steps = RouteSerializer.fromJson(json, gson).get(0).getSteps();
        assertEquals(ItemMode.SHOP, steps.get(0).getItemMode());
        assertTrue(steps.get(0).countsGainedItems());
        assertEquals(SkillGoalType.XP_GAIN, steps.get(1).getSkillingGoalType());
        assertEquals(Skill.WOODCUTTING, steps.get(1).skill());
        assertNull(steps.get(2).getItemMode()); // left out: treated as BANK everywhere

        // And they save back under the same names, so older plugin versions can still read them.
        String saved = RouteSerializer.toJson(RouteSerializer.fromJson(json, gson), gson);
        assertTrue(saved.contains("\"itemMode\":\"SHOP\""));
        assertTrue(saved.contains("\"skillingGoalType\":\"XP_GAIN\""));
    }

    @Test
    public void legacyAgilityStepsGetALevelGoal() {
        String json = "[{\"name\":\"Old\",\"steps\":[{\"agilityCourse\":\"Gnome Stronghold\",\"agilityGoalValue\":10}]}]";
        RouteStep step = RouteSerializer.fromJson(json, gson).get(0).getSteps().get(0);
        assertEquals(SkillGoalType.LEVEL, step.getSkillingGoalType());
        assertEquals("AGILITY", step.getSkillingSkill());
    }

    @Test
    public void resettingARouteClearsXpGainProgress() {
        Route route = new Route("Test");
        RouteStep step = new RouteStep("Cut logs", "WOODCUTTING", SkillGoalType.XP_GAIN, 1000, 5000);
        step.setSkillingProgress(400);
        step.setCompleted(true);
        route.addStepToLastSection(step);

        route.resetProgress();

        assertFalse(step.isCompleted());
        assertEquals(0, step.getSkillingProgress());
        assertEquals(0, step.getSkillingStartXp());
    }

    @Test
    public void areasSurviveARoundTrip() {
        Route route = new Route("Test");
        RouteStep step = new RouteStep("Talk to Hans", StepType.LOCATION, new WorldPoint(3221, 3219, 0), -1);
        step.setAreaFrom(new WorldPoint(3200, 3200, 0));
        step.setAreaTo(new WorldPoint(3245, 3235, 0));
        route.addStepToLastSection(step);

        String json = RouteSerializer.toJson(Collections.singletonList(route), gson);
        RouteStep back = RouteSerializer.fromJson(json, gson).get(0).getSteps().get(0);
        assertTrue(back.hasArea());
        assertEquals(step.area().getWidth(), back.area().getWidth());
        assertEquals(new WorldPoint(3200, 3200, 0), back.getAreaFrom());

        // Older saves have no area: they keep the usual arrival around the tile
        String old = "[{\"name\":\"Old\",\"sections\":[{\"id\":\"s1\",\"name\":\"Steps\",\"steps\":[{\"name\":\"Walk\"}]}]}]";
        assertFalse(RouteSerializer.fromJson(old, gson).get(0).getSteps().get(0).hasArea());
    }
}
