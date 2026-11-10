package com.routeplanner.record;

import com.routeplanner.model.ItemMode;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.TileArea;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.BiConsumer;

/**
 * Lines a section's steps up against a fresh recording of it. Steps are paired in order by how much
 * they have in common (NPC, object, tile, teleport, items, skill, and whether the route ticked the
 * step off around the time the recorded step was made). A paired step lists the fields the
 * recording would fill in or change; recorded steps with no partner are new; section steps with no
 * partner were either ticked off anyway (unchanged) or not seen at all.
 */
public final class SectionDiff {

    public enum Kind { SAME, CHANGED, NEW, UNSEEN }

    /** One field the recording has a different value for. Applying copies it onto the step. */
    public static final class FieldChange {
        public final String label;
        public final String oldText;
        public final String newText;
        /** The step had nothing here, so taking the recorded value loses nothing. */
        public final boolean fill;
        private final BiConsumer<RouteStep, RouteStep> copy;

        FieldChange(String label, String oldText, String newText, BiConsumer<RouteStep, RouteStep> copy) {
            this.label = label;
            this.oldText = oldText;
            this.newText = newText;
            this.fill = oldText == null;
            this.copy = copy;
        }

        public void applyTo(RouteStep step, RouteStep recorded) {
            copy.accept(step, recorded);
        }
    }

    public static final class Row {
        public final Kind kind;
        public final RouteStep step;     // the section's step (null for NEW)
        public final RouteStep recorded; // the recorded step (null for SAME-without-partner and UNSEEN)
        public final List<FieldChange> changes;

        Row(Kind kind, RouteStep step, RouteStep recorded, List<FieldChange> changes) {
            this.kind = kind;
            this.step = step;
            this.recorded = recorded;
            this.changes = changes;
        }
    }

    /** Below this a pair isn't trusted; the two are shown as one unseen and one new step. */
    static final int MATCH_THRESHOLD = 3;

    private SectionDiff() {}

