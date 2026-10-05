package dev.dihclient.port.staff;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Ported from an open-source client (GPL-3.0). */
public final class StaffNames {
    public static final List<String> DONUT_STAFF = List.of(
            "FluffyMaster07", "archivePedro", "Munkerlich", "Frenk_Btw", "Napooo_", "auzzitech", "CryptoDaveYT", "W1zox_",
            "ZEEF69", "showered", "CaptainMoose35", "BobIsFound", "NoahvdAa", "0GSummer", "LzouzMP5", "Pastagamer08",
            "u_vv", "Owen1212055", "Splaterd", "Fallerfly");

    private static final Set<String> KEYS = keys();

    private static final Pattern ACCOUNT_NAME = Pattern.compile("[.*]?[A-Za-z0-9_]{1,16}");

    private StaffNames() {
    }

    private static Set<String> keys() {
        Set<String> out = new HashSet<>();
        for (String name : DONUT_STAFF) {
            out.add(name.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(out);
    }

    public static boolean isStaffName(String name) {
        return name != null && KEYS.contains(name.toLowerCase(Locale.ROOT));
    }

    public static boolean isAccount(UUID id, String name) {
        return name != null && id.version() != 2 && ACCOUNT_NAME.matcher(name).matches();
    }

    public static String formatAge(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        long hours = total / 3600L;
        long minutes = total / 60L % 60L;
        long seconds = total % 60L;
        StringBuilder out = new StringBuilder(8);
        if (hours > 0L) {
            out.append(hours).append(minutes < 10L ? ":0" : ":");
        }
        return out.append(minutes).append(seconds < 10L ? ":0" : ":").append(seconds).toString();
    }
}
