package dev.dihclient.nav;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out the whole route of a build before the first block is placed: layer by layer, the spots to stand on, in the
 * order they are visited. A spot is chosen where the most open blocks can be placed from (reach and line of sight) minus
 * a little for the way there from the previous spot, so the builder walks as little as possible. Finished layers count as
 * solid ground for the next ones, so a second floor is planned from the first one.
 * <p>
 * The work is cut into slices ({@link Job#step}), so the game never freezes while a big build is planned. It knows nothing
 * about Minecraft; the host describes the world and the blocks.
 */
public final class RoutePlanner {
    private RoutePlanner() {
    }

    /** One place to stand and what is placed from there. */
    public record Stop(int x, int y, int z, int layer, int[][] blocks, double cost) {
    }

    public static final class Result {
        public final List<Stop> stops = new ArrayList<>();
        /** Blocks that no spot reaches from the ground (they need pillaring or scaffolding). */
        public int unreachable;
        public int blocks;
        /** Sum of the walking costs between the spots, about one per block walked. */
        public double walk;
        public boolean finished;
        public String failure;
        /** Blocks no spot reaches, and floating blocks with no ground to put a support on: {x, y, z}. At most {@link #MAX_MARKS}. */
        public final List<int[]> problems = new ArrayList<>();
        /** Support blocks the plan puts under floating parts (placed first, from the same stops): {x, y, z}. */
        public final List<int[]> supports = new ArrayList<>();
        public int supportCount;
        public int floating;
        public static final int MAX_MARKS = 3000;

        private final Set<Long> problemKeys = new HashSet<>();

        void problem(int[] b) {
            if (this.problems.size() < MAX_MARKS && this.problemKeys.add(Nav.key(b[0], b[1], b[2]))) {
                this.problems.add(new int[]{b[0], b[1], b[2]});
            }
        }

        /** Seconds, a rough guess: walking at about four blocks a second and some placing time. */
        public int estimateSeconds() {
            return (int) Math.round(this.walk / 4.0 + this.blocks / 8.0);
        }
    }

    /** One planning run. Blocks are {x, y, z, solid} with solid 1 when the placed block can be stood on. */
    public static final class Job {
        private static final double TRAVEL_COST = 0.12;

        private final Nav.Terrain base;
        private final List<List<int[]>> layers;
        private final List<Integer> layerIds;
        private final double reach;
        private final double eye;
        private final Nav.Options options = new Nav.Options();
        private final Set<Long> solid = new HashSet<>();
        /** Every block of the finished layers (solid or not): something a new block can be placed against. */
        private final Set<Long> placedAll = new HashSet<>();
        private final Nav.Terrain world;
        private final Result result = new Result();
        private int li;
        private List<int[]> open;
        private Set<Long> openKeys;
        private int cx;
        private int cy;
        private int cz;
        private boolean moved;
        private int total;
        private int placed;

        /**
         * @param layers   the open blocks of each layer, lowest first
         * @param layerIds the layer number of each entry of {@code layers}
         */
        public Job(Nav.Terrain base, int sx, int sy, int sz, List<List<int[]>> layers, List<Integer> layerIds, double reach, double eye, int maxFall) {
            this.base = base;
            this.world = new OverlayTerrain(base, this.solid);
            this.layers = layers;
            this.layerIds = layerIds;
            this.reach = reach;
            this.eye = eye;
            this.cx = sx;
            this.cy = sy;
            this.cz = sz;
            this.options.maxFall = Math.max(1, Math.min(4, maxFall));
            this.options.radius = 40;
            this.options.maxNodes = 14000;
            for (List<int[]> l : layers) {
                this.total += l.size();
            }
            this.result.blocks = this.total;
            if (!Nav.standable(this.world, sx, sy, sz)) {
                this.result.failure = "Not standing on solid ground";
                this.result.finished = true;
            }
        }

        public Result result() {
            return this.result;
        }

        public double progress() {
            return this.total == 0 ? 1.0 : Math.min(1.0, (double) this.placed / this.total);
        }

        /** Plans until the time is up. @return true when the plan is complete */
        public boolean step(long deadlineNanos) {
            while (!this.result.finished) {
                if (System.nanoTime() >= deadlineNanos) {
                    return false;
                }
                if (this.li >= this.layers.size()) {
                    this.result.finished = true;
                    break;
                }
                if (this.open == null) {
                    this.open = new ArrayList<>(this.layers.get(this.li));
                    this.openKeys = new HashSet<>();
                    for (int[] b : this.open) {
                        this.openKeys.add(Nav.key(b[0], b[1], b[2]));
                    }
                    this.planSupports();
                    this.moved = false;
                }
                if (this.open.isEmpty()) {
                    this.finishLayer();
                    continue;
                }
                this.oneStop();
            }
            return true;
        }

        /** Something a block can be placed against. */
        private boolean anchor(int x, int y, int z) {
            long k = Nav.key(x, y, z);
            return this.placedAll.contains(k) || this.world.support(x, y, z) || !this.world.passable(x, y, z);
        }

        private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        private static final int MAX_SUPPORT_DEPTH = 6;

        /**
         * Blocks of this layer that touch nothing (not the world, not an earlier layer, not a block of this layer that touches
         * something) cannot be placed. For every such floating group a column of supports is planned under its lowest block down to
         * the ground; the supports become open blocks of this layer, so the stops are chosen to reach them as well. A group with no
         * ground within {@value #MAX_SUPPORT_DEPTH} blocks is a problem (it needs scaffolding).
         */
        private void planSupports() {
            Set<Long> anchored = new HashSet<>();
            List<int[]> todo = new ArrayList<>();
            java.util.Map<Long, int[]> byKey = new java.util.HashMap<>();
            for (int[] b : this.open) {
                byKey.put(Nav.key(b[0], b[1], b[2]), b);
            }
            for (int[] b : this.open) {
                for (int[] d : SIDES) {
                    if (this.anchor(b[0] + d[0], b[1] + d[1], b[2] + d[2])) {
                        anchored.add(Nav.key(b[0], b[1], b[2]));
                        todo.add(b);
                        break;
                    }
                }
            }
            while (!todo.isEmpty()) {
                int[] b = todo.remove(todo.size() - 1);
                for (int[] d : SIDES) {
                    long k = Nav.key(b[0] + d[0], b[1] + d[1], b[2] + d[2]);
                    int[] n = byKey.get(k);
                    if (n != null && anchored.add(k)) {
                        todo.add(n);
                    }
                }
            }
            if (anchored.size() == this.open.size()) {
                return;
            }
            Set<Long> seen = new HashSet<>(anchored);
            List<int[]> added = new ArrayList<>();
            for (int[] start : new ArrayList<>(this.open)) {
                long sk = Nav.key(start[0], start[1], start[2]);
                if (!seen.add(sk)) {
                    continue;
                }
                // one floating group: find its lowest block
                List<int[]> group = new ArrayList<>();
                List<int[]> queue = new ArrayList<>();
                queue.add(start);
                int[] low = start;
                while (!queue.isEmpty()) {
                    int[] b = queue.remove(queue.size() - 1);
                    group.add(b);
                    if (b[1] < low[1]) {
                        low = b;
                    }
                    for (int[] d : SIDES) {
                        long k = Nav.key(b[0] + d[0], b[1] + d[1], b[2] + d[2]);
                        int[] n = byKey.get(k);
                        if (n != null && seen.add(k)) {
                            queue.add(n);
                        }
                    }
                }
                this.result.floating += group.size();
                List<int[]> column = new ArrayList<>();
                int y = low[1] - 1;
                boolean grounded = false;
                for (int i = 0; i < MAX_SUPPORT_DEPTH; i++, y--) {
                    if (this.anchor(low[0], y, low[2])) {
                        grounded = true;
                        break;
                    }
                    column.add(new int[]{low[0], y, low[2], 1, 2});
                    if (this.anchor(low[0], y - 1, low[2]) || this.anchor(low[0] + 1, y, low[2]) || this.anchor(low[0] - 1, y, low[2])
                            || this.anchor(low[0], y, low[2] + 1) || this.anchor(low[0], y, low[2] - 1)) {
                        grounded = true;
                        break;
                    }
                }
                if (!grounded) {
                    for (int[] b : group) {
                        this.result.problem(b);
                    }
                    continue;
                }
                added.addAll(column);
            }
            for (int[] b : added) {
                long k = Nav.key(b[0], b[1], b[2]);
                if (this.openKeys.add(k)) {
                    this.open.add(b);
                    this.total++;
                    this.result.supportCount++;
                    if (this.result.supports.size() < Result.MAX_MARKS) {
                        this.result.supports.add(new int[]{b[0], b[1], b[2]});
                    }
                }
            }
        }

        private void finishLayer() {
            for (int[] b : this.layers.get(this.li)) {
                if (b.length > 3 && b[3] == 1) {
                    this.solid.add(Nav.key(b[0], b[1], b[2]));
                }
                this.placedAll.add(Nav.key(b[0], b[1], b[2]));
            }
            for (int[] b : this.result.supports) {
                this.solid.add(Nav.key(b[0], b[1], b[2]));
                this.placedAll.add(Nav.key(b[0], b[1], b[2]));
            }
            this.li++;
            this.open = null;
        }

        private void oneStop() {
            Nav.Region region = Nav.explore(this.world, this.cx, this.cy, this.cz, this.options);
            StandPlanner.Choice choice = StandPlanner.best(region, this.open, this.world, this.reach, this.eye, TRAVEL_COST,
                    cell -> this.openKeys.contains(Nav.key(cell.x, cell.y, cell.z)) || this.openKeys.contains(Nav.key(cell.x, cell.y + 1, cell.z)));
            if (choice != null) {
                List<int[]> got = StandPlanner.coveredBy(choice.cell(), this.open, this.world, this.reach, this.eye);
                if (!got.isEmpty()) {
                    this.take(choice.cell(), got);
                    return;
                }
            }
            // nothing is placeable from the area around here: walk on towards the nearest open block once, then give up on the rest
            Nav.Cell next = this.moved ? null : this.towardsNearest(region);
            if (next == null || (next.x == this.cx && next.y == this.cy && next.z == this.cz)) {
                this.result.unreachable += this.open.size();
                for (int[] b : this.open) {
                    this.result.problem(b);
                }
                this.placed += this.open.size();
                this.open.clear();
                return;
            }
            this.moved = true;
            this.result.walk += next.cost;
            this.cx = next.x;
            this.cy = next.y;
            this.cz = next.z;
        }

        private void take(Nav.Cell cell, List<int[]> got) {
            int[][] blocks = got.toArray(new int[0][]);
            this.result.stops.add(new Stop(cell.x, cell.y, cell.z, this.layerIds.get(this.li), blocks, cell.cost));
            this.result.walk += cell.cost;
            for (int[] b : got) {
                this.openKeys.remove(Nav.key(b[0], b[1], b[2]));
            }
            this.open.removeAll(got);
            this.placed += got.size();
            this.cx = cell.x;
            this.cy = cell.y;
            this.cz = cell.z;
            this.moved = false;
        }

        private Nav.Cell towardsNearest(Nav.Region region) {
            int[] near = null;
            double nearD = Double.MAX_VALUE;
            for (int[] b : this.open) {
                double d = Math.abs(b[0] - this.cx) + Math.abs(b[2] - this.cz) + Math.abs(b[1] - this.cy) * 0.5;
                if (d < nearD) {
                    nearD = d;
                    near = b;
                }
            }
            if (near == null) {
                return null;
            }
            Nav.Cell best = null;
            double bestScore = Double.MAX_VALUE;
            for (Nav.Cell c : region.cells.values()) {
                if (this.openKeys.contains(Nav.key(c.x, c.y, c.z)) || this.openKeys.contains(Nav.key(c.x, c.y + 1, c.z))) {
                    continue;
                }
                double dx = c.x + 0.5 - (near[0] + 0.5), dz = c.z + 0.5 - (near[2] + 0.5), dy = c.y + this.eye - (near[1] + 0.5);
                double score = Math.sqrt(dx * dx + dz * dz) * 3.0 + Math.abs(dy) * 0.6 + c.cost * 0.05;
                if (score < bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            return best;
        }
    }
}
