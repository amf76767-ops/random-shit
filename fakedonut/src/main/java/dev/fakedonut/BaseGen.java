package dev.fakedonut;

import java.util.Random;
import net.minecraft.class_1263;
import net.minecraft.class_1299;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_2248;
import net.minecraft.class_2586;
import net.minecraft.class_2636;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_2818;
import net.minecraft.class_3218;

public final class BaseGen {
    private static final int SEA = 62;
    private static final int FLAGS = 2 | 16;

    private BaseGen() {
    }

    private static final Object[][] LOOT = {
        {Ids.DIAMOND, 1, 9, 10}, {Ids.NETHERITE_SCRAP, 1, 4, 5}, {Ids.NETHERITE_INGOT, 1, 2, 2},
        {Ids.EMERALD, 2, 20, 8}, {Ids.GOLD_INGOT, 4, 32, 9}, {Ids.IRON_INGOT, 8, 48, 10},
        {Ids.GOLDEN_APPLE, 1, 6, 6}, {Ids.ENCHANTED_GOLDEN_APPLE, 1, 2, 2}, {Ids.ENDER_PEARL, 2, 16, 8},
        {Ids.TOTEM_OF_UNDYING, 1, 1, 3}, {Ids.EXPERIENCE_BOTTLE, 8, 64, 8}, {Ids.REDSTONE, 8, 48, 6},
        {Ids.ARROW, 16, 64, 5}, {Ids.COOKED_BEEF, 8, 32, 6}, {Ids.END_CRYSTAL, 1, 6, 3},
        {Ids.ELYTRA, 1, 1, 1}, {Ids.DIAMOND_PICKAXE, 1, 1, 3}, {Ids.NETHERITE_SWORD, 1, 1, 1},
        {Ids.AMETHYST_SHARD, 4, 32, 4}, {Ids.COAL, 8, 48, 6}, {Ids.LAPIS_LAZULI, 4, 32, 4}
    };

    private static int totalWeight() {
        int t = 0;
        for (Object[] l : LOOT) {
            t += (Integer) l[3];
        }
        return t;
    }

