package dev.fakedonut;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.class_1923;
import net.minecraft.class_2672;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_3218;
import net.minecraft.class_3222;

public final class AntiXray {
    private static final ThreadLocal<Boolean> REAL = new ThreadLocal<>();
    private static final ThreadLocal<Object[]> PENDING = new ThreadLocal<>();
    private static final Map<UUID, View> VIEWS = new HashMap<>();

    private static final class View {
        class_3218 world;
        int cx;
        int cz;
        final Set<Long> open = new HashSet<>();
    }

    private AntiXray() {
    }

    private static boolean hidden(int sectionY) {
        return sectionY + 15 < Config.get().hideBelowY;
    }

    public static boolean hiddenEntity(class_2818 chunk, int packedXz, int y) {
        if (!Config.get().antiXray || REAL.get() != null) {
            return false;
        }
        int minY = chunk.method_31607();
        int sy = minY + (((y - minY) >> 4) << 4);
        return hidden(sy);
    }

    public static class_2826[] prepare(class_2818 chunk) {
        class_2826[] out = compute(chunk);
        PENDING.set(new Object[] {chunk, out});
        return out;
    }

    public static class_2826[] sectionsFor(class_2818 chunk) {
        Object[] cached = PENDING.get();
        if (cached != null && cached[0] == chunk) {
            PENDING.remove();
            return (class_2826[]) cached[1];
        }
        return compute(chunk);
    }

    private static class_2826[] compute(class_2818 chunk) {
        class_2826[] src = chunk.method_12006();
        if (!Config.get().antiXray || REAL.get() != null) {
            return src;
        }
        try {
            int minY = chunk.method_31607();
            class_2826[] out = new class_2826[src.length];
            for (int i = 0; i < src.length; i++) {
                out[i] = hidden(minY + i * 16) ? fill(src[i]) : src[i];
            }
            return out;
        } catch (RuntimeException e) {
            FakeDonut.LOG.warn("anti-xray failed, sending the chunk unchanged", e);
            return src;
        }
    }

    private static class_2826 fill(class_2826 sec) {
        if (!sec.method_19523(s -> s.method_26204() != Ids.DEEPSLATE)) {
            return sec;
        }
        class_2826 copy = sec.method_61771();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (copy.method_12254(x, y, z).method_26204() != Ids.DEEPSLATE) {
                        copy.method_16675(x, y, z, Ids.DEEPSLATE.method_9564());
                    }
                }
            }
        }
        return copy;
    }

    private static void send(class_3222 player, class_3218 world, int cx, int cz, boolean real) {
        class_2818 chunk = world.method_8497(cx, cz);
        if (real) {
            REAL.set(Boolean.TRUE);
        }
        try {
            player.field_13987.method_52413(new class_2672(chunk, world.method_14178().method_12130(), null, null));
        } finally {
            REAL.remove();
        }
    }

    public static void tick(class_3218 world, class_3222 player) {
        Config cfg = Config.get();
        View v = VIEWS.computeIfAbsent(player.method_5667(), k -> new View());
        class_1923 pc = player.method_31476();
        if (v.world != world || Math.abs(v.cx - pc.field_9181) > 4 || Math.abs(v.cz - pc.field_9180) > 4) {
            v.open.clear();
            v.world = world;
        }
        v.cx = pc.field_9181;
        v.cz = pc.field_9180;
        boolean deep = cfg.antiXray && player.method_23318() < cfg.hideBelowY;
        int r = cfg.revealChunks;
        Set<Long> want = new HashSet<>();
        if (deep) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    want.add(class_1923.method_8331(pc.field_9181 + dx, pc.field_9180 + dz));
                }
            }
        }
        int budget = 6;
        for (java.util.Iterator<Long> it = v.open.iterator(); it.hasNext() && budget > 0;) {
            long k = it.next();
            if (want.contains(k)) {
                continue;
            }
            it.remove();
            int kx = class_1923.method_8325(k);
            int kz = class_1923.method_8332(k);
            if (Math.abs(kx - pc.field_9181) <= r + 2 && Math.abs(kz - pc.field_9180) <= r + 2 && world.method_8393(kx, kz)) {
                send(player, world, kx, kz, false);
                budget--;
            }
        }
        for (long k : want) {
            if (budget <= 0) {
                break;
            }
            if (v.open.contains(k)) {
                continue;
            }
            int kx = class_1923.method_8325(k);
            int kz = class_1923.method_8332(k);
            if (world.method_8393(kx, kz)) {
                send(player, world, kx, kz, true);
                v.open.add(k);
                budget--;
            }
        }
    }

    public static void forget(UUID id) {
        VIEWS.remove(id);
    }

    public static void clear() {
        VIEWS.clear();
    }
}
