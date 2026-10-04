package dev.dihclient.port.packets;

import java.util.Arrays;
import java.util.Locale;

/** Ported from a Meteor addon. Pure text logic of {@link PacketLog}: the packet list and the cut of long values. */
public final class PacketFilter {
    private PacketFilter() {
    }

    /** A value longer than {@code max} is cut and ends with "...". */
    public static String cut(String value, int max) {
        return value.length() > max ? value.substring(0, max) + "..." : value;
    }

    /** Comma separated list to lower-case entries; a "minecraft:" prefix is dropped and empty entries are skipped. */
    public static String[] parseList(String raw) {
        return Arrays.stream(raw.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .map(s -> s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    /** An entry matches when the packet name contains it (case-insensitive), so "move" covers every move packet. */
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
