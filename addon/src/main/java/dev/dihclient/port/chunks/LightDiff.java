package dev.dihclient.port.chunks;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/** Ported from an open-source client (GPL-3.0). */
public final class LightDiff {
    public static final int SECTION_BYTES = 2048;

    public static final int LAMP_LIGHT = 14;

    public static final int ORE_LIGHT = 9;

    public static final int AMETHYST_MAX_LIGHT = 5;
    public static final int AMETHYST_REACH = 5;

    public static final byte[] ZERO_SECTION = new byte[SECTION_BYTES];

    private LightDiff() {
    }

    public static boolean lampExplains(int lx, int ly, int lz, int lampLight, int x, int y, int z, int value) {
        return manhattan(lx, ly, lz, x, y, z) <= lampLight - value;
    }

    public static boolean oreExplains(int ox, int oy, int oz, int x, int y, int z, int value) {
        return value <= ORE_LIGHT && manhattan(ox, oy, oz, x, y, z) <= ORE_LIGHT - value;
    }

    public static boolean amethystExplains(int ax, int ay, int az, int x, int y, int z, int oldValue, int newValue) {
        return Math.max(oldValue, newValue) <= AMETHYST_MAX_LIGHT && manhattan(ax, ay, az, x, y, z) <= AMETHYST_REACH;
    }

    public static int manhattan(int ax, int ay, int az, int bx, int by, int bz) {
        return Math.abs(ax - bx) + Math.abs(ay - by) + Math.abs(az - bz);
    }

    public static int blockIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    public static int nibble(byte[] section, int index) {
        int b = section[index >> 1] & 0xFF;
        return (index & 1) == 0 ? b & 15 : b >>> 4 & 15;
    }

    @FunctionalInterface
    public interface Explainer {
        boolean explains(int x, int y, int z, int oldValue, int newValue);
    }

    public static final class Summary {
        public int count;
        public int x;
        public int y;
        public int z;
        public int oldValue;
        public int newValue;

        public void add(int x, int y, int z, int oldValue, int newValue) {
            if (this.count == 0 || Math.abs(newValue - oldValue) > Math.abs(this.newValue - this.oldValue)) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.oldValue = oldValue;
                this.newValue = newValue;
            }
            this.count++;
        }
    }

    public static Map<Integer, byte[]> zeroedChunk(Map<Integer, byte[]> into, int bottomSection, int topSection) {
        for (int sectionY = bottomSection; sectionY <= topSection; sectionY++) {
            into.put(sectionY, ZERO_SECTION);
        }
        return into;
    }

    public static void apply(Map<Integer, byte[]> sections, int chunkX, int chunkZ, BitSet inited, List<byte[]> updates, BitSet uninited,
                             int lightBottomY, int bottomSection, int topSection, boolean compare, Explainer explainer, Summary summary) {
        int next = 0;
        for (int bit = inited.nextSetBit(0); bit >= 0 && next < updates.size(); bit = inited.nextSetBit(bit + 1)) {
            byte[] update = updates.get(next++);
            int sectionY = lightBottomY + bit;
            if (sectionY >= bottomSection && sectionY <= topSection && update.length == SECTION_BYTES) {
                updateSection(sections, chunkX, chunkZ, sectionY, Arrays.copyOf(update, SECTION_BYTES), compare, explainer, summary);
            }
        }
        for (int bit = uninited.nextSetBit(0); bit >= 0; bit = uninited.nextSetBit(bit + 1)) {
            int sectionY = lightBottomY + bit;
            if (sectionY >= bottomSection && sectionY <= topSection) {
                updateSection(sections, chunkX, chunkZ, sectionY, ZERO_SECTION, compare, explainer, summary);
            }
        }
    }

    private static void updateSection(Map<Integer, byte[]> sections, int chunkX, int chunkZ, int sectionY, byte[] light, boolean compare,
                                      Explainer explainer, Summary summary) {
        byte[] previous = sections.get(sectionY);
        if (compare && previous != null && summary != null) {
            compareSection(previous, light, chunkX, chunkZ, sectionY, explainer, summary);
        }
        sections.put(sectionY, light);
    }

    public static void compareSection(byte[] previous, byte[] light, int chunkX, int chunkZ, int sectionY, Explainer explainer, Summary summary) {
        if (previous == light || Arrays.equals(previous, light)) {
            return;
        }
        for (int i = 0; i < SECTION_BYTES; i++) {
            int was = previous[i] & 0xFF;
            int now = light[i] & 0xFF;
            int changed = was ^ now;
            if ((changed & 0x0F) != 0) {
                record(chunkX, chunkZ, sectionY, i << 1, was & 15, now & 15, explainer, summary);
            }
            if ((changed & 0xF0) != 0) {
                record(chunkX, chunkZ, sectionY, (i << 1) + 1, was >>> 4 & 15, now >>> 4 & 15, explainer, summary);
            }
        }
    }

    private static void record(int chunkX, int chunkZ, int sectionY, int index, int oldValue, int newValue, Explainer explainer, Summary summary) {
        int x = (chunkX << 4) + (index & 15);
        int y = (sectionY << 4) + (index >>> 8 & 15);
        int z = (chunkZ << 4) + (index >>> 4 & 15);
        if (explainer == null || !explainer.explains(x, y, z, oldValue, newValue)) {
            summary.add(x, y, z, oldValue, newValue);
        }
    }
}
