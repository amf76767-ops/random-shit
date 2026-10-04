package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.autobuild.BuildPlan;
import dev.dihclient.autobuild.BuildRuntime;
import dev.dihclient.autobuild.McTerrain;
import dev.dihclient.autobuild.RoutePlans;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.modules.world.AutoBuild;
import dev.dihclient.nav.Nav;
import dev.dihclient.nav.RoutePlanner;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.util.Notifications;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_746;

/**
 * Plans the whole way of a build before it starts. When AutoBuild begins a build and "Plan First" is on, the build is held
 * for a moment, the route over all layers is calculated (a few milliseconds per tick, the game keeps running), a summary is
 * shown, and then the build goes on and follows the plan. The "Plan Route" button of AutoBuild does the same for the build
 * that is loaded, also while it is only a preview. Hidden and always on.
 */
public class BuildPlannerModule extends Module {
    private static final long SLICE_NANOS = 5_000_000L;
    private static final double EYE = 1.62;

    private final AutoBuild build;
    private final BoolSetting planFirst;
    private final McTerrain terrain = new McTerrain();
    private RoutePlanner.Job job;
    private BuildPlan jobPlan;
    private BuildPlan lastPlanned;
    private boolean heldByUs;
    private int ticks;

    private static Field done;

    public BuildPlannerModule(AutoBuild build, BoolSetting planFirst) {
        super("BuildPlanner", Category.AUTOMATION, "Internal: plans the route of AutoBuild before it builds.");
        this.build = build;
        this.planFirst = planFirst;
        this.setHidden(true);
    }

    /** The "Plan Route" button. */
    public void planNow() {
        BuildRuntime rt = this.build.runtime();
        if (rt.plan() == null || !rt.isRunning()) {
            Notifications.warn("AutoBuild", "Load a schematic first (preview or build), then plan the route.");
            return;
        }
        this.start(rt, false);
    }

