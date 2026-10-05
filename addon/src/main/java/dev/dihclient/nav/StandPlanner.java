package dev.dihclient.nav;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class StandPlanner {
    private StandPlanner() {
    }

    public record Choice(Nav.Cell cell, int covered, double score) {
    }

    private static long bucket(int x, int y, int z) {
        return Nav.key(x >> 2, y >> 2, z >> 2);
    }

    public static Choice best(Nav.Region region, List<int[]> open, Nav.Terrain world, double reach, double eye, double travelCost,
                              Predicate<Nav.Cell> forbidden) {
        if (open.isEmpty()) {
            return null;
        }
        Map<Long, List<int[]>> buckets = new HashMap<>();
        for (int[] b : open) {
            buckets.computeIfAbsent(bucket(b[0], b[1], b[2]), k -> new ArrayList<>()).add(b);
        }
        double r2 = reach * reach;
        List<Choice> top = new ArrayList<>();
        for (Nav.Cell c : region.cells.values()) {
            if (forbidden != null && forbidden.test(c)) {
                continue;
            }
            int n = count(buckets, c, eye, reach, r2, null);
            if (n <= 0) {
                continue;
            }
            top.add(new Choice(c, n, n - travelCost * c.cost));
        }
        if (top.isEmpty()) {
            return null;
        }
        top.sort((a, b) -> Double.compare(b.score(), a.score()));

        Choice best = null;
        for (int i = 0; i < Math.min(12, top.size()); i++) {
            Choice ch = top.get(i);
            int seen = count(buckets, ch.cell(), eye, reach, r2, world);
            if (seen <= 0) {
                continue;
            }
            Choice real = new Choice(ch.cell(), seen, seen - travelCost * ch.cell().cost);
            if (best == null || real.score() > best.score()) {
                best = real;
            }
        }
        return best;
    }

    private static int count(Map<Long, List<int[]>> buckets, Nav.Cell c, double eye, double reach, double r2, Nav.Terrain los) {
        double ex = c.x + 0.5, ey = c.y + eye, ez = c.z + 0.5;
        int n = 0;
        int x0 = (int) Math.floor(ex - reach) >> 2, x1 = (int) Math.floor(ex + reach) >> 2;
        int y0 = (int) Math.floor(ey - reach) >> 2, y1 = (int) Math.floor(ey + reach) >> 2;
        int z0 = (int) Math.floor(ez - reach) >> 2, z1 = (int) Math.floor(ez + reach) >> 2;
        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    List<int[]> list = buckets.get(Nav.key(bx, by, bz));
                    if (list == null) {
                        continue;
                    }
                    for (int[] b : list) {
                        double dx = b[0] + 0.5 - ex, dy = b[1] + 0.5 - ey, dz = b[2] + 0.5 - ez;
                        if (dx * dx + dy * dy + dz * dz <= r2 && (los == null || visible(los, ex, ey, ez, b))) {
                            n++;
                        }
                    }
                }
            }
        }
        return n;
    }

    static boolean visible(Nav.Terrain world, double ex, double ey, double ez, int[] b) {
        double tx = b[0] + 0.5, ty = b[1] + 0.5, tz = b[2] + 0.5;
        double dx = tx - ex, dy = ty - ey, dz = tz - ez;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(len / 0.25);
        for (int i = 1; i < steps; i++) {
            double f = (double) i / steps;
            int x = (int) Math.floor(ex + dx * f), y = (int) Math.floor(ey + dy * f), z = (int) Math.floor(ez + dz * f);
            if (x == b[0] && y == b[1] && z == b[2]) {
                break;
            }
            int manhattan = Math.abs(x - b[0]) + Math.abs(y - b[1]) + Math.abs(z - b[2]);
            if (manhattan <= 1) {
                continue;
            }
            if (world.support(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    public static List<int[]> coveredBy(Nav.Cell cell, List<int[]> open, Nav.Terrain world, double reach, double eye) {
        double ex = cell.x + 0.5, ey = cell.y + eye, ez = cell.z + 0.5;
        double r2 = reach * reach;
        List<int[]> out = new ArrayList<>();
        for (int[] b : open) {
            double dx = b[0] + 0.5 - ex, dy = b[1] + 0.5 - ey, dz = b[2] + 0.5 - ez;
            if (dx * dx + dy * dy + dz * dz <= r2 && visible(world, ex, ey, ez, b)) {
                out.add(b);
            }
        }
        return out;
    }
}
