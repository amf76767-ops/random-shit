package dev.dihclient.glue;

import dev.dihclient.autobuild.McTerrain;
import dev.dihclient.nav.Nav;
import dev.dihclient.nav.StandPlanner;
import dev.dihclient.util.KeyUtil;
import dev.dihclient.util.RotationUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.class_310;
import net.minecraft.class_746;

/**
 * Walks the player to the best stand spot for a set of blocks (see {@link StandPlanner}) on a searched path, with the
 * keys of the game. The same ideas as the AutoBuild pilot, for modules that are not part of AutoBuild.
 */
final class PathWalker {
    private static final class_310 mc = class_310.method_1551();
    private static final double EYE = 1.62;

    private final McTerrain terrain = new McTerrain();
    private List<Nav.Cell> path;
    private int index;
    private long goalKey;
    private int plannedAt = -1000;
    private int sampleTick;
    private double sampleX;
    private double sampleZ;
    private int stuck;
    private int arrived;
    private final Set<Long> avoid = new HashSet<>();
    private int failures;

    /** What happened in a call. */
    enum State { WALKING, ARRIVED, NO_ROUTE }

    void reset() {
        this.path = null;
        this.avoid.clear();
        this.failures = 0;
        this.arrived = 0;
        this.release();
    }

    int arrivedTicks() {
        return this.arrived;
    }

    /** Marks the current goal as useless (nothing could be done there); the next call picks another one. */
    void giveUpGoal() {
        if (this.path != null) {
            this.avoid.add(this.goalKey);
        }
        this.path = null;
    }

    /**
     * @param open   block coordinates {x, y, z} the player wants to be able to reach
     * @param reach  reach in blocks (a margin is taken off)
     * @param maxFall how deep a drop on the way may be
     */
    State walk(List<int[]> open, double reach, int maxFall) {
        class_746 p = mc.field_1724;
        if (p == null || mc.field_1687 == null) {
            return State.NO_ROUTE;
        }
        int tick = p.field_6012;
        if (this.path == null || tick - this.plannedAt > 160 || !this.onPath(p) || this.blockedAhead()) {
            if (!this.plan(open, reach, maxFall, tick)) {
                this.release();
                return State.NO_ROUTE;
            }
        }
        return this.follow(p, tick);
    }

    private boolean plan(List<int[]> open, double reach, int maxFall, int tick) {
        class_746 p = mc.field_1724;
        this.terrain.reset();
        int sx = (int) Math.floor(p.method_23317()), sy = (int) Math.floor(p.method_23318() + 0.01), sz = (int) Math.floor(p.method_23321());
        if (!Nav.standable(this.terrain, sx, sy, sz)) {
            if (Nav.standable(this.terrain, sx, sy - 1, sz)) {
                sy--;
            } else if (Nav.standable(this.terrain, sx, sy + 1, sz)) {
                sy++;
            } else {
                return false;
            }
        }
        Nav.Options o = new Nav.Options();
        o.maxFall = Math.max(1, Math.min(4, maxFall));
        Nav.Region region = Nav.explore(this.terrain, sx, sy, sz, o);
        StandPlanner.Choice c = StandPlanner.best(region, open, this.terrain, Math.max(2.5, reach - 0.4), EYE, 0.12,
                cell -> this.avoid.contains(Nav.key(cell.x, cell.y, cell.z)));
        if (c == null) {
            return false;
        }
        this.goalKey = Nav.key(c.cell().x, c.cell().y, c.cell().z);
        this.path = region.path(c.cell());
        this.index = this.path.size() > 1 ? 1 : 0;
        this.plannedAt = tick;
        this.sampleTick = tick;
        this.sampleX = p.method_23317();
        this.sampleZ = p.method_23321();
        this.stuck = 0;
        this.arrived = 0;
        return true;
    }

    private boolean onPath(class_746 p) {
        int i = Math.max(0, Math.min(this.index, this.path.size() - 1));
        for (int k = i; k >= Math.max(0, i - 1); k--) {
            Nav.Cell c = this.path.get(k);
            double dx = p.method_23317() - (c.x + 0.5), dz = p.method_23321() - (c.z + 0.5);
            if (dx * dx + dz * dz < 6.0 && Math.abs(p.method_23318() - c.y) < 3.0) {
                return true;
            }
        }
        return false;
    }

    private boolean blockedAhead() {
        this.terrain.reset();
        for (int k = this.index; k < Math.min(this.path.size(), this.index + 3); k++) {
            Nav.Cell c = this.path.get(k);
            if (!Nav.standable(this.terrain, c.x, c.y, c.z)) {
                return true;
            }
        }
        return false;
    }

    private State follow(class_746 p, int tick) {
        while (this.index < this.path.size()) {
            Nav.Cell c = this.path.get(this.index);
            double dx = c.x + 0.5 - p.method_23317(), dz = c.z + 0.5 - p.method_23321();
            boolean last = this.index == this.path.size() - 1;
            if (dx * dx + dz * dz < (last ? 0.09 : 0.3) && Math.abs(p.method_23318() - c.y) < 1.2) {
                this.index++;
            } else {
                break;
            }
        }
        if (this.index >= this.path.size()) {
            this.release();
            this.arrived++;
            return State.ARRIVED;
        }
        this.arrived = 0;
        Nav.Cell next = this.path.get(this.index);
        double dx = next.x + 0.5 - p.method_23317(), dz = next.z + 0.5 - p.method_23321();
        float want = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        p.method_36456(RotationUtil.approachAngle(p.method_36454(), want, 22.0F));
        p.method_36457(RotationUtil.approachAngle(p.method_36455(), 12.0F, 5.0F));
        if (tick - this.sampleTick >= 10) {
            double moved = Math.hypot(p.method_23317() - this.sampleX, p.method_23321() - this.sampleZ);
            this.stuck = moved < 0.25 && p.method_24828() ? this.stuck + 1 : 0;
            this.sampleTick = tick;
            this.sampleX = p.method_23317();
            this.sampleZ = p.method_23321();
            if (this.stuck >= 4) {
                this.avoid.add(this.goalKey);
                this.path = null;
                this.release();
                return ++this.failures > 8 ? State.NO_ROUTE : State.WALKING;
            }
        }
        boolean up = next.y > p.method_23318() + 0.4;
        boolean ahead = Math.hypot(dx, dz) < 1.6;
        mc.field_1690.field_1894.method_23481(true);
        mc.field_1690.field_1913.method_23481(false);
        mc.field_1690.field_1903.method_23481(p.method_24828() && ((up && ahead) || this.stuck >= 2));
        return State.WALKING;
    }

    /** Gives the movement keys back to the real keyboard. */
    void release() {
        if (mc.field_1690 != null) {
            mc.field_1690.field_1894.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1894));
            mc.field_1690.field_1903.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1903));
            mc.field_1690.field_1913.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1913));
        }
    }
}
