package dev.dihclient.port.lag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where the server's tick rate was low. Every sample is the TPS the server managed in the last second, taken at the place the player
 * stood. A server only ticks what is near a player, so a huge hidden farm or base makes the TPS drop exactly while you are within its
 * range and recover when you leave: the cells with a clearly lower average than the normal TPS, joined together, mark such a place.
 * Pure logic, no game classes.
 */
public final class LagMap {
    /** Side of a cell in blocks. */
    public static final int CELL = 32;
    private static final int RECENT = 400;
    /** Slow cells up to this many cells apart belong to the same zone (a source slows the server within about 100 blocks). */
    private static final int LINK = 3;

    /** A group of neighbouring slow cells. */
    public record Zone(double x, double z, double radius, double avgTps, int samples, int cells) {
        /** A key that stays the same while the zone only grows a little. */
        public long id() {
            return ((long) Math.floorDiv((int) x, 96) << 32) ^ (Math.floorDiv((int) z, 96) & 0xFFFFFFFFL);
        }
    }

    private static final class Cell {
        double sum;
        int n;
    }

    private final Map<Long, Cell> cells = new HashMap<>();
    private final double[] recent = new double[RECENT];
    private int recentCount;
    private int recentPos;

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private static int cx(long key) {
        return (int) (key >> 32);
    }

    private static int cz(long key) {
        return (int) key;
    }

    public void clear() {
        this.cells.clear();
        this.recentCount = 0;
        this.recentPos = 0;
    }

    public int cellCount() {
        return this.cells.size();
    }

    public void add(double x, double z, double tps) {
        double clamped = Math.max(0.0, Math.min(20.0, tps));
        Cell c = this.cells.computeIfAbsent(key(Math.floorDiv((int) Math.floor(x), CELL), Math.floorDiv((int) Math.floor(z), CELL)), k -> new Cell());
        c.sum += clamped;
        c.n++;
        this.recent[this.recentPos] = clamped;
        this.recentPos = (this.recentPos + 1) % RECENT;
        this.recentCount = Math.min(RECENT, this.recentCount + 1);
    }

    /** The usual TPS: the median of the latest samples (20 when there are too few). */
    public double baseline() {
        if (this.recentCount < 12) {
            return 20.0;
        }
        double[] copy = Arrays.copyOf(this.recent, this.recentCount);
        Arrays.sort(copy);
        return Math.min(20.0, copy[copy.length / 2]);
    }

    /**
     * @param drop        how many TPS below the normal TPS a cell has to average to count as slow
     * @param minSamples  samples a cell needs before it is judged
     */
    public List<Zone> zones(double drop, int minSamples) {
        double normal = this.baseline();
        Map<Long, Cell> hot = new HashMap<>();
        for (Map.Entry<Long, Cell> e : this.cells.entrySet()) {
            Cell c = e.getValue();
            if (c.n >= minSamples && normal - c.sum / c.n >= drop) {
                hot.put(e.getKey(), c);
            }
        }
        List<Zone> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long start : hot.keySet()) {
            if (!seen.add(start)) {
                continue;
            }
            List<Long> group = new ArrayList<>();
            List<Long> todo = new ArrayList<>();
            todo.add(start);
            while (!todo.isEmpty()) {
                long k = todo.remove(todo.size() - 1);
                group.add(k);
                for (int dx = -LINK; dx <= LINK; dx++) {
                    for (int dz = -LINK; dz <= LINK; dz++) {
                        long nk = key(cx(k) + dx, cz(k) + dz);
                        if (hot.containsKey(nk) && seen.add(nk)) {
                            todo.add(nk);
                        }
                    }
                }
            }
            double wx = 0;
            double wz = 0;
            double w = 0;
            double sum = 0;
            int n = 0;
            for (long k : group) {
                Cell c = hot.get(k);
                double avg = c.sum / c.n;
                double weight = (normal - avg) * c.n;
                wx += (cx(k) + 0.5) * CELL * weight;
                wz += (cz(k) + 0.5) * CELL * weight;
                w += weight;
                sum += c.sum;
                n += c.n;
            }
            double x = wx / w;
            double z = wz / w;
            double radius = CELL / 2.0;
            for (long k : group) {
                double dx = (cx(k) + 0.5) * CELL - x;
                double dz = (cz(k) + 0.5) * CELL - z;
                radius = Math.max(radius, Math.sqrt(dx * dx + dz * dz) + CELL / 2.0);
            }
            // one lonely cell is only a place that happened to be slow once; it needs a second cell or twice the samples
            if (group.size() >= 2 || n >= minSamples * 2) {
                out.add(new Zone(x, z, radius, sum / n, n, group.size()));
            }
        }
        out.sort((a, b) -> Double.compare(a.avgTps(), b.avgTps()));
        return out;
    }
}
