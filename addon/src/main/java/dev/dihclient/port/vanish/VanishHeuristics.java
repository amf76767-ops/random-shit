package dev.dihclient.port.vanish;

import java.util.Locale;
import java.util.Map;

/** Ported from an open-source client (GPL-3.0). */
public final class VanishHeuristics {
    private VanishHeuristics() {
    }

    public static boolean suspiciousSound(String id) {
        String path = path(id);
        return path.contains("chest.open")
                || path.contains("chest.close")
                || path.contains("chest.locked")
                || path.contains("barrel.open")
                || path.contains("barrel.close")
                || path.contains("shulker_box.open")
                || path.contains("shulker_box.close")
                || path.contains("door.open")
                || path.contains("door.close")
                || path.contains("fence_gate.open")
                || path.contains("fence_gate.close")
                || path.contains("button.click")
                || path.contains("lever.click");
    }

    public static boolean suspiciousParticle(String id) {
        String path = path(id);
        return path.contains("crit") || path.contains("enchanted_hit") || path.contains("damage_indicator") || path.contains("smoke") || path.equals("block");
    }

    public static String path(String id) {
        if (id == null) {
            return "unknown";
        }
        int split = id.indexOf(':');
        return (split >= 0 && split + 1 < id.length() ? id.substring(split + 1) : id).toLowerCase(Locale.ROOT);
    }

    public static boolean isContainerSignal(String id) {
        String path = path(id);
        return path.contains("chest") || path.contains("barrel") || path.contains("shulker");
    }

    public static boolean doorLike(String id) {
        String path = path(id);
        return path.contains("door") && !path.contains("trapdoor") || path.contains("fence_gate");
    }

    public static boolean ambientSmokeBlock(String blockPath) {
        String path = path(blockPath);
        return path.contains("campfire")
                || path.contains("furnace")
                || path.contains("smoker")
                || path.contains("torch")
                || path.contains("fire")
                || path.contains("candle")
                || path.contains("respawn_anchor");
    }

    public static boolean weakParticle(String particlePath) {
        return particlePath.equals("block") || particlePath.contains("smoke");
    }

    public static int silentTabRemovalScore(boolean serverSendsLeaveMessages) {
        return serverSendsLeaveMessages ? 100 : 30;
    }

    public static boolean tabDepartureReason(String reason) {
        if (reason == null) {
            return false;
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.startsWith("vanish event:") && (lower.contains("tab") || lower.contains("entity remained") || lower.contains("no leave packet"));
    }

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) | ((long) z & 0x3FFFFFFL) << 12;
    }

    public static void markSelfFootprint(int x, int y, int z, String itemPath, Map<Long, Long> targets, long until) {
        if (targets == null) {
            return;
        }
        targets.put(pack(x, y, z), until);
        String id = itemPath == null ? "" : itemPath;
        boolean vertical = id.endsWith("_door") && !id.endsWith("trapdoor")
                || id.contains("sunflower")
                || id.contains("lilac")
                || id.contains("rose_bush")
                || id.contains("peony")
                || id.contains("tall_grass")
                || id.contains("large_fern")
                || id.contains("pitcher_plant");
        if (vertical) {
            targets.put(pack(x, y + 1, z), until);
            targets.put(pack(x, y - 1, z), until);
        }
        if (id.endsWith("_bed")) {
            targets.put(pack(x + 1, y, z), until);
            targets.put(pack(x - 1, y, z), until);
            targets.put(pack(x, y, z + 1), until);
            targets.put(pack(x, y, z - 1), until);
        }
    }

    public static String compass(double dx, double dz) {
        String ns = dz < -1.0 ? "N" : (dz > 1.0 ? "S" : "");
        String ew = dx > 1.0 ? "E" : (dx < -1.0 ? "W" : "");
        return ns + ew;
    }

    public static String located(double dx, double dy, double dz) {
        long dist = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
        String dir = compass(dx, dz);
        return dir.isEmpty() ? dist + "m" : dist + "m " + dir;
    }

    public static String tag(String name, String reason) {
        if ("CRITICAL".equalsIgnoreCase(name)) {
            return "ALERT";
        }
        String lower = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        if (lower.startsWith("vanish event")) {
            return "VANISH";
        } else if (lower.startsWith("invisible entity")) {
            return "INVIS";
        } else if (lower.startsWith("suspicious sound")) {
            return "SOUND";
        }
        return lower.startsWith("ghost particle") ? "PARTICLE" : "WATCH";
    }
}
