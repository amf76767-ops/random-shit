package dev.dihclient.port.chunks;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * Ported from an open-source client (GPL-3.0).
 * The maths of Prime Chunk Finder, without any game class: keeping the last block-light of every section of a chunk, finding which
 * light values changed when a new light packet comes in, and the distance rules that explain a change by a known light source
 * (a lamp, exposed redstone ore, an amethyst cluster) so that it does not count as a player's work.
 * Light arrays are Minecraft's nibble arrays: 2048 bytes, block index = y * 256 + z * 16 + x, even index in the low nibble.
 */
public final class LightDiff {
    public static final int SECTION_BYTES = 2048;
    /** Light of a lit redstone lamp / similar block: changes up to this far from one are explained by it. */
    public static final int LAMP_LIGHT = 14;
    /** Light of redstone ore (lit). */
    public static final int ORE_LIGHT = 9;
    /** Amethyst gives light 5 at most, and only that close. */
    public static final int AMETHYST_MAX_LIGHT = 5;
    public static final int AMETHYST_REACH = 5;
    /** Shared, never written to. */
    public static final byte[] ZERO_SECTION = new byte[SECTION_BYTES];

    private LightDiff() {
    }

    /** Is a light change at (x, y, z) with the bigger of its two values {@code value} explained by a lamp of light {@code lampLight} at the other point? */
    public static boolean lampExplains(int lx, int ly, int lz, int lampLight, int x, int y, int z, int value) {
        return manhattan(lx, ly, lz, x, y, z) <= lampLight - value;
    }

    /** Same for exposed redstone ore (light {@link #ORE_LIGHT}). */
    public static boolean oreExplains(int ox, int oy, int oz, int x, int y, int z, int value) {
        return value <= ORE_LIGHT && manhattan(ox, oy, oz, x, y, z) <= ORE_LIGHT - value;
    }

    /** Can an amethyst cluster at the other point cause a change from {@code oldValue} to {@code newValue}? */
    public static boolean amethystExplains(int ax, int ay, int az, int x, int y, int z, int oldValue, int newValue) {
        return Math.max(oldValue, newValue) <= AMETHYST_MAX_LIGHT && manhattan(ax, ay, az, x, y, z) <= AMETHYST_REACH;
    }

    public static int manhattan(int ax, int ay, int az, int bx, int by, int bz) {
        return Math.abs(ax - bx) + Math.abs(ay - by) + Math.abs(az - bz);
    }

    /** Index of a block inside a section array. */
    public static int blockIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    /** The light value (0-15) at a block index of a nibble array. */
    public static int nibble(byte[] section, int index) {
        int b = section[index >> 1] & 0xFF;
        return (index & 1) == 0 ? b & 15 : b >>> 4 & 15;
    }

    /** Decides whether a change is explained by something other than a player; true = ignore it. */
    @FunctionalInterface
    public interface Explainer {
        boolean explains(int x, int y, int z, int oldValue, int newValue);
    }

    /** The biggest change found in a packet (the one shown in the notification) and how many there were. */
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

    /** A chunk with no known light yet: every section is dark. */
    public static Map<Integer, byte[]> zeroedChunk(Map<Integer, byte[]> into, int bottomSection, int topSection) {
        for (int sectionY = bottomSection; sectionY <= topSection; sectionY++) {
            into.put(sectionY, ZERO_SECTION);
        }
        return into;
    }

    /**
     * Applies a light packet to the remembered light of the chunk.
     *
     * @param sections       section y -> light array; updated in place
     * @param inited         bit i set = the packet carries data for light section i (data in {@code updates}, in bit order)
     * @param uninited       bit i set = the packet says light section i is empty (becomes all dark)
     * @param lightBottomY   section y of light section 0 (one below the lowest world section)
     * @param bottomSection  lowest / highest world section y; the two outer light sections are not stored
     * @param compare        false for the first packet of a chunk (nothing to compare with) and for chunks the player was in
     * @param explainer      may be null
     * @param summary        receives the changes, may be null
     */
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
            return; // the usual case: nothing changed (one fast vectorised compare instead of 2048 single steps)
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
