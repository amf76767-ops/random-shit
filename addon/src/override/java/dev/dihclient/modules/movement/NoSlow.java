package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.MoveUtil;
import dev.dihclient.util.RegistryUtil;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2338;

/**
 * No slowdown while eating, blocking or drawing a bow (the Multiplier, applied by a mixin), and now also in cobwebs and
 * on soul sand. The slowdown of those two is applied by the game inside the move itself, so it is made up for right after
 * it: soul sand by giving the speed back, cobwebs by moving on by the part of the step that was swallowed.
 */
public class NoSlow extends Module {
    public final DoubleSetting multiplier = this.dbl(
            "Multiplier", "Movement multiplier while using items (vanilla 0.2, 1.0 = no slowdown).", 1.0, 0.2, 1.0, 0.01)
            .legacy("noSlow.multiplier");
    public final BoolSetting webs = this.bool("Cobwebs", "No slowdown in cobwebs.", true);
    public final BoolSetting soulSand = this.bool("Soul Sand", "No slowdown on soul sand.", true);
    private double lastX;
    private double lastZ;
    private boolean haveLast;

    public NoSlow() {
        super("NoSlow", Category.MOVEMENT, "No slowdown while eating, blocking, drawing a bow, in cobwebs and on soul sand.");
    }

    @Override
    protected void onEnable() {
        this.haveLast = false;
    }

    private static boolean is(class_2338 pos, String id) {
        return RegistryUtil.blockId(mc.field_1687.method_8320(pos)).equals(id);
    }

    @Override
    public void onTick() {
        if (mc.field_1724.method_31549().field_7479 || mc.field_1724.method_5854() != null) {
            this.haveLast = false;
            return;
        }
        double x = mc.field_1724.method_23317();
        double y = mc.field_1724.method_23318();
        double z = mc.field_1724.method_23321();
        class_243 v = mc.field_1724.method_18798();

        if (this.soulSand.get() && mc.field_1724.method_24828()
                && is(class_2338.method_49637(x, y - 0.5, z), "minecraft:soul_sand")) {
            mc.field_1724.method_18800(v.field_1352 / 0.4, v.field_1351, v.field_1350 / 0.4);
        }

        if (this.webs.get() && (is(class_2338.method_49637(x, y, z), "minecraft:cobweb") || is(class_2338.method_49637(x, y + 1.0, z), "minecraft:cobweb"))) {
            double[] want = MoveUtil.direction(mc.field_1690.field_1867.method_1434() ? 0.28 : 0.21);
            if (this.haveLast) {
                double moved = Math.hypot(x - this.lastX, z - this.lastZ);
                double wanted = Math.hypot(want[0], want[1]);
                double missing = wanted - moved;
                if (wanted > 0 && missing > 0.01) {
                    double nx = want[0] / wanted * missing;
                    double nz = want[1] / wanted * missing;
                    class_238 box = mc.field_1724.method_5829().method_989(nx, 0.0, nz);
                    if (mc.field_1687.method_8587(mc.field_1724, box)) {
                        mc.field_1724.method_5814(x + nx, y, z + nz);
                    }
                }
            }
            double up = mc.field_1690.field_1903.method_1434() ? 0.3 : (mc.field_1690.field_1832.method_1434() ? -0.3 : Math.max(v.field_1351, -0.15));
            mc.field_1724.method_18800(v.field_1352, up, v.field_1350);
            mc.field_1724.field_6017 = 0.0;
        }
        this.lastX = mc.field_1724.method_23317();
        this.lastZ = mc.field_1724.method_23321();
        this.haveLast = true;
    }
}
