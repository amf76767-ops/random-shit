package dev.dihclient.port.donuta;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import net.minecraft.class_2246;
import net.minecraft.class_2680;
import net.minecraft.class_3481;

/** Ported from an open-source client (GPL-3.0). */
public class DonutSpeedMine extends Module {
    private static volatile DonutSpeedMine active;

    public final DoubleSetting breakAt = this.dbl("Break At", "Share of the normal break time after which the block counts as broken.",
            0.7, 0.3, 1.0, 0.01);
    public final BoolSetting skipDelay = this.bool("Skip Break Delay", "Drop the short wait between breaking one block and the next.", true);
    public final BoolSetting keepInstantDelay = this.bool("Keep Instant Delay",
            "Keep the wait after blocks that break instantly anyway.", true).visibleWhen(this.skipDelay::get);

    public DonutSpeedMine() {
        super("Donut Speed Mine", Category.DONUT, "Finishes breaking blocks before the full break time.");
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    public static float scaleProgress(class_2680 state, float progress) {
        DonutSpeedMine module = active;
        return module != null && affects(state) ? DonutSpeedMineLogic.scaleProgress(progress, module.breakAt.get()) : progress;
    }

    public static boolean dropsDelay(class_2680 state, float progress) {
        DonutSpeedMine module = active;
        return module != null && affects(state)
                && DonutSpeedMineLogic.dropsDelay(module.skipDelay.get(), module.keepInstantDelay.get(), progress);
    }

    private static boolean affects(class_2680 state) {
        if (state.method_26215() || state.method_27852(class_2246.field_10260)) {
            return false;
        }
        return state.method_27852(class_2246.field_10255)
                || (!state.method_26164(class_3481.field_33716) && !state.method_26164(class_3481.field_33713));
    }
}
