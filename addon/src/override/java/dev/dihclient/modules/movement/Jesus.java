package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_3486;
import net.minecraft.class_3610;

/**
 * Walk on water (and lava). The old version asked "am I touching water?" every tick; standing on the surface the player
 * sinks a hair each tick, so it kept switching between "rise" and "stand" and jittered or fell through. This one looks at
 * the fluid under the feet: close to the surface it snaps onto it, deeper it rises smoothly.
 */
public class Jesus extends Module {
    public final EnumSetting<Jesus.Mode> mode = this.mode("Mode", "Solid: stand on the surface. Bob: float up and bob.", Jesus.Mode.SOLID);
    public final BoolSetting lava = this.bool("Lava", "Also works on lava.", true);
    public final BoolSetting sneakSink = this.bool("Sneak To Sink", "Hold sneak to go under.", true);

    public Jesus() {
        super("Jesus", Category.MOVEMENT, "Walk on water and lava. Sneak to dive.");
    }

    private boolean isFluid(class_3610 state) {
        if (state.method_15769()) {
            return false;
        }
        return state.method_15767(class_3486.field_15517) || (this.lava.get() && state.method_15767(class_3486.field_15518));
    }

    /** Height of the fluid surface in the column at (x, y, z), looking from one block above to one block below. NaN = none. */
    private double surface(double x, double y, double z) {
        class_2338 base = class_2338.method_49637(x, y - 0.05, z);
        class_2338[] candidates = {base.method_10084(), base, base.method_10074()};
        for (class_2338 p : candidates) {
            class_3610 here = mc.field_1687.method_8316(p);
            if (this.isFluid(here) && mc.field_1687.method_8316(p.method_10084()).method_15769()) {
                return p.method_10264() + here.method_15763(mc.field_1687, p);
            }
        }
        return Double.NaN;
    }

    @Override
    public void onTick() {
        if (mc.field_1724.method_31549().field_7479 || mc.field_1724.method_6128() || mc.field_1724.method_5854() != null) {
            return;
        }
        if (this.sneakSink.get() && mc.field_1690.field_1832.method_1434()) {
            return;
        }
        double x = mc.field_1724.method_23317();
        double y = mc.field_1724.method_23318();
        double z = mc.field_1724.method_23321();
        double top = this.surface(x, y, z);
        if (Double.isNaN(top)) {
            return;
        }
        double depth = top - y;               // > 0: feet are under the surface
        class_243 v = mc.field_1724.method_18798();
        boolean jump = mc.field_1690.field_1903.method_1434();
        if (depth > 1.6 || depth < -0.35) {
            return;                           // far below (a real swim) or well above (a jump): leave it alone
        }
        if (this.mode.get() == Jesus.Mode.BOB) {
            mc.field_1724.method_18800(v.field_1352, depth > 0.1 ? 0.11 : (depth < -0.05 ? v.field_1351 : 0.0), v.field_1350);
            return;
        }
        if (depth > 0.2) {
            // deeper than a step: rise smoothly until the snap range is reached
            mc.field_1724.method_18800(v.field_1352, Math.min(0.28, 0.08 + depth * 0.5), v.field_1350);
            return;
        }
        if (v.field_1351 <= 0.2 || !jump) {
            mc.field_1724.method_5814(x, top, z);
            mc.field_1724.method_18800(v.field_1352, jump ? 0.42 : 0.0, v.field_1350);
            mc.field_1724.method_24830(true);
            mc.field_1724.field_6017 = 0.0;
        }
    }

    @Override
    public String getInfo() {
        return this.mode.displayValue();
    }

    public static enum Mode {
        SOLID,
        BOB;
    }
}
