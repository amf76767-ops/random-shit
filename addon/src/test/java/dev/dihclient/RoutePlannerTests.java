package dev.dihclient;

import dev.dihclient.nav.Nav;
import dev.dihclient.nav.RoutePlanner;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RoutePlannerTests {
    static int failed;
    static int passed;

    static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + what);
        }
    }

    /** A flat world: solid ground up to y = 0 (the top block), air above, optional extra solid blocks. */
    static final class Flat implements Nav.Terrain {
        final Set<Long> solid = new HashSet<>();

        public boolean passable(int x, int y, int z) {
            return y > 0 && !this.solid.contains(Nav.key(x, y, z));
        }

        public boolean support(int x, int y, int z) {
            return y <= 0 || this.solid.contains(Nav.key(x, y, z));
        }

        public boolean hazard(int x, int y, int z) {
            return false;
        }
    }

    static List<List<int[]>> box(int x0, int z0, int w, int d, int layers) {
        List<List<int[]>> out = new ArrayList<>();
        for (int y = 0; y < layers; y++) {
            List<int[]> layer = new ArrayList<>();
            for (int x = x0; x < x0 + w; x++) {
                for (int z = z0; z < z0 + d; z++) {
                    boolean wall = x == x0 || x == x0 + w - 1 || z == z0 || z == z0 + d - 1;
                    if (wall || y == layers - 1) { // a hollow box with a roof
                        layer.add(new int[]{x, 1 + y, z, 1});
                    }
                }
            }
            out.add(layer);
        }
        return out;
    }

    static RoutePlanner.Result run(Flat world, List<List<int[]>> layers, double reach) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < layers.size(); i++) {
            ids.add(i);
        }
        RoutePlanner.Job job = new RoutePlanner.Job(world, 0, 1, 0, layers, ids, reach, 1.62, 3);
        int slices = 0;
        while (!job.step(System.nanoTime() + 2_000_000L)) {
            slices++;
            check(slices < 100000, "the job finishes");
            if (slices >= 100000) {
                break;
            }
        }
        return job.result();
    }

    public static void main(String[] args) {
        // a 12 x 12 hollow box, 4 layers + roof, from the ground
        Flat world = new Flat();
        List<List<int[]>> layers = box(5, 5, 12, 12, 5);
        int total = 0;
        for (List<int[]> l : layers) {
            total += l.size();
        }
        RoutePlanner.Result r = run(world, layers, 4.1);
        check(r.finished && r.failure == null, "finished without failure");
        int covered = 0;
        for (RoutePlanner.Stop s : r.stops) {
            covered += s.blocks().length;
        }
        check(covered + r.unreachable == total, "every block is covered or counted unreachable: " + covered + " + " + r.unreachable + " vs " + total);
        check(r.unreachable == 0, "a 5 high box with a roof is all reachable by standing on the layers below, unreachable=" + r.unreachable);
        check(r.stops.size() < total / 4, "far fewer stops than blocks: " + r.stops.size() + " for " + total);
        // each stop stands on something and its blocks are within reach
        for (RoutePlanner.Stop s : r.stops) {
            for (int[] b : s.blocks()) {
                double dx = b[0] + 0.5 - (s.x() + 0.5), dy = b[1] + 0.5 - (s.y() + 1.62), dz = b[2] + 0.5 - (s.z() + 0.5);
                check(dx * dx + dy * dy + dz * dz <= 4.1 * 4.1 + 1e-6, "block within reach of its stop");
            }
        }
        // the layers are visited in order
        int last = -1;
        boolean ordered = true;
        for (RoutePlanner.Stop s : r.stops) {
            ordered &= s.layer() >= last;
            last = s.layer();
        }
        check(ordered, "stops come layer by layer");
        check(r.estimateSeconds() > 0, "an estimate exists");

        // nothing is reachable far above the ground without a floor
        Flat sky = new Flat();
        List<List<int[]>> tower = new ArrayList<>();
        List<int[]> high = new ArrayList<>();
        high.add(new int[]{3, 40, 3, 1});
        tower.add(high);
        RoutePlanner.Result t = run(sky, tower, 4.5);
        check(t.unreachable == 1 && t.stops.isEmpty(), "a block 40 high is unreachable from the ground");

        check(t.problems.size() == 1, "the block 40 high is marked as a problem");

        // a floating block 4 above the ground gets a column of supports down to the ground
        Flat field = new Flat();
        List<List<int[]>> floating = new ArrayList<>();
        List<int[]> fl = new ArrayList<>();
        fl.add(new int[]{3, 4, 3, 1});
        fl.add(new int[]{4, 4, 3, 1});
        floating.add(fl);
        RoutePlanner.Result fp = run(field, floating, 4.5);
        check(fp.supportCount == 3, "three supports under the floating pair: " + fp.supportCount);
        check(fp.floating == 2, "two floating blocks: " + fp.floating);
        check(fp.problems.isEmpty(), "no problem when supports can reach the ground");
        int fc = 0;
        for (RoutePlanner.Stop st : fp.stops) {
            fc += st.blocks().length;
        }
        check(fc == 5 && fp.unreachable == 0, "the blocks and their supports are all covered: " + fc);

        // blocks that touch the ground need no support
        RoutePlanner.Result grounded = run(new Flat(), box(5, 5, 4, 4, 1), 4.5);
        check(grounded.supportCount == 0 && grounded.problems.isEmpty(), "blocks on the ground need no supports");

        // a start that is not standable fails clearly
        Flat bad = new Flat();
        RoutePlanner.Job job = new RoutePlanner.Job(bad, 0, 5, 0, box(5, 5, 4, 4, 1), List.of(0), 4.5, 1.62, 3);
        check(job.step(System.nanoTime() + 1_000_000L) && job.result().failure != null, "start in the air fails");

        // empty plan
        RoutePlanner.Result empty = run(new Flat(), new ArrayList<>(), 4.5);
        check(empty.finished && empty.stops.isEmpty(), "nothing to build is a finished empty plan");

        // a wall of blocks between the walker and the target on the far side still gets covered (walks around)
        Flat wall = new Flat();
        List<List<int[]>> far = new ArrayList<>();
        List<int[]> row = new ArrayList<>();
        for (int x = 30; x < 40; x++) {
            row.add(new int[]{x, 1, 0, 1});
        }
        far.add(row);
        RoutePlanner.Result f = run(wall, far, 4.5);
        check(f.unreachable == 0 && !f.stops.isEmpty(), "a build 30 blocks away is reached by walking, unreachable=" + f.unreachable);

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
