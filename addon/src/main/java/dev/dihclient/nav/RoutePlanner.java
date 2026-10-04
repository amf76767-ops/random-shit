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

        private void finishLayer() {
            for (int[] b : this.layers.get(this.li)) {
                if (b.length > 3 && b[3] == 1) {
                    this.solid.add(Nav.key(b[0], b[1], b[2]));
                }
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
