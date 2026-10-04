package dev.dihclient.port.donutd;

import java.util.function.LongConsumer;
import net.minecraft.class_1944;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_2804;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_3562;
import net.minecraft.class_4076;
import net.minecraft.class_638;
import net.minecraft.class_2338.class_2339;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * ANBS+ Scan of Amethyst Bypass. Reads the block light the server sent (the light is not hidden by anti-xray) and looks, between
 * Y -58 and 30, for open blocks with light 0 that touch light 4 and nothing brighter: the faint glow a geode throws into the
 * hidden cave around it. The light layers of the chunk and its eight neighbours are cached once per scan.
 */
public final class GeodeGlowScan {
    public static final int MIN_Y = -58;
    public static final int MAX_Y = 30;
    private static final int LOW_SECTION = -4;
    private static final int HIGH_SECTION = 1;
    private static final int FIRST_CACHED = -5;
    private static final int CACHED = 8;
    private static final class_2350[] SIDES = class_2350.values();

    private final class_2804[] layers = new class_2804[9 * CACHED];
    private final class_2339 cursor = new class_2339();
    private final int[] sideLight = new int[SIDES.length];
    private int chunkX;
    private int chunkZ;
    private class_638 level;
    private int edgeX;
    private int edgeY;
    private int edgeZ;

    /** @return how many glow edges the chunk has; each is passed to {@code hit} as a packed block position (may be null) */
    public int scan(class_638 level, class_2818 chunk, LongConsumer hit) {
        this.level = level;
        this.chunkX = chunk.method_12004().field_9181;
        this.chunkZ = chunk.method_12004().field_9180;
        if (!this.cacheLight(level)) {
            return 0;
        }
        class_2826[] sections = chunk.method_12006();
        int minX = this.chunkX << 4;
        int minZ = this.chunkZ << 4;
        int count = 0;
        for (int sy = LOW_SECTION; sy <= HIGH_SECTION; sy++) {
            int index = chunk.method_31603(sy);
            if (index < 0 || index >= sections.length || sections[index] == null || !this.nearLight(sy)) {
                continue;
            }
            class_2826 section = sections[index];
            int from = Math.max(MIN_Y, sy << 4);
            int to = Math.min(MAX_Y, (sy << 4) + 15);
            for (int y = from; y <= to; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!open(section.method_12254(x, y & 15, z))) {
                            continue;
                        }
                        int wx = minX + x;
                        int wz = minZ + z;
                        if (this.lightAt(wx, y, wz) == 0 && this.edge(wx, y, wz)) {
                            count++;
                            if (hit != null) {
                                hit.accept(class_2338.method_10064(wx, y, wz));
                            }
                        }
                    }
                }
            }
        }
        this.level = null;
        return count;
    }

    private boolean cacheLight(class_638 level) {
        class_3562 light = level.method_22336().method_15562(class_1944.field_9282);
        boolean any = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int s = 0; s < CACHED; s++) {
                    class_2804 layer = light.method_15544(class_4076.method_18676(this.chunkX + dx, FIRST_CACHED + s, this.chunkZ + dz));
                    if (layer != null && layer.method_12146()) {
                        layer = null; // an all-zero layer says nothing
                    }
                    this.layers[DonutDLogic.glowSlot(dx, dz, s)] = layer;
                    any |= layer != null;
                }
            }
        }
        return any;
    }

    /** Is there any light data in the section or the ones above and below it, in this and the neighbour chunks? */
    private boolean nearLight(int sy) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int s = sy - 1; s <= sy + 1; s++) {
                    int cached = s - FIRST_CACHED;
                    if (cached >= 0 && cached < CACHED && this.layers[DonutDLogic.glowSlot(dx, dz, cached)] != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean edge(int x, int y, int z) {
        this.edgeX = x;
        this.edgeY = y;
        this.edgeZ = z;
        for (int i = 0; i < SIDES.length; i++) {
            this.sideLight[i] = this.lightAt(x + SIDES[i].method_10148(), y + SIDES[i].method_10164(), z + SIDES[i].method_10165());
        }
        return DonutDLogic.isGlowEdge(this.sideLight, this::openSide);
    }

    private boolean openSide(int side) {
        class_2350 d = SIDES[side];
        return open(this.level.method_8320(this.cursor.method_10103(this.edgeX + d.method_10148(), this.edgeY + d.method_10164(), this.edgeZ + d.method_10165())));
    }

    private int lightAt(int x, int y, int z) {
        int s = (y >> 4) - FIRST_CACHED;
        if (s < 0 || s >= CACHED) {
            return 0;
        }
        class_2804 layer = this.layers[DonutDLogic.glowSlot((x >> 4) - this.chunkX, (z >> 4) - this.chunkZ, s)];
        return layer == null ? 0 : layer.method_12139(x & 15, y & 15, z & 15);
    }

    /** Air, or an amethyst cluster (it lets light through and sits where a bud grew). */
    private static boolean open(class_2680 state) {
        return state.method_26215() || state.method_27852(class_2246.field_27161);
    }
}
