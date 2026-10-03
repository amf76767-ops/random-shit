package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.autobuild.BuildPlan;
import dev.dihclient.autobuild.BuildRuntime;
import dev.dihclient.autobuild.McTerrain;
import dev.dihclient.modules.world.AutoBuild;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.nav.Nav;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.HumanAim;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1542;
import net.minecraft.class_1799;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;

/**
 * Takes down a build: every block of the loaded AutoBuild plan that is still there (same block as planned) is mined,
 * from the top down. It walks to the spots from which the most blocks are in reach (same path search as the AutoBuild
 * walking), aims at each block like a player would, uses the best tool in the hotbar and finally collects the drops.
 * Only blocks of the plan are touched. The module asks once before it starts; switching it off pauses, the AutoSupervisor
 * pauses and resumes it like the other automation.
 */
public class Demolish extends Module {
    public final BoolSetting confirm = this.bool("Confirm", "Ask once (switch the module on a second time) before it takes the build down.", true);
    public final BoolSetting collect = this.bool("Collect Drops", "Walk over the dropped items at the end.", true);
    public final IntSetting reach = this.integer("Reach", "How far it mines from (blocks). Servers usually allow about 4.5.", 4, 2, 5);
    public final IntSetting maxFall = this.integer("Max Fall", "How deep a drop on its way may be.", 2, 1, 4);
    public final IntSetting rotateSpeed = this.integer("Rotate Speed", "Maximum head turn per tick in degrees.", 30, 8, 90);

    private enum Phase { OFF, CLEARING, COLLECTING, DONE }

    private Phase phase = Phase.OFF;
    private final PathWalker walker = new PathWalker();
    private final List<class_2338> targets = new ArrayList<>();
    private final Set<Long> remaining = new HashSet<>();
    private final Set<Long> skipped = new HashSet<>();
    private final Map<Long, class_2338> byKey = new HashMap<>();
    private class_2338 mining;
    private int miningTicks;
    private boolean aimed;
    private int removed;
    private int total;
    private long confirmUntil;
    private int collectTicks;
    private String status = "Idle";
    private String planName = "";

    public Demolish() {
        super("Demolish", Category.AUTOMATION, "Takes down what AutoBuild built: mines the blocks of the loaded plan from the top, then collects the drops.");
        this.action("Stop", "Ends the job for good (switching the module off only pauses it).", () -> {
            this.phase = Phase.OFF;
            this.targets.clear();
            this.remaining.clear();
            if (this.isEnabled()) {
                this.setEnabledSilently(false);
            }
        });
    }

    @Override
    protected void onEnable() {
        if (this.phase == Phase.CLEARING || this.phase == Phase.COLLECTING) {
            return; // resumed (the supervisor paused it)
        }
        if (!inGame()) {
            this.setEnabledSilently(false);
            return;
        }
        AutoBuild build = ModuleManager.of(AutoBuild.class);
        BuildRuntime rt = build == null ? null : build.runtime();
        BuildPlan plan = rt == null ? null : rt.plan();
        if (plan == null) {
            Notifications.warn(this.name(), "No build loaded. Open the schematic in AutoBuild first (the plan tells what to take down).");
            this.setEnabledSilently(false);
            return;
        }
        if (rt.isBuilding()) {
            Notifications.warn(this.name(), "AutoBuild is still building. Stop it first.");
            this.setEnabledSilently(false);
            return;
        }
        this.collectTargets(plan);
        if (this.targets.isEmpty()) {
            Notifications.info(this.name(), "Nothing of " + plan.schematic.name() + " is standing here.");
            this.setEnabledSilently(false);
            return;
        }
        long now = System.currentTimeMillis();
        if (this.confirm.get() && now > this.confirmUntil) {
            this.confirmUntil = now + 20_000L;
            Notifications.warn(this.name(), this.targets.size() + " blocks of " + this.planName + " will be mined. Switch Demolish on again within 20 s to start.");
            this.targets.clear();
            this.remaining.clear();
            this.setEnabledSilently(false);
            return;
        }
        this.confirmUntil = 0;
        this.removed = 0;
        this.total = this.targets.size();
        this.skipped.clear();
        this.mining = null;
        this.walker.reset();
        this.phase = Phase.CLEARING;
        Notifications.info(this.name(), "Taking down " + this.total + " blocks of " + this.planName);
    }

    @Override
    protected void onDisable() {
        this.walker.release();
        if (this.mining != null && mc.field_1761 != null) {
            mc.field_1761.method_2925();
        }
        this.mining = null;
        if (this.phase == Phase.DONE) {
            this.phase = Phase.OFF;
        }
    }

    @Override
    public void onWorldChange() {
        this.phase = Phase.OFF;
        this.targets.clear();
        this.remaining.clear();
        if (this.isEnabled()) {
            this.setEnabledSilently(false);
        }
    }

