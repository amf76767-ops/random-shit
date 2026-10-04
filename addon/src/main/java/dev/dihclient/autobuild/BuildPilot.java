package dev.dihclient.autobuild;

import dev.dihclient.DIHClient;
import dev.dihclient.nav.Nav;
import dev.dihclient.nav.StandPlanner;
import dev.dihclient.util.RotationUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_1792;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_746;

/**
 * The walking of AutoBuild. The old code walked in a straight line to the nearest open block and jumped whenever it
 * bumped into something, so it ran into walls two blocks high and stayed there. This one
 * <ol>
 *   <li>looks at the open blocks of the current layer and picks the spot from which the most of them can be placed
 *       (reach and line of sight), with a small penalty for the way there;</li>
 *   <li>walks there on a path that was searched on the real world: steps up by one block, drops, around walls;</li>
 *   <li>when no spot sees a block, walks to the reachable cell closest to it and pillars up from there (the old code
 *       does that), and skips spots that did not work out.</li>
 * </ol>
 * {@link #walkLayer} replaces the "walk to the next layer / the next open block" call of BuildRuntime.tick,
 * {@link #walkPoint} the walk to a given point (re-positioning). Anything unexpected hands over to the old walking.
 */
public final class BuildPilot {
    private static final class_310 mc = class_310.method_1551();
    private static final McTerrain TERRAIN = new McTerrain();
    private static final double EYE = 1.62;
    private static final int REPLAN_AFTER = 160;

    /** Switched by the "Smart Path" setting of AutoBuild. */
    public static volatile boolean enabled = true;

    private static final class Trip {
        List<Nav.Cell> path;
        int index;
        long goalKey;
        boolean covers;
        boolean layerMode;
        int plannedAt;
        int sampleTick;
        double sampleX;
        double sampleZ;
        int stuck;
        int replans;
        int arrived;
        int towered;
        Set<Long> avoid = new HashSet<>();
        int avoidSince;
    }

    private static Trip trip;
    private static int failures;
    private static int pausedUntil;
    /** After the normal search found no way, the next ones look much further (until this tick). */
    private static int bigUntil;
    private static int lastGaveUp = -1000;
    private static int noPlanUntil;

    // ---- stalls: too long at one spot -> park the blocks around it for a while, work elsewhere, come back later
    private static final int STALL_SPOT_TICKS = 140;
    private static final int STALL_DONE_TICKS = 900;
    private static final double PARK_RADIUS = 6.0;
    /** Index of a plan block -> tick until which the pilot leaves it alone. */
    private static final Map<Integer, Integer> PARKED = new java.util.HashMap<>();
    private static Object parkedPlan;
    private static int parkRounds;
    private static int lastCall = -1000;
    private static int lastDone = -1;
    private static int anchorTick;
    private static int doneTick;
    private static double anchorX;
    private static double anchorY;
    private static double anchorZ;

    private BuildPilot() {
    }

    public static void walkLayer(BuildRuntime rt, class_243 target, double reach) {
        run(rt, target, reach, true);
    }

    public static void walkPoint(BuildRuntime rt, class_243 target, double reach) {
        run(rt, target, reach, false);
    }

    private static void run(BuildRuntime rt, class_243 target, double reach, boolean layer) {
        boolean handled = false;
        int now = mc.field_1724 == null ? 0 : mc.field_1724.field_6012;
        if (layer && enabled) {
            try {
                stallCheck(rt, target, now);
            } catch (Throwable t) {
                DIHClient.LOG.warn("[DIHClient] AutoBuild stall check failed", t);
            }
        }
        if (enabled && now >= pausedUntil) {
            try {
                handled = drive(rt, target, reach, layer);
            } catch (Throwable t) {
                // not for the rest of the session: the next try is in 10 seconds, and after 5 failures in a row the old walking stays
                failures++;
                pausedUntil = failures >= 5 ? Integer.MAX_VALUE : now + 200;
                trip = null;
                DIHClient.LOG.warn("[DIHClient] smart path failed (" + failures + "), the old walking takes over for a while", t);
            }
            if (handled) {
                failures = 0;
            }
        }
        if (!handled) {
            oldWalk(rt, layer ? unparkedTarget(rt, target) : target, reach);
        }
    }