    /**
     * @param tickedAt section steps the route ticked off while recording, with the number of steps
     *                 recorded at that moment
     */
    public static List<Row> compare(List<RouteStep> section, List<RouteStep> recorded, Map<RouteStep, Integer> tickedAt) {
        int n = section.size(), m = recorded.size();
        int[][] score = new int[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) score[i][j] = similarity(section.get(i), recorded.get(j), tickedAt.get(section.get(i)), j);
        }
        // Best order-keeping pairing (like a diff, weighted by similarity)
        int[][] best = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                int b = Math.max(best[i + 1][j], best[i][j + 1]);
                if (score[i][j] >= MATCH_THRESHOLD) b = Math.max(b, best[i + 1][j + 1] + score[i][j]);
                best[i][j] = b;
            }
        }
        List<Row> rows = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && score[i][j] >= MATCH_THRESHOLD && best[i][j] == best[i + 1][j + 1] + score[i][j]) {
                List<FieldChange> ch = changes(section.get(i), recorded.get(j));
                rows.add(new Row(ch.isEmpty() ? Kind.SAME : Kind.CHANGED, section.get(i), recorded.get(j), ch));
                i++;
                j++;
            } else if (i < n && (j >= m || best[i][j] == best[i + 1][j])) {
                RouteStep s = section.get(i++);
                rows.add(new Row(tickedAt.containsKey(s) ? Kind.SAME : Kind.UNSEEN, s, null, Collections.emptyList()));
            } else {
                rows.add(new Row(Kind.NEW, null, recorded.get(j++), Collections.emptyList()));
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------ pairing

    static int similarity(RouteStep a, RouteStep b, Integer tickedAtCount, int recordedIndex) {
        int s = 0;
        String npcA = npc(a), npcB = npc(b);
        if (npcA != null && npcA.equalsIgnoreCase(npcB)) s += 3;
        if (notBlank(a.getSkillingTargetObject()) && a.getSkillingTargetObject().equalsIgnoreCase(b.getSkillingTargetObject())) s += 3;
        if (a.getTeleportMethod() != null && Objects.equals(teleportKey(a), teleportKey(b))) s += 3;
        if (notBlank(a.getName()) && a.getName().trim().equalsIgnoreCase(b.getName() == null ? "" : b.getName().trim())) s += 3;
        if (a.getWorldPoint() != null && b.getWorldPoint() != null) {
            WorldPoint p = a.getWorldPoint(), q = b.getWorldPoint();
            int d = p.getPlane() == q.getPlane() ? Math.max(Math.abs(p.getX() - q.getX()), Math.abs(p.getY() - q.getY())) : Integer.MAX_VALUE;
            TileArea area = a.area();
            if (d <= 3 || (area != null && area.contains(q))) s += 2;
            else if (d <= 8) s += 1;
            else if (d > 30) s -= 3;
        }
        if (a.getItemMode() == b.getItemMode() && notBlank(a.getItemList()) && notBlank(b.getItemList())) {
            TreeSet<String> x = itemNames(a.getItemList());
            x.retainAll(itemNames(b.getItemList()));
            if (!x.isEmpty()) s += 2;
        }
        if (a.hasSkillGoal() && b.hasSkillGoal() && a.getSkillingSkill().equalsIgnoreCase(b.getSkillingSkill())) s += 2;
        // The route ticked this step off just as this step was recorded (or just before/after)
        if (tickedAtCount != null && recordedIndex >= tickedAtCount - 2 && recordedIndex <= tickedAtCount) s += 2;
        return s;
    }

    // ------------------------------------------------------------------ fields

    private static List<FieldChange> changes(RouteStep step, RouteStep rec) {
        List<FieldChange> out = new ArrayList<>();
        // Tile: close enough (or inside the step's area) counts as the same place
        if (rec.getWorldPoint() != null && !samePlace(step, rec.getWorldPoint())) {
            add(out, "Tile", tile(step.getWorldPoint()), tile(rec.getWorldPoint()), (s, r) -> s.setWorldPoint(r.getWorldPoint()));
        }
        if (rec.getTransitionPoint() != null && (step.getTransitionPoint() == null
            || step.getTransitionPoint().distanceTo(rec.getTransitionPoint()) > 2)) {
            add(out, "Stairs", tile(step.getTransitionPoint()), tile(rec.getTransitionPoint()), (s, r) -> {
                s.setTransitionPoint(r.getTransitionPoint());
                s.setTransitionObjectId(r.getTransitionObjectId());
            });
        }
        if (rec.getTeleportMethod() != null && !Objects.equals(teleportKey(step), teleportKey(rec))) {
            add(out, "Teleport", teleportText(step), teleportText(rec), (s, r) -> {
                s.setTeleportMethod(r.getTeleportMethod());
                s.setTeleportSpell(r.getTeleportSpell());
                s.setTeleportSpellId(r.getTeleportSpellId());
                s.setTeleportItem(r.getTeleportItem());
                s.setTeleportNexus(r.getTeleportNexus());
                s.setTeleportDestination(r.getTeleportDestination());
            });
        }
        if (notBlank(rec.getDialogOptions()) && !compact(rec.getDialogOptions()).equals(compact(step.getDialogOptions()))) {
            add(out, "Dialog options", blankToNull(step.getDialogOptions()), rec.getDialogOptions(),
                (s, r) -> s.setDialogOptions(r.getDialogOptions()));
        }
        if (rec.hasItems() && (mode(step) != mode(rec) || !itemNames(step.getItemList()).equals(itemNames(rec.getItemList())))) {
            add(out, "Items", step.hasItems() ? itemsText(step) : null, itemsText(rec), (s, r) -> {
                s.setItemMode(r.getItemMode());
                s.setItemList(r.getItemList());
                s.setPickupBaseline(null);
            });
        }
        String npc = npc(rec);
        if (npc != null && !npc.equalsIgnoreCase(npc(step))) {
            add(out, "NPC", npc(step), npc, (s, r) -> {
                String v = npc(r);
                if (notBlank(s.getNpcHighlight()) || (!notBlank(s.getSkillingTargetNpc()) && notBlank(r.getNpcHighlight()))) s.setNpcHighlight(v);
                else s.setSkillingTargetNpc(v);
                s.setHighlightEnabled(true);
            });
        }
        if (notBlank(rec.getSkillingTargetObject()) && !rec.getSkillingTargetObject().equalsIgnoreCase(step.getSkillingTargetObject())) {
            add(out, "Object", blankToNull(step.getSkillingTargetObject()), rec.getSkillingTargetObject(), (s, r) -> {
                s.setSkillingTargetObject(r.getSkillingTargetObject());
                s.setHighlightEnabled(true);
            });
        }
        if (rec.getNpcKillCount() > 0 && rec.getNpcKillCount() != step.getNpcKillCount()) {
            add(out, "Kills", step.getNpcKillCount() > 0 ? String.valueOf(step.getNpcKillCount()) : null,
                String.valueOf(rec.getNpcKillCount()), (s, r) -> s.setNpcKillCount(r.getNpcKillCount()));
        }
        // Fill only: a recorded XP gain, quest, boat or note doesn't replace one the step was given
        if (rec.hasSkillGoal() && !step.hasSkillGoal()) {
            add(out, "Skill goal", null, skillText(rec), (s, r) -> {
                s.setSkillingSkill(r.getSkillingSkill());
                s.setSkillingGoalType(r.getSkillingGoalType());
                s.setSkillingGoalValue(r.getSkillingGoalValue());
                s.setSkillingStartXp(0);
                s.setSkillingProgress(0);
            });
        }
        if (rec.hasQuest() && !step.hasQuest()) {
            add(out, "Quest", null, rec.getQuestName(), (s, r) -> s.setQuestName(r.getQuestName()));
        }
        if (rec.hasBoarding() && !step.hasBoarding()) {
            add(out, "Boat", null, tile(rec.getBoardPoint()), (s, r) -> {
                s.setBoardPoint(r.getBoardPoint());
                s.setBoardObjectId(r.getBoardObjectId());
            });
        }
        if (rec.hasFarmPatch() && !step.hasFarmPatch()) {
            add(out, "Farming patch", null, "patch " + rec.getFarmPatchId(), (s, r) -> {
                s.setFarmPatchId(r.getFarmPatchId());
                s.setFarmReplant(r.isFarmReplant());
            });
        }
        if (rec.hasNote() && !step.hasNote()) {
            add(out, "Note", null, rec.getNoteText(), (s, r) -> s.setNoteText(r.getNoteText()));
        }
        return out;
    }

    private static void add(List<FieldChange> out, String label, String oldText, String newText, BiConsumer<RouteStep, RouteStep> copy) {
        out.add(new FieldChange(label, oldText, newText, copy));
    }

    private static boolean samePlace(RouteStep step, WorldPoint q) {
        WorldPoint p = step.getWorldPoint();
        TileArea area = step.area();
        if (area != null && area.contains(q)) return true;
        return p != null && p.getPlane() == q.getPlane()
            && Math.max(Math.abs(p.getX() - q.getX()), Math.abs(p.getY() - q.getY())) <= 3;
    }

    /** The NPC a step talks to, kills or highlights. */
    static String npc(RouteStep s) {
        if (notBlank(s.getSkillingTargetNpc())) return s.getSkillingTargetNpc().trim();
        if (notBlank(s.getNpcHighlight())) return s.getNpcHighlight().trim();
        return null;
    }

    private static String teleportKey(RouteStep s) {
        if (s.getTeleportMethod() == null) return null;
        return s.getTeleportMethod() + "|" + lower(s.getTeleportSpell()) + "|" + lower(s.getTeleportItem()) + "|" + lower(s.getTeleportNexus());
    }

    private static String teleportText(RouteStep s) {
        if (s.getTeleportMethod() == null) return null;
        return first(s.getTeleportSpell(), s.getTeleportItem(), s.getTeleportNexus(), s.getTeleportMethod());
    }

    private static ItemMode mode(RouteStep s) {
        return s.getItemMode() == null ? ItemMode.BANK : s.getItemMode();
    }

    private static String itemsText(RouteStep s) {
        String mode = mode(s).name().charAt(0) + mode(s).name().substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        return notBlank(s.getItemList()) ? mode + ": " + s.getItemList() : mode;
    }

    private static String skillText(RouteStep s) {
        String skill = s.getSkillingSkill().charAt(0) + s.getSkillingSkill().substring(1).toLowerCase(Locale.ROOT);
        return skill + " " + (s.getSkillingGoalType() == null ? "" : s.getSkillingGoalType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " ")
            + s.getSkillingGoalValue();
    }

    /** "3 Ball of wool, Coins" -> {ball of wool, coins}: what's on the list, not how many. */
    static TreeSet<String> itemNames(String list) {
        TreeSet<String> out = new TreeSet<>();
        if (list == null) return out;
        for (String part : list.split(",")) {
            String t = part.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\d+\\s*x?\\s+", "");
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static String tile(WorldPoint p) {
        return p == null ? null : p.getX() + ", " + p.getY() + ", " + p.getPlane();
    }

    private static String compact(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

    private static String blankToNull(String s) {
        return notBlank(s) ? s : null;
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String first(String... vals) {
        return Arrays.stream(vals).filter(SectionDiff::notBlank).findFirst().orElse(null);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
