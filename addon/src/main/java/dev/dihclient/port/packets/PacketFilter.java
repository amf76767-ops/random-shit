package dev.dihclient.port.packets;

import java.util.Arrays;
import java.util.Locale;

public final class PacketFilter {
    private PacketFilter() {
    }

    public static String cut(String value, int max) {
        return value.length() > max ? value.substring(0, max) + "..." : value;
    }

    public static String[] parseList(String raw) {
        return Arrays.stream(raw.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .map(s -> s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    public static boolean matches(String name, String[] entries) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String entry : entries) {
            if (lower.contains(entry)) {
                return true;
            }
        }
        return false;
    }
}
