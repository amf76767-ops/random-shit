package dev.dihclient.port.vanish;

import java.util.Locale;
import java.util.Map;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Small pure rules of the Anti Vanish module: which sounds and particles count as "somebody is here", how an item
 * placement marks the blocks the player itself touched, scoring of silent tab removals.
 */
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

    /** "minecraft:block.chest.open" -> "block.chest.open" (lower case); null -> "unknown". */
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

    /** Doors and gates that a villager can open on its own (trapdoors are not opened by villagers). */
    public static boolean doorLike(String id) {
        String path = path(id);
        return path.contains("door") && !path.contains("trapdoor") || path.contains("fence_gate");
    }

    /** Block ids whose presence next to a smoke particle explains it. */
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

    /** Weak particles that need two hits in a short time (and not right next to you) before they count. */
    public static boolean weakParticle(String particlePath) {
        return particlePath.equals("block") || particlePath.contains("smoke");
    }

    /** 100 when the server announces leaving players (so a silent removal is certain), else only a weak 30. */
    public static int silentTabRemovalScore(boolean serverSendsLeaveMessages) {
        return serverSendsLeaveMessages ? 100 : 30;
    }

    /** True for the tab-based vanish reasons that a later "player left" message disproves. */
    public static boolean tabDepartureReason(String reason) {
        if (reason == null) {
            return false;
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.startsWith("vanish event:") && (lower.contains("tab") || lower.contains("entity remained") || lower.contains("no leave packet"));
    }

    /** Same packing as Minecraft's BlockPos.asLong, so keys are comparable without loading the game classes. */
    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) | ((long) z & 0x3FFFFFFL) << 12;
    }

    /** Marks the block and, for doors, tall plants and beds, the other half(s), as "placed by yourself" until {@code until}. */
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

    /** "N", "SE" ... of a offset; empty when within one block on both axes. */
    public static String compass(double dx, double dz) {
        String ns = dz < -1.0 ? "N" : (dz > 1.0 ? "S" : "");
        String ew = dx > 1.0 ? "E" : (dx < -1.0 ? "W" : "");
        return ns + ew;
    }

    /** "23m NE", the location label of a sensor signal without a name. */
    public static String located(double dx, double dy, double dz) {
        long dist = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
        String dir = compass(dx, dz);
        return dir.isEmpty() ? dist + "m" : dist + "m " + dir;
    }

    /** HUD/summary tag of a detection reason. */
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
