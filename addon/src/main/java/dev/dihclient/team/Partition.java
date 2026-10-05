package dev.dihclient.team;

public final class Partition {
    private Partition() {
    }

    public static int strip(int coord, int min, int max, int count) {
        if (count <= 1 || max <= min) {
            return 0;
        }
        long extent = (long) max - min + 1;
        long strip = (long) (coord - min) * count / extent;
        return (int) Math.max(0, Math.min(count - 1, strip));
    }

    public static boolean mine(int coord, int min, int max, int index, int count) {
        return strip(coord, min, max, count) == index;
    }
}
