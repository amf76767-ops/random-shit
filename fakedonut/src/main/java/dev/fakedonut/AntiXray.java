package dev.fakedonut;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2626;
import net.minecraft.class_2680;
import net.minecraft.class_2791;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_7923;

public final class AntiXray {
    private static final Set<class_2248> GEODE = new HashSet<>();
    private static final Set<class_2248> SPECIAL = new HashSet<>();
    private static final Map<UUID, Set<Long>> REVEALED = new HashMap<>();

    static {
        GEODE.add(Ids.AMETHYST_BLOCK);
        GEODE.add(Ids.BUDDING_AMETHYST);
        GEODE.add(Ids.SMALL_AMETHYST_BUD);
        GEODE.add(Ids.MEDIUM_AMETHYST_BUD);
        GEODE.add(Ids.LARGE_AMETHYST_BUD);
        GEODE.add(Ids.AMETHYST_CLUSTER);
        GEODE.add(Ids.CALCITE);
        GEODE.add(Ids.SMOOTH_BASALT);
        SPECIAL.add(Ids.CHEST);
        SPECIAL.add(Ids.TRAPPED_CHEST);
        SPECIAL.add(Ids.ENDER_CHEST);
        SPECIAL.add(Ids.BARREL);
        SPECIAL.add(Ids.SPAWNER);
        SPECIAL.add(Ids.FURNACE);
        SPECIAL.add(Ids.BLAST_FURNACE);
        SPECIAL.add(Ids.HOPPER);
        SPECIAL.add(Ids.DROPPER);
        SPECIAL.add(Ids.DISPENSER);
        SPECIAL.add(Ids.CRAFTING_TABLE);
        SPECIAL.add(Ids.TNT);
        SPECIAL.add(Ids.SEA_LANTERN);
        SPECIAL.add(Ids.DEEPSLATE_BRICKS);
        SPECIAL.add(Ids.CRACKED_DEEPSLATE_BRICKS);
        SPECIAL.add(Ids.DEEPSLATE_TILES);
        SPECIAL.add(Ids.POLISHED_DEEPSLATE);
    }

    private static final ThreadLocal<Object[]> PENDING = new ThreadLocal<>();

    private AntiXray() {
    }

    private static boolean isOre(class_2248 b) {
        String p = class_7923.field_41175.method_10221(b).method_12832();
        return p.endsWith("_ore") || p.equals("ancient_debris");
    }

    private static class_2248 filler(int y) {
        return y < 0 ? Ids.DEEPSLATE : Ids.STONE;
    }

    public static boolean hidesBlock(class_2248 b, boolean enclosed) {
        if (GEODE.contains(b)) {
            return true;
        }
        if (isOre(b) || SPECIAL.contains(b)) {
            return enclosed;
        }
        return false;
    }

    public static class_2826[] sectionsFor(class_2818 chunk) {
        Object[] cached = PENDING.get();
        if (cached != null && cached[0] == chunk) {
            PENDING.remove();
            return (class_2826[]) cached[1];
        }
        return compute(chunk);
    }

    public static class_2826[] prepare(class_2818 chunk) {
        class_2826[] out = compute(chunk);
        PENDING.set(new Object[] {chunk, out});
        return out;
    }

    private static class_2826[] compute(class_2818 chunk) {
        try {
            return computeUnsafe(chunk);
        } catch (RuntimeException e) {
            FakeDonut.LOG.warn("anti-xray failed, sending the chunk unchanged", e);
            return chunk.method_12006();
        }
    }

    private static class_2826[] computeUnsafe(class_2818 chunk) {
        class_2826[] src = chunk.method_12006();
        Config cfg = Config.get();
        if (!cfg.antiXray) {
            return src;
        }
        int cx = chunk.method_12004().field_9181;
        int cz = chunk.method_12004().field_9180;
        int minY = chunk.method_31607();
        List<Bases.Base> bases = Bases.inChunk(cx, cz);
        class_2826[] out = new class_2826[src.length];
        for (int i = 0; i < src.length; i++) {
            int sy = minY + i * 16;
            out[i] = sy > cfg.hideBelowY || src[i].method_38292() ? src[i] : hideSection(src[i], sy, cx, cz, bases);
        }
        return out;
    }

    private static boolean opaque(class_2680 s) {
        return s.method_26225();
    }

