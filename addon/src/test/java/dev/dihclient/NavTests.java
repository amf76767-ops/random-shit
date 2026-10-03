package dev.dihclient;

import dev.dihclient.nav.Nav;
import dev.dihclient.nav.StandPlanner;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NavTests {
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

    /** A flat floor at y=-1 (so the feet stand at y=0) with solid blocks added by the test. */
    static final class Grid implements Nav.Terrain {
        final Set<Long> solid = new HashSet<>();
        final Set<Long> hazard = new HashSet<>();

        Grid(int size) {
            for (int x = -size; x <= size; x++) {
                for (int z = -size; z <= size; z++) {
                    solid.add(Nav.key(x, -1, z));
                }
            }
        }

        void block(int x, int y, int z) {
            solid.add(Nav.key(x, y, z));
        }

        void wall(int x, int z, int height) {
            for (int y = 0; y < height; y++) {
                block(x, y, z);
            }
        }

        public boolean passable(int x, int y, int z) {
            return !solid.contains(Nav.key(x, y, z)) && !hazard.contains(Nav.key(x, y, z));
        }

        public boolean support(int x, int y, int z) {
            return solid.contains(Nav.key(x, y, z));
        }

        public boolean hazard(int x, int y, int z) {
            return hazard.contains(Nav.key(x, y, z));
        }
    }

    public static void main(String[] args) {
        Nav.Options o = new Nav.Options();

        Grid flat = new Grid(12);
        Nav.Region r = Nav.explore(flat, 0, 0, 0, o);
        check(r.get(5, 0, 5) != null, "flat floor: far cell reachable");
        check(r.get(5, 0, 0).cost > 4.9f && r.get(5, 0, 0).cost < 5.1f, "5 steps cost about 5");

        Grid wall1 = new Grid(12);
        for (int z = -12; z <= 12; z++) {
            wall1.block(3, 0, z); // a wall one block high across the whole floor
        }
        r = Nav.explore(wall1, 0, 0, 0, o);
        check(r.get(3, 1, 0) != null && r.get(6, 0, 0) != null, "one block high wall: climbed by a jump and left on the other side");

        Grid wall2 = new Grid(12);
        for (int z = -12; z <= 12; z++) {
            wall2.wall(3, z, 2);
        }
        r = Nav.explore(wall2, 0, 0, 0, o);
        check(r.get(5, 0, 0) == null, "two blocks high wall: the other side is not reachable");
        check(r.get(2, 0, 0) != null, "two blocks high wall: this side is");
        check(r.get(3, 2, 0) == null, "no standing on top of a two high wall from the ground");

        Grid around = new Grid(12);
        for (int z = -3; z <= 3; z++) {
            around.wall(3, z, 3);
        }
        r = Nav.explore(around, 0, 0, 0, o);
        Nav.Cell far = r.get(6, 0, 0);
        check(far != null, "wall with an end: reached around it");
        List<Nav.Cell> path = r.path(far);
        boolean detour = path.stream().anyMatch(c -> Math.abs(c.z) >= 4);
        check(detour, "the path goes around the end of the wall");
        check(path.stream().noneMatch(c -> c.x == 3 && Math.abs(c.z) <= 3), "the path never goes through the wall");

        // stairs of blocks 1,2 -> reachable by jumps
        Grid stairs = new Grid(12);
        stairs.block(3, 0, 0);
        stairs.block(4, 0, 0);
        stairs.block(4, 1, 0);
        stairs.block(5, 0, 0);
        stairs.block(5, 1, 0);
        stairs.block(5, 2, 0);
        r = Nav.explore(stairs, 0, 0, 0, o);
        check(r.get(5, 3, 0) != null, "a staircase of blocks is climbed to the top");

        // drop
        Grid pit = new Grid(12);
        for (int x = 2; x <= 6; x++) {
            for (int z = -12; z <= 12; z++) {
                pit.solid.remove(Nav.key(x, -1, z));
                pit.block(x, -3, z); // 2 blocks lower
            }
        }
        r = Nav.explore(pit, 0, 0, 0, o);
        check(r.get(4, -2, 0) != null, "a drop of 2 blocks is taken");
        o.maxFall = 1;
        r = Nav.explore(pit, 0, 0, 0, o);
        check(r.get(4, -2, 0) == null, "a drop of 2 blocks is refused when maxFall is 1");
        o.maxFall = 3;

        // hazards
        Grid lava = new Grid(12);
        lava.hazard.add(Nav.key(2, 0, 0));
        r = Nav.explore(lava, 0, 0, 0, o);
        check(r.get(2, 0, 0) == null, "a hazard cell is not entered");
        check(r.get(3, 0, 0) != null, "but it is walked around");

        // planner: blocks on a ring around (0,0), the best stop is in the middle
        Grid site = new Grid(14);
        List<int[]> open = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) == 3 || Math.abs(z) == 3) {
                    open.add(new int[]{x + 10, 0, z});
                }
            }
        }
        r = Nav.explore(site, 0, 0, 0, o);
        StandPlanner.Choice c = StandPlanner.best(r, open, site, 4.4, 1.62, 0.1, null);
        check(c != null && c.covered() >= open.size() - 4, "planner finds a spot that covers (almost) the whole ring: " + (c == null ? 0 : c.covered()) + "/" + open.size());
        check(c != null && Math.abs(c.cell().x - 10) <= 1 && Math.abs(c.cell().z) <= 1, "that spot is in the middle of the ring");

        // planner prefers the near spot when coverage is equal
        List<int[]> two = new ArrayList<>();
        two.add(new int[]{1, 0, 3});
        two.add(new int[]{11, 0, 3});
        r = Nav.explore(site, 0, 0, 0, o);
        c = StandPlanner.best(r, two, site, 4.4, 1.62, 0.1, null);
        check(c != null && c.covered() >= 1, "planner returns something for two far apart blocks");

        // hidden block: a thick wall between the spot and the block is not 'seen'
        Grid hidden = new Grid(14);
        for (int y = 0; y < 4; y++) {
            for (int z = -6; z <= 6; z++) {
                hidden.block(2, y, z);
                hidden.block(3, y, z);
            }
        }
        List<int[]> behind = new ArrayList<>();
        behind.add(new int[]{5, 1, 0});
        r = Nav.explore(hidden, 0, 0, 0, o);
        Nav.Cell here = r.get(0, 0, 0);
        List<int[]> single = behind;
        StandPlanner.Choice none = StandPlanner.best(r, single, hidden, 4.4, 1.62, 0.1, cell -> cell.x > 1);
        check(none == null || none.cell().x <= 1 && none.covered() == 0, "a block behind a thick wall is not counted (" + (none == null ? "none" : none.covered()) + ")");

        // forbidden cells are skipped
        r = Nav.explore(site, 0, 0, 0, o);
        c = StandPlanner.best(r, open, site, 4.4, 1.62, 0.1, cell -> true);
        check(c == null, "nothing is chosen when every cell is forbidden");

        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
