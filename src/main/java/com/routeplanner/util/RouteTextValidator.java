package com.routeplanner.util;

import java.util.regex.Pattern;

/**
 * Validates user-entered route text. Its main job is to keep links out of any free-text field --
 * route names, section names, step names, and note text -- so shared routes cannot be used to point
 * players at external sites. Enforced at creation time (the step editor) and again at import time
 * (the Route Hub), because imported JSON cannot be assumed to have passed through the editor.
 *
 * <p>The link check is deliberately moderate: it catches obvious URLs and common chat-invite shapes,
 * not exotic obfuscation. Determined obfuscation is caught by human review of Route Hub submissions
 * and by the standing "community content" warning, not by regex.
 */
public final class RouteTextValidator {

    private RouteTextValidator() {}

    // http:// or https:// followed by something
    private static final Pattern SCHEME = Pattern.compile("(?i)\\bhttps?://\\S+");
    // www.something
    private static final Pattern WWW = Pattern.compile("(?i)\\bwww\\.\\S+");
    // a bare domain like example.com / foo.gg / bar.io, with a known-ish TLD, optionally with a path.
    // Kept to a curated TLD list so ordinary text ("meet at lvl.99", "1.5m gp") does not trip it.
    private static final Pattern BARE_DOMAIN = Pattern.compile(
        "(?i)\\b[a-z0-9][a-z0-9-]{0,62}\\.(?:com|net|org|io|gg|gved|co|me|tv|xyz|link|site|shop|store|"
        + "info|app|dev|discord|invite)\\b(?:/\\S*)?");
    // explicit well-known invite/link shapes
    private static final Pattern KNOWN = Pattern.compile(
        "(?i)\\b(?:discord\\.gg|discord\\.com/invite|discordapp\\.com/invite|t\\.me|bit\\.ly|"
        + "tinyurl\\.com|youtu\\.be)\\b");

    /** @return true if the text appears to contain a web link or invite. */
    public static boolean containsLink(String text) {
        if (text == null || text.isEmpty()) return false;
        return SCHEME.matcher(text).find()
            || WWW.matcher(text).find()
            || KNOWN.matcher(text).find()
            || BARE_DOMAIN.matcher(text).find();
    }

    /**
     * Check a single field. Returns null if OK, or a short user-facing error message if not.
     * @param label human name of the field, used in the message (e.g. "Step name", "Note")
     */
    public static String checkField(String label, String text) {
        if (containsLink(text)) {
            return label + " can't contain a web link. Please remove it.";
        }
        return null;
    }
}