    private static class_2826 hideSection(class_2826 sec, int sy, int cx, int cz, List<Bases.Base> bases) {
        class_2680[] st = new class_2680[4096];
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    st[(y << 8) | (z << 4) | x] = sec.method_12254(x, y, z);
                }
            }
        }
        boolean[] hide = new boolean[4096];
        boolean any = false;
        boolean[] geode = new boolean[4096];
        for (int i = 0; i < 4096; i++) {
            geode[i] = GEODE.contains(st[i].method_26204());
        }
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int i = (y << 8) | (z << 4) | x;
                    class_2680 s = st[i];
                    class_2248 b = s.method_26204();
                    boolean h = false;
                    if (!bases.isEmpty()) {
                        int wx = (cx << 4) + x;
                        int wz = (cz << 4) + z;
                        for (Bases.Base base : bases) {
                            if (Bases.inside(base, wx, sy + y, wz)) {
                                h = true;
                                break;
                            }
                        }
                    }
                    if (!h) {
                        if (geode[i]) {
                            h = true;
                        } else if (isOre(b) || SPECIAL.contains(b)) {
                            h = enclosed(st, x, y, z);
                        } else if (!opaque(s) && Config.get().hideClosedRooms && nearGeode(geode, x, y, z)) {
                            h = true;
                        }
                    }
                    if (h && !(b == filler(sy + y))) {
                        hide[i] = true;
                        any = true;
                    }
                }
            }
        }
        if (!any) {
            return sec;
        }
        class_2826 copy = sec.method_61771();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (hide[(y << 8) | (z << 4) | x]) {
                        copy.method_16675(x, y, z, filler(sy + y).method_9564());
                    }
                }
            }
        }
        return copy;
    }

    private static boolean nearGeode(boolean[] geode, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int nx = x + dx;
                    int ny = y + dy;
                    int nz = z + dz;
                    if (nx < 0 || ny < 0 || nz < 0 || nx > 15 || ny > 15 || nz > 15) {
                        continue;
                    }
                    if (geode[(ny << 8) | (nz << 4) | nx]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static boolean enclosed(class_2680[] st, int x, int y, int z) {
        for (int[] d : DIRS) {
            int nx = x + d[0];
            int ny = y + d[1];
            int nz = z + d[2];
            if (nx < 0 || ny < 0 || nz < 0 || nx > 15 || ny > 15 || nz > 15) {
                continue;
            }
            if (!opaque(st[(ny << 8) | (nz << 4) | nx])) {
                return false;
            }
        }
        return true;
    }

    public static boolean hiddenAt(class_3218 world, int x, int y, int z) {
        if (!Config.get().antiXray || y > Config.get().hideBelowY) {
            return false;
        }
        if (Bases.insideAny(x, y, z)) {
            return true;
        }
        class_2338 p = new class_2338(x, y, z);
        class_2680 s = world.method_8320(p);
        class_2248 b = s.method_26204();
        if (GEODE.contains(b)) {
            return true;
        }
        if (isOre(b) || SPECIAL.contains(b)) {
            for (int[] d : DIRS) {
                int nx = x + d[0];
                int nz = z + d[2];
                if (world.method_8393(nx >> 4, nz >> 4) && !opaque(world.method_8320(p.method_10069(d[0], d[1], d[2])))) {
                    return false;
                }
            }
            return true;
        }
        if (!opaque(s) && Config.get().hideClosedRooms) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (world.method_8393((x + dx) >> 4, (z + dz) >> 4) && GEODE.contains(world.method_8320(p.method_10069(dx, dy, dz)).method_26204())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static void reveal(class_3222 player, class_3218 world, int cx, int cy, int cz, int r) {
        Set<Long> seen = REVEALED.computeIfAbsent(player.method_5667(), k -> new HashSet<>());
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    if (y > Config.get().hideBelowY || y < world.method_31607()) {
                        continue;
                    }
                    if (!world.method_8393(x >> 4, z >> 4)) {
                        continue;
                    }
                    class_2338 p = new class_2338(x, y, z);
                    long k = p.method_10063();
                    if (seen.contains(k)) {
                        continue;
                    }
                    if (hiddenAt(world, x, y, z)) {
                        seen.add(k);
                        player.field_13987.method_52413(new class_2626(p, world.method_8320(p)));
                        if (world.method_8321(p) != null) {
                            player.field_13987.method_52413(world.method_8321(p).method_38235());
                        }
                    }
                }
            }
        }
    }

    public static void prune(class_3222 player) {
        Set<Long> seen = REVEALED.get(player.method_5667());
        if (seen == null || seen.size() < 4096) {
            return;
        }
        int px = player.method_31477();
        int pz = player.method_31479();
        seen.removeIf(k -> {
            class_2338 p = class_2338.method_10092(k);
            return Math.abs(p.method_10263() - px) > 160 || Math.abs(p.method_10260() - pz) > 160;
        });
    }

    public static void forget(class_3222 player) {
        REVEALED.remove(player.method_5667());
    }

    public static boolean hiddenEntity(class_2818 chunk, int packedXz, int y) {
        try {
            return hiddenEntityUnsafe(chunk, packedXz, y);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean hiddenEntityUnsafe(class_2818 chunk, int packedXz, int y) {
        Config cfg = Config.get();
        if (!cfg.antiXray || y > cfg.hideBelowY) {
            return false;
        }
        int lx = (packedXz >> 4) & 15;
        int lz = packedXz & 15;
        int cx = chunk.method_12004().field_9181;
        int cz = chunk.method_12004().field_9180;
        if (Bases.insideAny((cx << 4) + lx, y, (cz << 4) + lz)) {
            return true;
        }
        class_2338 p = new class_2338((cx << 4) + lx, y, (cz << 4) + lz);
        class_2248 b = chunk.method_8320(p).method_26204();
        if (!SPECIAL.contains(b)) {
            return false;
        }
        int ly = (y - chunk.method_31607()) & 15;
        for (int[] d : DIRS) {
            int nx = lx + d[0];
            int ny = ly + d[1];
            int nz = lz + d[2];
            if (nx < 0 || ny < 0 || nz < 0 || nx > 15 || ny > 15 || nz > 15) {
                continue;
            }
            if (!opaque(chunk.method_8320(p.method_10069(d[0], d[1], d[2])))) {
                return false;
            }
        }
        return true;
    }
}