    /** While blocks are parked, even the old walking aims at the nearest block that is not. */
    private static class_243 unparkedTarget(BuildRuntime rt, class_243 target) {
        if (PARKED.isEmpty() || mc.field_1724 == null) {
            return target;
        }
        try {
            class_243 best = target;
            double bestD = Double.MAX_VALUE;
            for (int[] b : openBlocks(rt)) {
                class_243 c = new class_243(b[0] + 0.5, b[1] + 0.5, b[2] + 0.5);
                double d = mc.field_1724.method_5707(c);
                if (d < bestD) {
                    bestD = d;
                    best = c;
                }
            }
            return best;
        } catch (ReflectiveOperationException e) {
            return target;
        }
    }

    /**
     * Called every tick the build wants to walk. Staying around one spot for several seconds without getting a block done (jumping at a
     * wall, a block that cannot be placed from anywhere reachable) parks the open blocks around that spot; the pilot then works on the
     * rest of the layer and comes back to the parked ones later.
     */
    private static void stallCheck(BuildRuntime rt, class_243 target, int now) throws ReflectiveOperationException {
        class_746 p = mc.field_1724;
        if (p == null) {
            return;
        }
        int done = Priv.DONECOUNT.getInt(rt);
        boolean fresh = now - lastCall > 60;
        lastCall = now;
        if (fresh || done != lastDone) {
            if (done != lastDone) {
                parkRounds = 0;
            }
            lastDone = done;
            doneTick = now;
            anchorTick = now;
            anchorX = p.method_23317();
            anchorY = p.method_23318();
            anchorZ = p.method_23321();
            return;
        }
        if (Math.hypot(p.method_23317() - anchorX, p.method_23321() - anchorZ) > 3.5 || Math.abs(p.method_23318() - anchorY) > 3.0) {
            anchorTick = now;
            anchorX = p.method_23317();
            anchorY = p.method_23318();
            anchorZ = p.method_23321();
        }
        if (now - anchorTick > STALL_SPOT_TICKS || now - doneTick > STALL_DONE_TICKS) {
            anchorTick = now;
            doneTick = now;
            park(rt, target, now);
        }
    }

    private static void park(BuildRuntime rt, class_243 target, int now) throws ReflectiveOperationException {
        List<int[]> open = openBlocks(rt);
        if (open.isEmpty()) {
            return;
        }
        class_243 center = target;
        Trip t = trip;
        if (t != null && t.path != null && !t.path.isEmpty()) {
            Nav.Cell goal = t.path.get(t.path.size() - 1);
            center = new class_243(goal.x + 0.5, goal.y + EYE, goal.z + 0.5);
        }
        int[] nearest = null;
        double nearestD = Double.MAX_VALUE;
        int until = now + Math.min(6000, 1200 * (1 + parkRounds));
        int parked = 0;
        for (int[] b : open) {
            double d = center.method_1022(new class_243(b[0] + 0.5, b[1] + 0.5, b[2] + 0.5));
            if (d < nearestD) {
                nearestD = d;
                nearest = b;
            }
            if (d <= PARK_RADIUS) {
                PARKED.put(b[3], until);
                parked++;
            }
        }
        if (parked == 0 && nearest != null) {
            PARKED.put(nearest[3], until);
            parked = 1;
        }
        if (t != null) {
            t.avoid.add(t.goalKey);
        }
        trip = null;
        noPlanUntil = 0;
        Priv.RELEASE.invoke(rt);
        Priv.STATUS.set(rt, "Too long at one spot: moving on, back to " + parked + " blocks later");
        DIHClient.LOG.info("[DIHClient] AutoBuild: parked " + parked + " blocks that could not be done from here, working elsewhere first");
    }

    // ---- reflection into the private parts of BuildRuntime

