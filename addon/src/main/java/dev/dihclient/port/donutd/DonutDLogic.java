package dev.dihclient.port.donutd;

import java.util.function.IntPredicate;

/** Ported from an open-source client (GPL-3.0). */
public final class DonutDLogic {

    private static final int EDGE_LIGHT = 4;
    private static final double FADE_SHARE = 0.125;
    private static final double MIN_TEXT_SCALE = 1.0;
    private static final double MAX_TEXT_SCALE = 8.0;
    private static final double TEXT_SCALE_DISTANCE = 10.0;

    private DonutDLogic() {
    }

    public static int scatter(int x, int y, int z) {
        long h = x * -7046029254386353131L ^ y * -4417276706812531889L ^ z * 1609587929392839161L;
        h ^= h >>> 29;
        h *= -4658895280553007687L;
        h ^= h >>> 32;
        return (int) Math.floorMod(h, 100L);
    }

    public static boolean isBudLight(int light) {
        return light == 1 || light == 2 || light == 4 || light == 5;
    }

    public static boolean isLocalMaximum(int light, int[] neighbourLight) {
        for (int value : neighbourLight) {
            if (value >= light) {
                return false;
            }
        }
        return true;
    }

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

    public static int glowSlot(int dx, int dz, int s) {
        return ((dx + 1) * 3 + dz + 1) * 8 + s;
    }

    public static boolean inChunkRange(int x, int z, int centerX, int centerZ, int radius) {
        return Math.abs(x - centerX) <= radius && Math.abs(z - centerZ) <= radius;
    }

    public static float fade(double distance, double range) {
        return (float) Math.max(0.0, Math.min(1.0, (range - distance) / (range * FADE_SHARE)));
    }

    public static int ringAlpha(boolean inside, float fade) {
        return Math.round((inside ? 0.8F : 0.4F) * fade * 255.0F);
    }

    public static int withAlpha(int rgb, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | rgb & 0xFFFFFF;
    }

    public static float textScale(double distance) {
        return (float) Math.max(MIN_TEXT_SCALE, Math.min(MAX_TEXT_SCALE, distance / TEXT_SCALE_DISTANCE));
    }

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

    public static double ringX(int i, int segments, double radius) {
        return Math.cos(Math.PI * 2 * i / segments) * radius;
    }

    public static double ringZ(int i, int segments, double radius) {
        return Math.sin(Math.PI * 2 * i / segments) * radius;
    }
}
