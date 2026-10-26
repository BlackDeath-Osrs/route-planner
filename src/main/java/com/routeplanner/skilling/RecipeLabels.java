package com.routeplanner.skilling;

import com.routeplanner.agility.AgilityCourse;
import com.routeplanner.agility.AgilityCoursePresets;
import com.routeplanner.model.RouteStep;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;

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
            case "Cooking": for (CookingFoods.Food f : CookingFoods.FOODS) out.add(f.toString()); break;
            case "Smithing": for (SmithingBars.Bar b : SmithingBars.BARS) out.add(b.toString()); break;
            case "Firemaking": for (FiremakingLogs.Log l : FiremakingLogs.LOGS) out.add(l.toString()); break;
            case "Mining": for (MiningRocks.Rock r : MiningRocks.ROCKS) out.add(r.toString()); break;
            case "Woodcutting": for (WoodcuttingTrees.Tree t : WoodcuttingTrees.TREES) out.add(t.toString()); break;
            case "Herblore": for (PotionRecipes.Potion p : PotionRecipes.POTIONS) out.add(p.toString()); break;
            case "Fishing": for (FishingMethods.Method m : FishingMethods.METHODS) out.add(m.toString()); break;
            case "Construction": for (ConstructionMethods.Method m : ConstructionMethods.METHODS) out.add(m.toString()); break;
            case "Crafting":
                for (CraftingData.Item i : CraftingData.JEWELRY) out.add(i.toString());
                for (CraftingData.Item i : CraftingData.GLASS) out.add(i.toString());
                for (CraftingData.Item i : CraftingData.LEATHER) out.add(i.toString());
                break;
            case "Fletching":
                for (FletchingData.Item i : FletchingData.AMMO) out.add(i.toString());
                for (FletchingData.Item i : FletchingData.BOWS) out.add(i.toString());
                break;
            case "Agility":
                for (AgilityCourse ac : AgilityCoursePresets.ALL) out.add(ac.getName());
                break;
            case "Thieving":
                for (SkillingNpc n : ThievingNpcs.PICKPOCKET_NPCS) out.add(n.toString());
                for (SkillingNpc s : ThievingStalls.STALLS) out.add(s.toString());
                break;
            default: break;
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
            case "Cooking": { CookingFoods.Food f = CookingFoods.getByLabel(label); if (f != null) step.setBankHighlightItems(f.rawItem); break; }
            case "Smithing": { SmithingBars.Bar b = SmithingBars.getByLabel(label); if (b != null) step.setBankHighlightItems(b.ores); break; }
            case "Firemaking": { FiremakingLogs.Log l = FiremakingLogs.getByLabel(label); if (l != null) step.setBankHighlightItems(l.item); break; }
            case "Mining": { MiningRocks.Rock r = MiningRocks.getByLabel(label); if (r != null) step.setSkillingTargetObject(r.objectName); break; }
            case "Woodcutting": { WoodcuttingTrees.Tree t = WoodcuttingTrees.getByLabel(label); if (t != null) step.setSkillingTargetObject(t.objectName); break; }
            case "Herblore": { step.setHerblorePotion(labelName(label)); break; }
            case "Crafting": { String mats = craftingMaterials(label); if (mats != null) step.setBankHighlightItems(mats); break; }
            case "Fletching": { String mats = fletchingMaterials(label); if (mats != null) step.setBankHighlightItems(mats); break; }
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
            default: break; // Fishing/Construction: informational only (use Items component to bring gear)
        }
    }

    private static String labelName(String label) {
        int i = label.indexOf(" (Lv");
        return i > 0 ? label.substring(0, i) : label;
    }

    private static String craftingMaterials(String label) {
        for (CraftingData.Item i : CraftingData.JEWELRY) if (i.toString().equals(label)) return i.materials;
        for (CraftingData.Item i : CraftingData.GLASS) if (i.toString().equals(label)) return i.materials;
        for (CraftingData.Item i : CraftingData.LEATHER) if (i.toString().equals(label)) return i.materials;
        return null;
    }

    private static String fletchingMaterials(String label) {
        for (FletchingData.Item i : FletchingData.AMMO) if (i.toString().equals(label)) return i.materials;
        for (FletchingData.Item i : FletchingData.BOWS) if (i.toString().equals(label)) return i.materials;
        return null;
    }
}
