package dev.dihclient.port.donutc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * The game-free parts of Player Detection: who counts, comma lists and the panic pay command.
 */
public final class PlayerDetectionLogic {
    /** Names that are never reported (lower case): the fake player of a free camera mod. */
    public static final Set<String> PERMANENT_WHITELIST = Set.of("freecamera");

    private PlayerDetectionLogic() {
    }

    /** Comma separated names to a set of lower case names; blanks are skipped. */
    public static Set<String> parseNames(String raw) {
        Set<String> parsed = new HashSet<>();
        if (raw == null) {
            return parsed;
        }
        for (String entry : raw.split(",")) {
            String name = entry.trim();
            if (!name.isEmpty()) {
                parsed.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return parsed;
    }

    /** Comma separated entries, trimmed, blanks and repeats (ignoring case) dropped, order kept. */
    public static List<String> parseList(String raw) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (raw == null) {
            return out;
        }
        for (String entry : raw.split(",")) {
            String key = entry.trim();
            if (!key.isEmpty() && seen.add(key.toLowerCase(Locale.ROOT))) {
                out.add(key);
            }
        }
        return out;
    }

    /**
     * The players that count right now: not you (by name), not on the permanent or the user whitelist.
     * @param ignored lower case names
     * @return names in the order given; empty when nobody counts
     */
    public static Set<String> relevant(Collection<String> names, String selfName, Set<String> ignored) {
        Set<String> current = new LinkedHashSet<>();
        for (String name : names) {
            if (name == null || name.equals(selfName)) {
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (!PERMANENT_WHITELIST.contains(key) && !ignored.contains(key)) {
                current.add(name);
            }
        }
        return current;
    }

    /** "/pay target amount" without the slash, or null when a part is empty or has whitespace (it would become another command). */
    public static String payCommand(String target, String amount) {
        String t = target == null ? "" : target.trim();
        String a = amount == null ? "" : amount.trim();
        if (t.isEmpty() || a.isEmpty() || hasSpace(t) || hasSpace(a)) {
            return null;
        }
        return "pay " + t + " " + a;
    }

    private static boolean hasSpace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i)) || Character.isISOControl(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** Short enough for a toast: the names joined, with "+N" for the rest. */
    public static String shortNames(Collection<String> names, int max) {
        List<String> all = new ArrayList<>(names);
        if (all.size() <= max) {
            return String.join(", ", all);
        }
        return String.join(", ", all.subList(0, max)) + " +" + (all.size() - max);
    }
}