    private static final class Priv {
        static final Field PLAN = f("plan"), DONE = f("done"), ATTEMPTS = f("attempts"), LAYER = f("layer"), START = f("layerStart"),
                END = f("layerEnd"), DEFERRED = f("lastDeferred"), SETTINGS = f("lastSettings"), WALKING = f("walking"), STATUS = f("status"),
                MISSING = f("missing"), DONECOUNT = f("doneCount"), TOWER = f("towerBase"), SNEAKING = f("sneaking"), STUCK = f("stuckTicks");
        static final Method WALK = m("walkTo", class_243.class, double.class), RELEASE = m("releaseKeys"),
                SNEAK = m("sneakKey", boolean.class), TOWER_START = m("startTower", BuildRuntime.Settings.class);

        static Field f(String name) {
            try {
                Field f = BuildRuntime.class.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        static Method m(String name, Class<?>... args) {
            try {
                Method m = BuildRuntime.class.getDeclaredMethod(name, args);
                m.setAccessible(true);
                return m;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static Method legacyWalk;

    private static void oldWalk(BuildRuntime rt, class_243 target, double reach) {
        try {
            // the old walking can climb a wall with support blocks, so it gets a short try; when it only jumps against the wall
            // (no blocks to climb with, or still stuck after two seconds) it is stopped instead
            BuildRuntime.Settings cfg = (BuildRuntime.Settings) Priv.SETTINGS.get(rt);
            boolean canClimb = cfg != null && cfg.supports && Priv.STUCK.getInt(rt) <= 40;
            if (!canClimb && wallAhead(target)) {
                // the old walking would run into it and jump against it for ever: stand still and say why
                Priv.RELEASE.invoke(rt);
                Priv.STATUS.set(rt, "Blocked by a wall: no way around found");
                int tick = mc.field_1724.field_6012;
                if (tick - lastGaveUp > 200) {
                    lastGaveUp = tick;
                    DIHClient.LOG.info("[DIHClient] AutoBuild: a wall two blocks high is in the way and no route around it was found");
                }
                return;
            }
            if (legacyWalk == null) {
                legacyWalk = BuildRuntime.class.getDeclaredMethod("walkTo", class_243.class, double.class);
                legacyWalk.setAccessible(true);
            }
            legacyWalk.invoke(rt, target, reach);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** True when the next block on the way to the target is a wall the player cannot jump over (feet and head cell both blocked). */
    private static boolean wallAhead(class_243 target) {
        class_746 p = mc.field_1724;
        if (p == null || mc.field_1687 == null) {
            return false;
        }
        double dx = target.field_1352 - p.method_23317(), dz = target.field_1350 - p.method_23321();
        double len = Math.hypot(dx, dz);
        if (len < 0.8) {
            return false;
        }
        TERRAIN.reset();
        int y = (int) Math.floor(p.method_23318() + 0.01);
        for (double d : new double[]{0.9, 1.5}) {
            int x = (int) Math.floor(p.method_23317() + dx / len * d), z = (int) Math.floor(p.method_23321() + dz / len * d);
            if (!TERRAIN.passable(x, y, z) && !TERRAIN.passable(x, y + 1, z) && !TERRAIN.hazard(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /** The cells of the route that is being walked (for the path preview), or null. */
    public static List<Nav.Cell> routeForPreview() {
        Trip t = trip;
        return t == null || t.path == null || t.index > t.path.size() ? null : t.path.subList(Math.max(0, t.index - 1), t.path.size());
    }

    /** True when the route ends at a spot that sees open blocks, false when it only leads as close as possible. */
    public static boolean routeCovers() {
        Trip t = trip;
        return t != null && t.covers;
    }

    // ---- the pilot

    private static boolean drive(BuildRuntime rt, class_243 target, double reach, boolean layerMode) throws ReflectiveOperationException {
        class_746 p = mc.field_1724;
        BuildRuntime.Settings s = (BuildRuntime.Settings) Priv.SETTINGS.get(rt);
        if (p == null || mc.field_1687 == null || s == null || s.mode != BuildRuntime.Mode.AUTO) {
            return false;
        }
        if (p.method_5799() && !p.method_24828()) {
            return false; // swimming: the old code knows how to get out of the water
        }
        int tick = p.field_6012;
        if (tick < noPlanUntil && trip == null) {
            return false; // nothing was found a moment ago; searching again every tick would only cost frames
        }
        Trip t = trip;
        if (t == null || t.layerMode != layerMode || tick - t.plannedAt > REPLAN_AFTER || !onPath(t, p) || blockedAhead(t)) {
            t = plan(rt, s, target, reach, layerMode, tick, t);
            if (t == null) {
                noPlanUntil = tick + 15;
                return false;
            }
            trip = t;
        }
        return follow(rt, s, t, p, tick);
    }

    private static boolean onPath(Trip t, class_746 p) {
        if (t.path == null || t.path.isEmpty()) {
            return false;
        }
        int i = Math.max(0, Math.min(t.index, t.path.size() - 1));
        Nav.Cell c = t.path.get(i);
        double dx = p.method_23317() - (c.x + 0.5), dz = p.method_23321() - (c.z + 0.5), dy = p.method_23318() - c.y;
        double near = dx * dx + dz * dz;
        if (near < 6.0 && Math.abs(dy) < 3.0) {
            return true;
        }
        if (i > 0) {
            Nav.Cell b = t.path.get(i - 1);
            dx = p.method_23317() - (b.x + 0.5);
            dz = p.method_23321() - (b.z + 0.5);
            return dx * dx + dz * dz < 6.0 && Math.abs(p.method_23318() - b.y) < 3.0;
        }
        return false;
    }

    /** The next few cells of the path must still be places to stand (a block was placed there, or the world changed). */
    private static boolean blockedAhead(Trip t) {
        TERRAIN.reset();
        for (int k = t.index; k < Math.min(t.path.size(), t.index + 3); k++) {
            Nav.Cell c = t.path.get(k);
            if (!Nav.standable(TERRAIN, c.x, c.y, c.z)) {
                return true;
            }
        }
        return false;
    }

    private static Trip plan(BuildRuntime rt, BuildRuntime.Settings s, class_243 target, double reach, boolean layerMode, int tick, Trip old)
            throws ReflectiveOperationException {
        class_746 p = mc.field_1724;
        TERRAIN.reset();
        int sx = (int) Math.floor(p.method_23317()), sy = (int) Math.floor(p.method_23318() + 0.01), sz = (int) Math.floor(p.method_23321());
        if (!Nav.standable(TERRAIN, sx, sy, sz)) {
            if (Nav.standable(TERRAIN, sx, sy - 1, sz)) {
                sy--;
            } else if (Nav.standable(TERRAIN, sx, sy + 1, sz)) {
                sy++;
            } else {
                return null;
            }
        }
        Nav.Options o = new Nav.Options();
        o.maxFall = Math.max(1, Math.min(4, s.maxFall > 0 ? s.maxFall : 1));
        if (tick < bigUntil) { // the normal search found no way before: look much further around walls
            o.radius = 64;
            o.maxNodes = 45000;
        }
        Nav.Region region = Nav.explore(TERRAIN, sx, sy, sz, o);

        Trip n = new Trip();
        n.layerMode = layerMode;
        n.plannedAt = tick;
        n.sampleTick = tick;
        n.sampleX = p.method_23317();
        n.sampleZ = p.method_23321();
        if (old != null && old.layerMode == layerMode && tick - old.avoidSince < 600) {
            n.avoid = old.avoid;
            n.avoidSince = old.avoidSince;
            n.replans = old.replans;
            n.towered = old.towered;
        } else {
            n.avoidSince = tick;
        }

        Nav.Cell goal = null;
        if (layerMode) {
            List<int[]> open = openBlocks(rt);
            if (!open.isEmpty()) {
                if (!PARKED.isEmpty()) {
                    // BuildRuntime's own target may be a parked block: aim at the nearest block that is not
                    double best = Double.MAX_VALUE;
                    for (int[] b : open) {
                        double d = p.method_5707(new class_243(b[0] + 0.5, b[1] + 0.5, b[2] + 0.5));
                        if (d < best) {
                            best = d;
                            target = new class_243(b[0] + 0.5, b[1] + 0.5, b[2] + 0.5);
                        }
                    }
                }
                Set<Long> openKeys = new HashSet<>();
                for (int[] b : open) {
                    openKeys.add(Nav.key(b[0], b[1], b[2]));
                }
                // the planned route first (see BuildPlannerModule), the own choice for what it does not cover
                goal = RoutePlans.nextStop((BuildPlan) Priv.PLAN.get(rt), Priv.LAYER.getInt(rt), openKeys, region, n.avoid);
                if (goal != null) {
                    n.covers = true;
                } else {
                    StandPlanner.Choice c = StandPlanner.best(region, open, TERRAIN, Math.max(2.5, s.reach - 0.4), EYE, 0.12,
                            cell -> n.avoid.contains(Nav.key(cell.x, cell.y, cell.z)) || openKeys.contains(Nav.key(cell.x, cell.y, cell.z))
                                    || openKeys.contains(Nav.key(cell.x, cell.y + 1, cell.z)));
                    if (c != null) {
                        goal = c.cell();
                        n.covers = true;
                    }
                }
            }
        }
        if (goal == null) {
            goal = closest(region, target, layerMode ? 0.0 : Math.max(0.8, reach - 0.4), n.avoid);
            n.covers = false;
        }
        if (goal == null) {
            return null;
        }
        n.goalKey = Nav.key(goal.x, goal.y, goal.z);
        n.path = region.path(goal);
        n.index = n.path.size() > 1 ? 1 : 0;
        return n;
    }

    /** The reachable cell that is nearest to the target (within {@code within} horizontally counts as arrived). */
    private static Nav.Cell closest(Nav.Region region, class_243 target, double within, Set<Long> avoid) {
        Nav.Cell best = null;
        double bestScore = Double.MAX_VALUE;
        for (Nav.Cell c : region.cells.values()) {
            if (avoid.contains(Nav.key(c.x, c.y, c.z))) {
                continue;
            }
            double dx = c.x + 0.5 - target.field_1352, dz = c.z + 0.5 - target.field_1350, dy = c.y + EYE - target.field_1351;
            double h = Math.sqrt(dx * dx + dz * dz);
            double score = Math.max(0.0, h - within) * 3.0 + Math.abs(dy) * 0.6 + c.cost * 0.05;
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /** The open blocks of the current layer that can be placed with what is in the inventory. */
    private static List<int[]> openBlocks(BuildRuntime rt) throws ReflectiveOperationException {
        BuildPlan plan = (BuildPlan) Priv.PLAN.get(rt);
        boolean[] done = (boolean[]) Priv.DONE.get(rt);
        int[] attempts = (int[]) Priv.ATTEMPTS.get(rt);
        int layer = Priv.LAYER.getInt(rt);
        int[] start = (int[]) Priv.START.get(rt);
        int[] end = (int[]) Priv.END.get(rt);
        @SuppressWarnings("unchecked")
        Set<Integer> deferred = (Set<Integer>) Priv.DEFERRED.get(rt);
        @SuppressWarnings("unchecked")
        Map<class_1792, Integer> missing = (Map<class_1792, Integer>) Priv.MISSING.get(rt);
        List<int[]> out = new ArrayList<>();
        if (plan == null || layer < 0 || start == null || layer >= start.length || start[layer] < 0) {
            return out;
        }
        if (plan != parkedPlan) {
            parkedPlan = plan;
            PARKED.clear();
            parkRounds = 0;
        }
        int tick = mc.field_1724 == null ? 0 : mc.field_1724.field_6012;
        PARKED.values().removeIf(until -> until <= tick);
        boolean skippedParked = false;
        for (int i = start[layer]; i < end[layer] && out.size() < 8000; i++) {
            if (done[i] || attempts[i] >= 1000 || deferred.contains(i)) {
                continue;
            }
            if (PARKED.containsKey(i)) {
                skippedParked = true;
                continue;
            }
            BuildPlan.Planned b = plan.blocks.get(i);
            if (!missing.isEmpty() && missing.containsKey(BuildPlan.itemOf(b.state()))) {
                continue;
            }
            out.add(new int[]{b.pos().method_10263(), b.pos().method_10264(), b.pos().method_10260(), i});
        }
        if (out.isEmpty() && skippedParked) {
            // nothing else is left: try the parked ones again, for longer each time
            PARKED.clear();
            parkRounds++;
            return openBlocks(rt);
        }
        return out;
    }

    private static boolean follow(BuildRuntime rt, BuildRuntime.Settings s, Trip t, class_746 p, int tick) throws ReflectiveOperationException {
        Priv.SNEAK.invoke(rt, false);
        if (t.path == null) {
            return false;
        }
        while (t.index < t.path.size()) {
            Nav.Cell c = t.path.get(t.index);
            double dx = c.x + 0.5 - p.method_23317(), dz = c.z + 0.5 - p.method_23321();
            boolean last = t.index == t.path.size() - 1;
            if (dx * dx + dz * dz < (last ? 0.09 : 0.3) && Math.abs(p.method_23318() - c.y) < 1.2) {
                t.index++;
            } else {
                break;
            }
        }
        if (t.index >= t.path.size()) {
            return arrive(rt, s, t, p, tick);
        }
        t.arrived = 0;

        Nav.Cell next = t.path.get(t.index);
        double dx = next.x + 0.5 - p.method_23317(), dz = next.z + 0.5 - p.method_23321();
        float want = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        p.method_36456(RotationUtil.approachAngle(p.method_36454(), want, 22.0F));
        if (s.human) {
            p.method_36457(RotationUtil.approachAngle(p.method_36455(), 12.0F, 5.0F));
        }

        // no progress for a while: jump once, then give up on this spot
        if (tick - t.sampleTick >= 10) {
            double moved = Math.hypot(p.method_23317() - t.sampleX, p.method_23321() - t.sampleZ);
            t.stuck = moved < 0.25 && p.method_24828() ? t.stuck + 1 : 0;
            t.sampleTick = tick;
            t.sampleX = p.method_23317();
            t.sampleZ = p.method_23321();
            if (t.stuck >= 4) {
                t.avoid.add(t.goalKey);
                t.replans++;
                trip = null;
                Priv.RELEASE.invoke(rt);
                if (t.replans >= 6) {
                    t.replans = 0;
                    if (tick >= bigUntil) {
                        bigUntil = tick + 1200; // first look further around; that costs a moment, so only for a minute
                        return true;
                    }
                    return false; // even the wide search had nothing: the old walking (which will not run into a wall) takes over
                }
                return true;
            }
        }
        boolean up = next.y > p.method_23318() + 0.4;
        boolean ahead = Math.hypot(dx, dz) < 1.6;
        mc.field_1690.field_1894.method_23481(true);
        mc.field_1690.field_1913.method_23481(false);
        mc.field_1690.field_1903.method_23481(p.method_24828() && ((up && ahead) || t.stuck >= 2));
        Priv.WALKING.setBoolean(rt, true);
        Priv.STATUS.set(rt, t.covers ? "Walking to the next stand spot" : "Walking towards the build");
        return true;
    }

    private static boolean arrive(BuildRuntime rt, BuildRuntime.Settings s, Trip t, class_746 p, int tick) throws ReflectiveOperationException {
        Priv.RELEASE.invoke(rt);
        t.arrived++;
        if (t.covers) {
            // the blocks should be placeable from here; if the build code still finds nothing, this spot does not work
            if (t.arrived > 12) {
                t.avoid.add(t.goalKey);
                t.replans++;
                trip = null;
            }
            return true;
        }
        // nothing sees a block from the closest reachable cell: pillar up if it is allowed, the build code does the rest
        Priv.STATUS.set(rt, "No spot reaches the next blocks");
        if (t.arrived == 6 && s.pillar && p.method_24828() && Priv.TOWER.get(rt) == null && t.towered < 12
                && ((Map<?, ?>) Priv.MISSING.get(rt)).isEmpty()) {
            t.towered++;
            Priv.TOWER_START.invoke(rt, s);
        } else if (t.arrived > 40) {
            t.avoid.add(t.goalKey);
            trip = null;
        }
        return true;
    }
}
