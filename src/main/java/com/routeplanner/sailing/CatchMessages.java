package com.routeplanner.sailing;

import net.runelite.client.util.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads deep sea trawling catches from chat (Sailing): "You catch three giant krill." for the
 * player's net and "Jobless Jim catches two giant krill." for a crewmate's. The fish land in the net,
 * not the inventory, and the game doesn't send the net's contents while fishing, so the messages
 * are the only count. Wording as read by Maewi's Deep Sea Trawling plugin.
 */
public final class CatchMessages {

    public static final class Catch {
        public final String fish;
        public final int count;

        Catch(String fish, int count) {
            this.fish = fish;
            this.count = count;
        }
    }

    private static final Pattern CATCH = Pattern.compile("^(?:You catch|.+? catches) (\\S+) (.+?)[.!]?$");
    private static final Map<String, Integer> WORDS = new HashMap<>();
    static {
        String[] words = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"};
        for (int i = 0; i < words.length; i++) WORDS.put(words[i], i);
        WORDS.put("a", 1);
        WORDS.put("an", 1);
    }

    private CatchMessages() {}

    /** The catch in a game message, or null if it isn't one. "Trawler's trust" extras come as their own catch. */
    public static Catch parse(String message) {
        if (message == null) return null;
        String msg = Text.removeTags(message).trim();
        if (msg.contains("Trawler's trust")) return null;
        Matcher m = CATCH.matcher(msg);
        if (!m.matches()) return null;
        String amount = m.group(1).toLowerCase();
        Integer count = WORDS.get(amount);
        if (count == null) {
            try {
                count = Integer.parseInt(amount);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (count <= 0) return null;
        String fish = m.group(2).trim();
        return fish.isEmpty() ? null : new Catch(Character.toUpperCase(fish.charAt(0)) + fish.substring(1), count);
    }

    /** Whether two fish names are the same kind ("Giant krill", "giant krill"; "Haddock", "Haddocks"). */
    public static boolean sameFish(String a, String b) {
        if (a == null || b == null) return false;
        return key(a).equals(key(b));
    }

    private static String key(String fish) {
        String k = fish.trim().toLowerCase();
        return k.endsWith("s") ? k.substring(0, k.length() - 1) : k;
    }
}
