package dev.dihclient.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Finds where a walking player can go: a Dijkstra search over "standing cells" (the cell of the feet). It knows
 * flat steps, steps up by one block (a jump), diagonal steps and drops of up to {@code maxFall} blocks, and nothing
 * else: a wall two blocks high is a wall. It knows nothing about Minecraft; the host describes the world.
 */
public final class Nav {
    private Nav() {
    }

    /** The world, as seen by the search. Coordinates are block coordinates. */
    public interface Terrain {
        /** The body (feet and head cell) may be in this cell: no collision, nothing that hurts. */
        boolean passable(int x, int y, int z);

        /** A player can stand on top of this cell (it has a collision box). */
        boolean support(int x, int y, int z);

        /** Standing next to or on it hurts (lava, fire, cactus, magma ...). */
        boolean hazard(int x, int y, int z);
    }

    public static final class Options {
        public int maxNodes = 9000;
        public int radius = 28;
        public int maxFall = 3;
        public boolean diagonals = true;
    }

    public static final class Cell {
        public final int x;
        public final int y;
        public final int z;
        public float cost;
        public Cell parent;
        boolean closed;

        Cell(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final class Region {
        public final Map<Long, Cell> cells = new HashMap<>();
        public Cell start;

        public Cell get(int x, int y, int z) {
            return this.cells.get(key(x, y, z));
        }

        /** Cells from the start to {@code goal}, the start first. */
        public List<Cell> path(Cell goal) {
            List<Cell> out = new ArrayList<>();
            for (Cell c = goal; c != null; c = c.parent) {
                out.add(c);
            }
            Collections.reverse(out);
            return out;
        }
    }

    public static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y + 2048 & 0xFFFL);
    }

    public static boolean standable(Terrain t, int x, int y, int z) {
        return t.passable(x, y, z) && t.passable(x, y + 1, z) && t.support(x, y - 1, z) && !t.hazard(x, y, z) && !t.hazard(x, y - 1, z);
    }

    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /** Every cell the player can reach from (sx, sy, sz) with the cheapest way to it. */
    public static Region explore(Terrain t, int sx, int sy, int sz, Options o) {
        Region r = new Region();
        Cell start = new Cell(sx, sy, sz);
        r.start = start;
        r.cells.put(key(sx, sy, sz), start);
        PriorityQueue<Cell> open = new PriorityQueue<>((a, b) -> Float.compare(a.cost, b.cost));
        open.add(start);
        int expanded = 0;
        while (!open.isEmpty() && expanded < o.maxNodes) {
            Cell c = open.poll();
            if (c.closed) {
                continue;
            }
            c.closed = true;
            expanded++;
            for (int[] d : CARDINAL) {
                int nx = c.x + d[0], nz = c.z + d[1];
                if (Math.abs(nx - sx) > o.radius || Math.abs(nz - sz) > o.radius) {
                    continue;
                }
                if (standable(t, nx, c.y, nz)) {
                    relax(r, open, c, nx, c.y, nz, 1.0f);
                } else if (t.passable(c.x, c.y + 2, c.z) && standable(t, nx, c.y + 1, nz) && Math.abs(c.y + 1 - sy) <= 24) {
                    relax(r, open, c, nx, c.y + 1, nz, 1.7f); // a jump
                } else if (t.passable(nx, c.y, nz) && t.passable(nx, c.y + 1, nz)) {
                    for (int k = 1; k <= o.maxFall; k++) {
                        if (!t.passable(nx, c.y - k, nz)) {
                            break;
                        }
                        if (standable(t, nx, c.y - k, nz)) {
                            relax(r, open, c, nx, c.y - k, nz, 1.2f + 0.5f * k);
                            break;
                        }
                    }
                }
            }
            if (o.diagonals) {
                for (int[] d : DIAGONAL) {
                    int nx = c.x + d[0], nz = c.z + d[1];
                    if (Math.abs(nx - sx) > o.radius || Math.abs(nz - sz) > o.radius) {
                        continue;
                    }
                    // both neighbours of the corner must be free, or the player gets stuck on the edge
                    if (standable(t, nx, c.y, nz) && t.passable(c.x + d[0], c.y, c.z) && t.passable(c.x + d[0], c.y + 1, c.z)
                            && t.passable(c.x, c.y, c.z + d[1]) && t.passable(c.x, c.y + 1, c.z + d[1])) {
                        relax(r, open, c, nx, c.y, nz, 1.42f);
                    }
                }
            }
        }
        return r;
    }

    private static void relax(Region r, PriorityQueue<Cell> open, Cell from, int x, int y, int z, float step) {
        long k = key(x, y, z);
        Cell n = r.cells.get(k);
        float cost = from.cost + step;
        if (n == null) {
            n = new Cell(x, y, z);
            n.cost = cost;
            n.parent = from;
            r.cells.put(k, n);
            open.add(n);
        } else if (cost < n.cost && !n.closed) {
            n.cost = cost;
            n.parent = from;
            open.add(n);
        }
    }
}
