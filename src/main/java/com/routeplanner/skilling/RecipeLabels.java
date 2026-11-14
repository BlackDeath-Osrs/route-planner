package com.routeplanner.skilling;

import com.routeplanner.agility.*;
import com.routeplanner.model.RouteStep;
import net.runelite.api.coords.WorldPoint;
import java.util.*;

/** The step editor's recipe/method choices per skill, and the targets each one puts on a step. */
public final class RecipeLabels {

    private RecipeLabels() {
    }

    /** The recipe/method choices for a skill ("(none)" first). */
    public static String[] labelsFor(String skill) {
        List<String> out = new ArrayList<>();
        out.add("(none)");
        if (skill == null) return out.toArray(new String[0]);
        switch (skill) {
            case "Smithing": for (SmithingBars.Bar b : SmithingBars.BARS) out.add(b.toString()); break;
            case "Herblore": for (PotionRecipes.Potion p : PotionRecipes.POTIONS) out.add(p.toString()); break;
            case "Fishing": for (FishingMethods.Method m : FishingMethods.METHODS) out.add(m.toString()); break;
            case "Construction": for (ConstructionMethods.Method m : ConstructionMethods.METHODS) out.add(m.toString()); break;
            case "Agility":
                for (AgilityCourse ac : AgilityCoursePresets.ALL) out.add(ac.getName());
                break;
            case "Thieving":
                for (SkillingNpc n : ThievingNpcs.PICKPOCKET_NPCS) out.add(n.toString());
                for (SkillingNpc s : ThievingStalls.STALLS) out.add(s.toString());
                break;
            default:
                for (Recipes.Recipe r : recipes(skill)) out.add(r.toString());
                break;
        }
        return out.toArray(new String[0]);
    }

    /**
     * Put a recipe/method's targets on the step (objects, NPCs, bank items, course start).
     * Clears the old targets first so switching skills doesn't leave stale ones.
     */
    public static void apply(RouteStep step, String skill, String label) {
        step.setSkillingTargetNpc(null);
        step.setSkillingTargetObject(null);
        step.setBankHighlightItems(null);
        step.setHerblorePotion(null);
        boolean none = (label == null || "(none)".equals(label));
        step.setRecipeLabel(none ? null : label);
        if (none) return;
        if (skill == null) return;
        switch (skill) {
            case "Smithing": { SmithingBars.Bar b = SmithingBars.getByLabel(label); if (b != null) step.setBankHighlightItems(b.ores); break; }
            case "Herblore": { step.setHerblorePotion(labelName(label)); break; }
            case "Agility": {
                WorldPoint loc = AgilityCoursePresets.startLocation(label);
                if (loc != null) step.setWorldPoint(loc); // course start -> Location tile
                break;
            }
            case "Thieving": {
                boolean isNpc = false;
                for (SkillingNpc n : ThievingNpcs.PICKPOCKET_NPCS) {
                    if (n.toString().equals(label)) { step.setSkillingTargetNpc(n.getName()); isNpc = true; break; }
                }
                if (!isNpc) {
                    for (SkillingNpc s : ThievingStalls.STALLS) {
                        if (s.toString().equals(label)) { step.setSkillingTargetObject(s.getName()); break; }
                    }
                }
                break;
            }
            default: {
                // Fishing/Construction have no recipe list here: informational only
                Recipes.Recipe r = Recipes.byLabel(recipes(skill), label);
                if (r == null) break;
                if (skill.equals("Mining") || skill.equals("Woodcutting")) step.setSkillingTargetObject(r.target);
                else step.setBankHighlightItems(r.target);
                break;
            }
        }
    }

    private static String labelName(String label) {
        int i = label.indexOf(" (Lv");
        return i > 0 ? label.substring(0, i) : label;
    }

    private static List<Recipes.Recipe> recipes(String skill) {
        switch (skill) {
            case "Cooking": return Recipes.COOKING;
            case "Firemaking": return Recipes.FIREMAKING;
            case "Mining": return Recipes.MINING;
            case "Woodcutting": return Recipes.WOODCUTTING;
            case "Crafting": return Recipes.CRAFTING;
            case "Fletching": return Recipes.FLETCHING;
            default: return java.util.Collections.emptyList();
        }
    }
}