    @Override
    public void onTick() {
        try {
            this.tick();
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] route planning failed", t);
            this.abort(true);
        }
    }

    private void tick() {
        BuildRuntime rt = this.build.runtime();
        if (this.job != null) {
            if (rt.plan() != this.jobPlan || !rt.isRunning() || mc.field_1724 == null) {
                this.abort(true);
                return;
            }
            RoutePlanner.Job j = this.job;
            boolean finished = j.step(System.nanoTime() + SLICE_NANOS);
            if (++this.ticks % 10 == 0) {
                rt.status(); // keeps the runtime's own status text alive
            }
            if (finished) {
                this.finish(rt, j.result());
            }
            return;
        }
        BuildPlan plan = rt.plan();
        if (plan == null || !rt.isRunning()) {
            this.lastPlanned = null;
            if (RoutePlans.any() != null && plan == null) {
                RoutePlans.clear();
            }
            return;
        }
        if (this.planFirst.get() && rt.phase() == BuildRuntime.Phase.BUILDING && plan != this.lastPlanned && RoutePlans.of(plan) == null) {
            this.lastPlanned = plan;
            this.start(rt, true);
        }
    }

    private void start(BuildRuntime rt, boolean hold) {
        class_746 p = mc.field_1724;
        BuildPlan plan = rt.plan();
        if (p == null || mc.field_1687 == null || plan == null) {
            return;
        }
        this.terrain.reset();
        int sx = (int) Math.floor(p.method_23317()), sy = (int) Math.floor(p.method_23318() + 0.01), sz = (int) Math.floor(p.method_23321());
        if (!Nav.standable(this.terrain, sx, sy, sz)) {
            if (Nav.standable(this.terrain, sx, sy - 1, sz)) {
                sy--;
            } else if (Nav.standable(this.terrain, sx, sy + 1, sz)) {
                sy++;
            } else {
                Notifications.warn("AutoBuild", "Route not planned: stand on solid ground first.");
                return;
            }
        }
        BuildRuntime.Settings s = settings(rt);
        double reach = s == null ? 4.5 : s.reach;
        int maxFall = s == null ? 3 : s.maxFall;
        int from = s == null ? 1 : Math.max(1, s.fromLayer) - 1;
        int to = s == null || s.toLayer <= 0 ? Integer.MAX_VALUE : s.toLayer - 1;
        boolean[] doneFlags = doneFlags(rt);

        List<List<int[]>> layers = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        List<int[]> current = null;
        int currentLayer = Integer.MIN_VALUE;
        class_2338.class_2339 pos = new class_2338.class_2339();
        for (int i = 0; i < plan.blocks.size(); i++) {
            BuildPlan.Planned b = plan.blocks.get(i);
            if (b.layer() < from || b.layer() > to || (doneFlags != null && i < doneFlags.length && doneFlags[i])) {
                continue;
            }
            if (b.layer() != currentLayer) {
                currentLayer = b.layer();
                current = new ArrayList<>();
                layers.add(current);
                ids.add(currentLayer);
            }
            class_2680 state = b.state();
            boolean solid = false;
            try {
                solid = !state.method_26215() && !state.method_26218(mc.field_1687, pos.method_10101(b.pos())).method_1110();
            } catch (Throwable ignored) {
                // treated as not standable
            }
            current.add(new int[]{b.pos().method_10263(), b.pos().method_10264(), b.pos().method_10260(), solid ? 1 : 0});
        }
        if (layers.isEmpty()) {
            return;
        }
        this.job = new RoutePlanner.Job(this.terrain, sx, sy, sz, layers, ids, Math.max(2.5, reach - 0.4), EYE, maxFall);
        this.jobPlan = plan;
        this.ticks = 0;
        if (hold && rt.phase() == BuildRuntime.Phase.BUILDING) {
            rt.pauseToggle();
            this.heldByUs = true;
        }
        Notifications.info("AutoBuild", "Planning the route …");
    }

    private void finish(BuildRuntime rt, RoutePlanner.Result r) {
        BuildPlan plan = this.jobPlan;
        this.job = null;
        this.jobPlan = null;
        this.resume(rt);
        if (r.failure != null) {
            Notifications.warn("AutoBuild", "Route not planned: " + r.failure);
            return;
        }
        RoutePlans.set(plan, r);
        int sec = r.estimateSeconds();
        String time = sec >= 90 ? (sec / 60) + " min" : sec + " s";
        String text = "Route ready: " + r.stops.size() + " stops, about " + Math.round(r.walk) + " blocks of walking, ~" + time
                + (r.supportCount > 0 ? ", " + r.supportCount + " supports (yellow)" : "")
                + (r.problems.size() > 0 ? ", " + r.problems.size() + " problem blocks (red): out of reach or floating without ground" : "");
        Notifications.info("AutoBuild", text);
        DIHClient.LOG.info("[DIHClient] AutoBuild {}", text);
        dev.dihclient.autobuild.BuildLog.add("route planned: " + text);
    }

    private void abort(boolean resume) {
        this.job = null;
        this.jobPlan = null;
        if (resume) {
            this.resume(this.build.runtime());
        }
    }

    /** Continues the build we stopped for the planning; a pause the player made in the meantime stays. */
    private void resume(BuildRuntime rt) {
        if (this.heldByUs) {
            this.heldByUs = false;
            if (rt.phase() == BuildRuntime.Phase.PAUSED) {
                rt.pauseToggle();
            }
        }
    }

    private static BuildRuntime.Settings settings(BuildRuntime rt) {
        try {
            Field f = BuildRuntime.class.getDeclaredField("lastSettings");
            f.setAccessible(true);
            return (BuildRuntime.Settings) f.get(rt);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean[] doneFlags(BuildRuntime rt) {
        try {
            if (done == null) {
                done = BuildRuntime.class.getDeclaredField("done");
                done.setAccessible(true);
            }
            return (boolean[]) done.get(rt);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
