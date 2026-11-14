package com.routeplanner.model;

import lombok.RequiredArgsConstructor;
import java.util.Locale;
import java.util.regex.*;

/**
 * A value the game keeps that shows a step is done, written the way it's saved on the step:
 * <ul>
 *   <li>{@code "varbit 3185 >= 4"}: a quest has reached stage 4 (quests keep their stage in a
 *   varbit or a varplayer).</li>
 *   <li>{@code "varp 107 >= 5"}: the same, for a quest kept in a varplayer.</li>
 *   <li>{@code "varp 1194 bit 6"}: an achievement diary task (each region's tasks are bits of one
 *   varplayer).</li>
 * </ul>
 */
@RequiredArgsConstructor
public final class ProgressCheck {

    public enum Kind { VARBIT, VARP }

    private static final Pattern AT_LEAST = Pattern.compile("^(varbit|varp)\\s+(\\d+)\\s*>=\\s*(\\d+)$");
    private static final Pattern BIT = Pattern.compile("^varp\\s+(\\d+)\\s+bit\\s+(\\d{1,2})$");

    public final Kind kind;
    public final int id;
    /** The bit that must be set (0-31), or -1 to compare the whole value. */
    public final int bit;
    /** The value the var must reach, when not a bit. */
    public final int atLeast;

    /** The check written in {@code text}, or null if it isn't one. */
    public static ProgressCheck parse(String text) {
        if (text == null) return null;
        String t = text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        try {
            Matcher m = AT_LEAST.matcher(t);
            if (m.matches()) {
                return new ProgressCheck(m.group(1).equals("varbit") ? Kind.VARBIT : Kind.VARP,
                    Integer.parseInt(m.group(2)), -1, Integer.parseInt(m.group(3)));
            }
            m = BIT.matcher(t);
            if (m.matches()) {
                int bit = Integer.parseInt(m.group(2));
                return bit < 32 ? new ProgressCheck(Kind.VARP, Integer.parseInt(m.group(1)), bit, 0) : null;
            }
        } catch (NumberFormatException e) {
            return null; // a number too big for an int
        }
        return null;
    }

    /** Whether the var's current value meets the check. */
    public boolean isMet(int value) {
        return bit >= 0 ? (value & (1 << bit)) != 0 : value >= atLeast;
    }

    @Override
    public String toString() {
        String var = (kind == Kind.VARBIT ? "varbit " : "varp ") + id;
        return bit >= 0 ? var + " bit " + bit : var + " >= " + atLeast;
    }
}