    private static class_1799 roll(Random r) {
        int pick = r.nextInt(totalWeight());
        for (Object[] l : LOOT) {
            pick -= (Integer) l[3];
            if (pick < 0) {
                int min = (Integer) l[1];
                int max = (Integer) l[2];
                return new class_1799((class_1792) l[0], min + r.nextInt(max - min + 1));
            }
        }
        return class_1799.field_8037;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static class_2680 withAge(class_2680 state, int age) {
        for (class_2769<?> p : state.method_28501()) {
            if ("age".equals(p.method_11899())) {
                return (class_2680) state.method_11657((class_2769) p, (Comparable) Integer.valueOf(age));
            }
        }
        return state;
    }

    private static boolean floorOk(class_2248 b) {
        return b == Ids.SAND || b == Ids.GRAVEL || b == Ids.DIRT || b == Ids.CLAY || b == Ids.MUD;
    }

    public static boolean tryChunk(class_3218 world, class_2818 chunk, Random r, boolean force) {
        Config c = Config.get();
        if (!force && r.nextDouble() > c.baseChance) {
            return false;
        }
        int cx = chunk.method_12004().field_9181;
        int cz = chunk.method_12004().field_9180;
        if (Bases.chunkHas(cx, cz)) {
            return false;
        }
        for (int attempt = 0; attempt < 24; attempt++) {
            int lx = 5 + r.nextInt(6);
            int lz = 5 + r.nextInt(6);
            int x = (cx << 4) + lx;
            int z = (cz << 4) + lz;
            class_2338.class_2339 pos = new class_2338.class_2339(x, SEA, z);
            if (world.method_8320(pos).method_26204() != Ids.WATER) {
                continue;
            }
            int y = SEA;
            while (y > world.method_31607() + 4 && world.method_8320(pos.method_10103(x, y - 1, z)).method_26204() == Ids.WATER) {
                y--;
            }
            int floor = y - 1;
            if (!floorOk(world.method_8320(pos.method_10103(x, floor, z)).method_26204())) {
                continue;
            }
            if (SEA - floor < c.seaMinDepth) {
                continue;
            }
            return build(world, x, z, floor, r);
        }
        return false;
    }

    private static boolean build(class_3218 world, int x, int z, int floor, Random r) {
        Config c = Config.get();
        int placed = 0;
        int bestTop = 0;
        int bestX = x;
        int bestZ = z;
        for (int i = 0; i < c.kelpPerBase * 3 && placed < c.kelpPerBase; i++) {
            int kx = x + r.nextInt(7) - 3;
            int kz = z + r.nextInt(7) - 3;
            class_2338.class_2339 p = new class_2338.class_2339(kx, SEA, kz);
            if (world.method_8320(p).method_26204() != Ids.WATER) {
                continue;
            }
            int fy = SEA;
            while (fy > world.method_31607() + 4 && world.method_8320(p.method_10103(kx, fy - 1, kz)).method_26204() == Ids.WATER) {
                fy--;
            }
            fy--;
            if (!floorOk(world.method_8320(p.method_10103(kx, fy, kz)).method_26204())) {
                continue;
            }
            int top = Math.min(SEA - 1, fy + 4 + r.nextInt(Math.max(1, SEA - fy - 3)));
            if (top <= fy + 1) {
                continue;
            }
            class_2680 plant = Ids.KELP_PLANT.method_9564();
            class_2680 head = withAge(Ids.KELP.method_9564(), 25);
            for (int ky = fy + 1; ky < top; ky++) {
                world.method_8652(new class_2338(kx, ky, kz), plant, FLAGS);
            }
            world.method_8652(new class_2338(kx, top, kz), head, FLAGS);
            placed++;
            if (top > bestTop) {
                bestTop = top;
                bestX = kx;
                bestZ = kz;
            }
        }
        if (placed < c.baseMinKelp) {
            return false;
        }
        Bases.Base b = new Bases.Base();
        b.x = x;
        b.z = z;
        b.y = c.baseY;
        b.kelpX = bestX;
        b.kelpZ = bestZ;
        b.kelpTop = bestTop;
        b.kelpFloor = floor;
        b.spawner = r.nextDouble() < c.spawnerChance;
        b.owner = OWNERS[r.nextInt(OWNERS.length)];
        if (!stash(world, b, r)) {
            room(world, b, r);
        }
        Bases.add(b);
        FakeDonut.LOG.info("Base at {} {} {} under {} kelp ({} {})", b.x, b.y, b.z, placed, bestX, bestZ);
        return true;
    }

    private static final String[] OWNERS = {"Steve_x", "DeepDigger", "xX_KelpKing_Xx", "AquaMiner", "NoLifeNate", "BaseBuilder99", "GlitchWizard", "Sven_DE"};

    private static void set(class_3218 w, int x, int y, int z, class_2248 b) {
        w.method_8652(new class_2338(x, y, z), b.method_9564(), 2);
    }

    private static void chest(class_3218 w, int x, int y, int z, class_2248 type, Random r) {
        set(w, x, y, z, type);
        class_2586 be = w.method_8321(new class_2338(x, y, z));
        if (be instanceof class_1263 inv) {
            int n = 5 + r.nextInt(10);
            for (int i = 0; i < n; i++) {
                inv.method_5447(r.nextInt(inv.method_5439()), roll(r));
            }
        }
    }

    private static boolean stash(class_3218 w, Bases.Base b, Random r) {
        java.util.List<Stashes.Stash> all = Stashes.all();
        if (all.isEmpty()) {
            return false;
        }
        Stashes.Stash st = all.get(r.nextInt(all.size()));
        int ox = b.x - st.sx / 2;
        int oz = b.z - st.sz / 2;
        int oy = b.y;
        if (oy + st.sy >= 0 || oy < w.method_31607()) {
            return false;
        }
        for (int cx = ox >> 4; cx <= (ox + st.sx - 1) >> 4; cx++) {
            for (int cz = oz >> 4; cz <= (oz + st.sz - 1) >> 4; cz++) {
                if (!w.method_8393(cx, cz)) {
                    return false;
                }
            }
        }
        for (int i = 0; i < st.pos.size(); i++) {
            int[] p = st.pos.get(i);
            class_2680 state = st.states.get(i);
            class_2338 at = new class_2338(ox + p[0], oy + p[1], oz + p[2]);
            w.method_8652(at, state, 2);
            class_2586 be = w.method_8321(at);
            if (be instanceof class_2636 sp) {
                sp.method_46408(r.nextBoolean() ? Ids.ZOMBIE_TYPE : Ids.SKELETON_TYPE, w.field_9229);
            } else if (be instanceof class_1263 inv && !(be instanceof class_2636)) {
                int n = 4 + r.nextInt(10);
                for (int k = 0; k < n; k++) {
                    inv.method_5447(r.nextInt(inv.method_5439()), roll(r));
                }
            }
        }
        b.spawner = false;
        FakeDonut.LOG.info("Stash {} placed at {} {} {}", st.name, ox, oy, oz);
        return true;
    }

    private static void room(class_3218 w, Bases.Base b, Random r) {
        int h = Bases.HALF;
        int y0 = b.y - 1;
        for (int dx = -h; dx <= h; dx++) {
            for (int dz = -h; dz <= h; dz++) {
                for (int dy = 0; dy < Bases.HEIGHT; dy++) {
                    boolean wall = Math.abs(dx) == h || Math.abs(dz) == h || dy == 0 || dy == Bases.HEIGHT - 1;
                    class_2248 blk = Ids.AIR;
                    if (wall) {
                        int roll = r.nextInt(10);
                        blk = roll < 6 ? Ids.DEEPSLATE_BRICKS : roll < 8 ? Ids.CRACKED_DEEPSLATE_BRICKS : roll < 9 ? Ids.DEEPSLATE_TILES : Ids.POLISHED_DEEPSLATE;
                    }
                    set(w, b.x + dx, y0 + dy, b.z + dz, blk);
                }
            }
        }
        for (int c = 0; c < 4; c++) {
            int sx = (c & 1) == 0 ? -h : h;
            int sz = (c & 2) == 0 ? -h : h;
            set(w, b.x + sx, y0 + 3, b.z + sz, Ids.SEA_LANTERN);
        }
        int in = h - 1;
        chest(w, b.x - in, b.y, b.z - in, Ids.CHEST, r);
        chest(w, b.x - in + 1, b.y, b.z - in, Ids.CHEST, r);
        chest(w, b.x + in, b.y, b.z - in, Ids.BARREL, r);
        chest(w, b.x + in, b.y, b.z - in + 1, Ids.TRAPPED_CHEST, r);
        set(w, b.x - in, b.y, b.z + in, Ids.FURNACE);
        set(w, b.x - in + 1, b.y, b.z + in, Ids.BLAST_FURNACE);
        set(w, b.x - in + 2, b.y, b.z + in, Ids.CRAFTING_TABLE);
        set(w, b.x + in, b.y, b.z + in, Ids.HOPPER);
        set(w, b.x + in - 1, b.y, b.z + in, Ids.HOPPER);
        set(w, b.x + in - 2, b.y, b.z + in, Ids.DROPPER);
        set(w, b.x, b.y, b.z - in, Ids.ENDER_CHEST);
        if (b.spawner) {
            set(w, b.x, b.y + 1, b.z, Ids.SPAWNER);
            class_2586 be = w.method_8321(new class_2338(b.x, b.y + 1, b.z));
            if (be instanceof class_2636 sp) {
                class_1299<?> t = r.nextBoolean() ? Ids.ZOMBIE_TYPE : Ids.SKELETON_TYPE;
                sp.method_46408(t, w.field_9229);
            }
            set(w, b.x, b.y, b.z, Ids.DEEPSLATE_BRICKS);
        }
        set(w, b.x + 2, b.y, b.z + 2, Ids.TNT);
        set(w, b.x - 2, b.y, b.z + 3, Ids.COBWEB);
    }
}
