package dev.dihclient.port.donutd;

import java.util.function.IntPredicate;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * The decisions of Amethyst Bypass and Spawner Nametags without any game class, so they can be unit tested.
 */
public final class DonutDLogic {
    /** Block light of a large amethyst bud: the light an exposed geode surface shows. */
    private static final int EDGE_LIGHT = 4;
    private static final double FADE_SHARE = 0.125;
    private static final double MIN_TEXT_SCALE = 1.0;
    private static final double MAX_TEXT_SCALE = 8.0;
    private static final double TEXT_SCALE_DISTANCE = 10.0;

    private DonutDLogic() {
    }

    /** A stable pseudo-random 0-99 per block, so the one marker chosen in a chunk does not jump around between scans. */
    public static int scatter(int x, int y, int z) {
        long h = x * -7046029254386353131L ^ y * -4417276706812531889L ^ z * 1609587929392839161L;
        h ^= h >>> 29;
        h *= -4658895280553007687L;
        h ^= h >>> 32;
        return (int) Math.floorMod(h, 100L);
    }

    /** Block light a bud leaves on its own block: small 1, medium 2, large 4, cluster 5. */
    public static boolean isBudLight(int light) {
        return light == 1 || light == 2 || light == 4 || light == 5;
    }

    /** True when no neighbour has at least this much light, i.e. the bud itself is the source. */
    public static boolean isLocalMaximum(int light, int[] neighbourLight) {
        for (int value : neighbourLight) {
            if (value >= light) {
                return false;
            }
        }
        return true;
    }

    /**
     * ANBS+ Scan: is the open block a glow edge of a geode? No neighbour may be brighter than 4, at least one has exactly 4 and
     * that one is open (air or a cluster) too. {@code openAt} is asked lazily for the side index, as it costs a block lookup.
     *
     * @param sideLight block light of the six neighbours
     */
    public static boolean isGlowEdge(int[] sideLight, IntPredicate openAt) {
        boolean litOpening = false;
        int brightest = 0;
        for (int side = 0; side < sideLight.length; side++) {
            int value = sideLight[side];
            if (value > EDGE_LIGHT) {
                return false;
            }
            brightest = Math.max(brightest, value);
            if (value == EDGE_LIGHT && !litOpening && openAt.test(side)) {
                litOpening = true;
            }
        }
        return brightest == EDGE_LIGHT && litOpening;
    }

    /** Index into the 3 x 3 chunks x 8 sections light cache of {@link GeodeGlowScan}. */
    public static int glowSlot(int dx, int dz, int s) {
        return ((dx + 1) * 3 + dz + 1) * 8 + s;
    }

    /** Is the chunk key (x, z) within {@code radius} chunks (square) of the centre? */
    public static boolean inChunkRange(int x, int z, int centerX, int centerZ, int radius) {
        return Math.abs(x - centerX) <= radius && Math.abs(z - centerZ) <= radius;
    }

    /** 1 close to the player, falling to 0 over the outer eighth of the range. */
    public static float fade(double distance, double range) {
        return (float) Math.max(0.0, Math.min(1.0, (range - distance) / (range * FADE_SHARE)));
    }

    /** Ring opacity 0-255: brighter while the player is inside the activation range, faded out with the distance. */
    public static int ringAlpha(boolean inside, float fade) {
        return Math.round((inside ? 0.8F : 0.4F) * fade * 255.0F);
    }

    /** A colour with the alpha of {@code alpha} (0-255) and the rgb of {@code rgb}. */
    public static int withAlpha(int rgb, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | rgb & 0xFFFFFF;
    }

    /** World text shrinks with the distance; this grows it so a label stays about as big on the screen. */
    public static float textScale(double distance) {
        return (float) Math.max(MIN_TEXT_SCALE, Math.min(MAX_TEXT_SCALE, distance / TEXT_SCALE_DISTANCE));
    }

    /** The player is within the activation range; a negative range (unknown) counts as inside. */
    public static boolean isInside(double distanceSq, int activationRange) {
        return activationRange < 0 || distanceSq < (double) activationRange * activationRange;
    }

    public static String title(String mob) {
        return mob == null || mob.isEmpty() ? "Spawner" : mob + " Spawner";
    }

    public static String detail(double distance, int activationRange, boolean showRange) {
        String text = Math.round(distance) + "m";
        return showRange && activationRange > 0 ? text + "  ·  " + activationRange + "m range" : text;
    }

    /** x of point {@code i} of a ring with {@code segments} segments. */
    public static double ringX(int i, int segments, double radius) {
        return Math.cos(Math.PI * 2 * i / segments) * radius;
    }

    public static double ringZ(int i, int segments, double radius) {
        return Math.sin(Math.PI * 2 * i / segments) * radius;
    }
}
