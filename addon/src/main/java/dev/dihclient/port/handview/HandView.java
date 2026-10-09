package dev.dihclient.port.handview;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.ActionSetting;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import net.minecraft.class_4587;
import org.joml.Quaternionf;

public class HandView extends Module {
    public final BoolSetting sameForBoth = this.bool("Same For Both Hands", "The off hand uses the main hand values, mirrored.", true);
    public final BoolSetting emptyHand = this.bool("Empty Hand", "Also move your arm when you hold nothing (position and size only).", true);
    private final DoubleSetting mainX = this.dbl("Main X", "Left (-) / right (+).", 0.0, -2.0, 2.0, 0.01);
    private final DoubleSetting mainY = this.dbl("Main Y", "Down (-) / up (+).", 0.0, -2.0, 2.0, 0.01);
    private final DoubleSetting mainZ = this.dbl("Main Z", "Forward (-) / back (+).", 0.0, -2.0, 2.0, 0.01);
    private final DoubleSetting mainRotX = this.dbl("Main Rotate X", "Tilt forward / back in degrees.", 0.0, -180.0, 180.0, 1.0);
    private final DoubleSetting mainRotY = this.dbl("Main Rotate Y", "Turn left / right in degrees.", 0.0, -180.0, 180.0, 1.0);
    private final DoubleSetting mainRotZ = this.dbl("Main Rotate Z", "Roll in degrees.", 0.0, -180.0, 180.0, 1.0);
    private final DoubleSetting mainScale = this.dbl("Main Scale", "Size of the item (1.0 = normal).", 1.0, 0.1, 3.0, 0.05);
    private final DoubleSetting offX = this.dbl("Off X", "Left (-) / right (+).", 0.0, -2.0, 2.0, 0.01).visibleWhen(() -> !this.sameForBoth.get());
    private final DoubleSetting offY = this.dbl("Off Y", "Down (-) / up (+).", 0.0, -2.0, 2.0, 0.01).visibleWhen(() -> !this.sameForBoth.get());
    private final DoubleSetting offZ = this.dbl("Off Z", "Forward (-) / back (+).", 0.0, -2.0, 2.0, 0.01).visibleWhen(() -> !this.sameForBoth.get());
    private final DoubleSetting offRotX = this.dbl("Off Rotate X", "Tilt forward / back in degrees.", 0.0, -180.0, 180.0, 1.0).visibleWhen(() -> !this.sameForBoth.get());
    private final DoubleSetting offRotY = this.dbl("Off Rotate Y", "Turn left / right in degrees.", 0.0, -180.0, 180.0, 1.0).visibleWhen(() -> !this.sameForBoth.get());
    private final DoubleSetting offRotZ = this.dbl("Off Rotate Z", "Roll in degrees.", 0.0, -180.0, 180.0, 1.0).visibleWhen(() -> !this.sameForBoth.get());
    private final DoubleSetting offScale = this.dbl("Off Scale", "Size of the item (1.0 = normal).", 1.0, 0.1, 3.0, 0.05).visibleWhen(() -> !this.sameForBoth.get());
    public final ActionSetting reset = this.action("Reset", "Puts every value back to normal.", this::resetAll);

    public HandView() {
        super("Hand View", Category.RENDER, "Move, turn and resize the item in your hand in first person (X / Y / Z), for each hand.");
    }

    private void resetAll() {
        for (DoubleSetting s : new DoubleSetting[]{this.mainX, this.mainY, this.mainZ, this.mainRotX, this.mainRotY, this.mainRotZ,
                this.offX, this.offY, this.offZ, this.offRotX, this.offRotY, this.offRotZ}) {
            s.set(0.0);
        }
        this.mainScale.set(1.0);
        this.offScale.set(1.0);
    }

    private static HandView active() {
        return ModuleManager.on(HandView.class) ? ModuleManager.of(HandView.class) : null;
    }

    public static void applyItem(class_4587 matrices, boolean mainHand) {
        HandView v = active();
        if (v != null) {
            v.apply(matrices, mainHand, true);
        }
    }

    public static void applyArm(class_4587 matrices, boolean mainHand) {
        HandView v = active();
        if (v != null && v.emptyHand.get()) {
            v.apply(matrices, mainHand, false);
        }
    }

    private void apply(class_4587 matrices, boolean mainHand, boolean rotate) {
        boolean mirror = !mainHand && this.sameForBoth.get();
        boolean main = mainHand || mirror;
        double x = (main ? this.mainX : this.offX).get();
        double y = (main ? this.mainY : this.offY).get();
        double z = (main ? this.mainZ : this.offZ).get();
        double rx = (main ? this.mainRotX : this.offRotX).get();
        double ry = (main ? this.mainRotY : this.offRotY).get();
        double rz = (main ? this.mainRotZ : this.offRotZ).get();
        float scale = (main ? this.mainScale : this.offScale).get().floatValue();
        if (mirror) {
            x = -x;
            ry = -ry;
            rz = -rz;
        }
        if (x != 0.0 || y != 0.0 || z != 0.0) {
            matrices.method_22904(x, y, z);
        }
        if (rotate && (rx != 0.0 || ry != 0.0 || rz != 0.0)) {
            matrices.method_22907(new Quaternionf().rotationXYZ((float) Math.toRadians(rx), (float) Math.toRadians(ry), (float) Math.toRadians(rz)));
        }
        if (scale != 1.0F) {
            matrices.method_22905(scale, scale, scale);
        }
    }
}