    private void collectTargets(BuildPlan plan) {
        this.targets.clear();
        this.remaining.clear();
        this.byKey.clear();
        this.planName = plan.schematic.name();
        for (BuildPlan.Planned b : plan.blocks) {
            class_2680 now = mc.field_1687.method_8320(b.pos());
            if (!now.method_26215() && now.method_26204() == b.state().method_26204()) {
                class_2338 pos = b.pos();
                this.targets.add(pos);
                long k = Nav.key(pos.method_10263(), pos.method_10264(), pos.method_10260());
                this.remaining.add(k);
                this.byKey.put(k, pos);
            }
        }
        this.targets.sort(Comparator.comparingInt(class_2338::method_10264).reversed());
    }

    @Override
    public String getInfo() {
        return this.phase == Phase.CLEARING ? this.removed + "/" + this.total : null;
    }

    @Override
    public List<String> details() {
        List<String> out = new ArrayList<>();
        out.add("Status: " + this.status);
        if (this.total > 0) {
            out.add("Removed: " + this.removed + " / " + this.total);
        }
        return out;
    }

    @Override
    public void onTick() {
        if (!inGame() || this.phase == Phase.OFF) {
            return;
        }
        if (mc.field_1755 != null) {
            this.status = "Paused (screen open)";
            this.walker.release();
            return;
        }
        try {
            switch (this.phase) {
                case CLEARING -> this.tickClearing();
                case COLLECTING -> this.tickCollecting();
                case DONE -> this.finish();
                default -> {
                }
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Demolish failed", t);
            Notifications.error(this.name(), "Stopped: " + t.getMessage());
            this.phase = Phase.OFF;
            this.setEnabledSilently(false);
        }
    }

    private boolean stillThere(class_2338 pos) {
        class_2680 s = mc.field_1687.method_8320(pos);
        return !s.method_26215() && !s.method_45474();
    }

    private void tickClearing() {
        if (this.mining != null) {
            this.tickMining();
            return;
        }
        // drop what is gone
        this.remaining.removeIf(k -> {
            class_2338 pos = this.byKey.get(k);
            return pos == null || !this.stillThere(pos);
        });
        if (this.remaining.isEmpty()) {
            this.walker.release();
            this.phase = this.collect.get() ? Phase.COLLECTING : Phase.DONE;
            this.collectTicks = 0;
            return;
        }
        class_2338 next = this.pickReachable();
        if (next != null) {
            this.walker.release();
            this.mining = next;
            this.miningTicks = 0;
            this.aimed = false;
            return;
        }
        // walk to the spot that reaches most of the top layer
        int top = Integer.MIN_VALUE;
        for (long k : this.remaining) {
            if (!this.skipped.contains(k)) {
                top = Math.max(top, this.byKey.get(k).method_10264());
            }
        }
        List<int[]> open = new ArrayList<>();
        for (long k : this.remaining) {
            class_2338 p = this.byKey.get(k);
            if (!this.skipped.contains(k) && p.method_10264() >= top - 3) {
                open.add(new int[]{p.method_10263(), p.method_10264(), p.method_10260()});
            }
        }
        if (open.isEmpty()) {
            this.phase = this.collect.get() ? Phase.COLLECTING : Phase.DONE;
            return;
        }
        PathWalker.State st = this.walker.walk(open, this.reach.get(), this.maxFall.get());
        this.status = "Walking to the next spot (" + (this.total - this.remaining.size()) + "/" + this.total + ")";
        if (st == PathWalker.State.NO_ROUTE) {
            // nothing reaches the rest: skip the highest ones, they are probably out of reach
            for (long k : new ArrayList<>(this.remaining)) {
                class_2338 p = this.byKey.get(k);
                if (p.method_10264() >= top - 3) {
                    this.skipped.add(k);
                }
            }
            this.walker.reset();
        } else if (st == PathWalker.State.ARRIVED && this.walker.arrivedTicks() > 15) {
            this.walker.giveUpGoal(); // we are there and still see nothing to mine: another spot
        }
    }

    /** The highest block of the plan that is in reach and in line of sight, or null. */
    private class_2338 pickReachable() {
        class_243 eye = mc.field_1724.method_33571();
        double r = this.reach.get() - 0.3;
        int er = (int) Math.ceil(r) + 1;
        class_2338 base = class_2338.method_49637(eye.field_1352, eye.field_1351, eye.field_1350);
        class_2338 best = null;
        McTerrain terrain = new McTerrain();
        for (int dx = -er; dx <= er; dx++) {
            for (int dy = -er; dy <= er; dy++) {
                for (int dz = -er; dz <= er; dz++) {
                    int x = base.method_10263() + dx, y = base.method_10264() + dy, z = base.method_10260() + dz;
                    long k = Nav.key(x, y, z);
                    if (!this.remaining.contains(k) || this.skipped.contains(k)) {
                        continue;
                    }
                    class_2338 pos = this.byKey.get(k);
                    if (class_243.method_24953(pos).method_1022(eye) > r) {
                        continue;
                    }
                    if (best != null && (y < best.method_10264() || (y == best.method_10264() && eye.method_1022(class_243.method_24953(pos)) >= eye.method_1022(class_243.method_24953(best))))) {
                        continue;
                    }
                    if (!seen(terrain, eye, pos)) {
                        continue;
                    }
                    best = pos;
                }
            }
        }
        return best;
    }

    /** No full block between the eyes and the block, except right next to it. */
    private static boolean seen(McTerrain world, class_243 eye, class_2338 b) {
        double tx = b.method_10263() + 0.5, ty = b.method_10264() + 0.5, tz = b.method_10260() + 0.5;
        double dx = tx - eye.field_1352, dy = ty - eye.field_1351, dz = tz - eye.field_1350;
        int steps = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.25);
        for (int i = 1; i < steps; i++) {
            double f = (double) i / steps;
            int x = (int) Math.floor(eye.field_1352 + dx * f), y = (int) Math.floor(eye.field_1351 + dy * f), z = (int) Math.floor(eye.field_1350 + dz * f);
            if (x == b.method_10263() && y == b.method_10264() && z == b.method_10260()) {
                break;
            }
            int manhattan = Math.abs(x - b.method_10263()) + Math.abs(y - b.method_10264()) + Math.abs(z - b.method_10260());
            if (manhattan > 1 && world.support(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private void tickMining() {
        class_2680 state = mc.field_1687.method_8320(this.mining);
        class_243 eye = mc.field_1724.method_33571();
        class_243 center = class_243.method_24953(this.mining);
        if (state.method_26215() || state.method_45474() || this.miningTicks > 300 || center.method_1022(eye) > this.reach.get() + 1.0) {
            boolean gone = state.method_26215();
            long k = Nav.key(this.mining.method_10263(), this.mining.method_10264(), this.mining.method_10260());
            if (gone) {
                this.removed++;
                this.remaining.remove(k);
            } else {
                this.skipped.add(k);
            }
            mc.field_1761.method_2925();
            this.mining = null;
            return;
        }
        this.miningTicks++;
        this.selectTool(state);
        class_2350 face = class_2350.method_58251(eye.method_1020(center));
        class_243 aim = center.method_1031(face.method_10148() * 0.49, face.method_10164() * 0.49, face.method_10165() * 0.49);
        boolean on = HumanAim.stepTo(aim, this.rotateSpeed.get(), 3.0F, 1.0F);
        if (!on || !this.aimed) {
            this.aimed = on;
            this.status = "Aiming at " + state.method_26204().method_9518().getString();
            return;
        }
        if (mc.field_1724.method_68878()) {
            mc.field_1761.method_2910(this.mining, face);
        } else {
            mc.field_1761.method_2902(this.mining, face);
        }
        mc.field_1724.method_6104(class_1268.field_5808);
        this.status = "Mining " + state.method_26204().method_9518().getString();
    }

    private void selectTool(class_2680 state) {
        int best = -1;
        float speed = 1.0F;
        for (int slot = 0; slot < 9; slot++) {
            class_1799 stack = mc.field_1724.method_31548().method_5438(slot);
            if (!stack.method_7963() || stack.method_7936() - stack.method_7919() >= 10) {
                float s = stack.method_7924(state);
                if (s > speed) {
                    speed = s;
                    best = slot;
                }
            }
        }
        if (best >= 0) {
            InvUtil.select(best);
        }
    }

    private void tickCollecting() {
        this.collectTicks++;
        AutoBuild build = ModuleManager.of(AutoBuild.class);
        BuildPlan plan = build.runtime().plan();
        class_238 box = plan.bounds().method_1014(8.0);
        List<int[]> drops = new ArrayList<>();
        for (class_1297 e : mc.field_1687.method_8335(null, box)) {
            if (e instanceof class_1542) {
                drops.add(new int[]{(int) Math.floor(e.method_23317()), (int) Math.floor(e.method_23318()), (int) Math.floor(e.method_23321())});
            }
        }
        if (drops.isEmpty() || this.collectTicks > 20 * 90) {
            this.walker.release();
            this.phase = Phase.DONE;
            return;
        }
        this.status = "Collecting " + drops.size() + " drops";
        // pick up reach: items are collected from about one block away
        PathWalker.State st = this.walker.walk(drops, 1.6, this.maxFall.get());
        if (st == PathWalker.State.NO_ROUTE || (st == PathWalker.State.ARRIVED && this.walker.arrivedTicks() > 20)) {
            this.walker.giveUpGoal();
            if (st == PathWalker.State.NO_ROUTE && this.collectTicks > 40) {
                this.phase = Phase.DONE;
            }
        }
    }

    private void finish() {
        this.walker.release();
        this.status = "Done";
        int left = 0;
        for (long k : this.remaining) {
            if (this.stillThere(this.byKey.get(k))) {
                left++;
            }
        }
        Notifications.push(this.name(), "Done: " + this.removed + " blocks removed" + (left > 0 ? ", " + left + " out of reach" : ""),
                Notifications.Type.SUCCESS);
        this.phase = Phase.OFF;
        this.targets.clear();
        this.setEnabledSilently(false);
    }
}
