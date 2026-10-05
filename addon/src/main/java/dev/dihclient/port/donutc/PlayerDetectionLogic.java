package dev.dihclient.port.donutc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Ported from an open-source client (GPL-3.0). */
public final class PlayerDetectionLogic {

    public static final Set<String> PERMANENT_WHITELIST = Set.of("freecamera");

    private PlayerDetectionLogic() {
    }

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

    public static String shortNames(Collection<String> names, int max) {
        List<String> all = new ArrayList<>(names);
        if (all.size() <= max) {
            return String.join(", ", all);
        }
        return String.join(", ", all.subList(0, max)) + " +" + (all.size() - max);
    }
}
