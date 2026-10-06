package dev.dihclient.port.tools;

import dev.dihclient.mixin.port.BreakAccessorMixin;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.IntSetting;

public class NoBreakDelay extends Module {
    public final IntSetting delay = this.integer("Delay", "Ticks of pause between two broken blocks (the game uses 5, 0 = none).", 0, 0, 5);

    public NoBreakDelay() {
        super("No Break Delay", Category.WORLD, "Removes the short pause after you break a block, so the next one starts at once.");
    }

    @Override
    public String getInfo() {
        return this.delay.get() + "t";
    }

    @Override
    public void onTick() {
        if (mc.field_1761 == null || mc.field_1724 == null) {
            return;
        }
        BreakAccessorMixin acc = (BreakAccessorMixin) mc.field_1761;
        int limit = this.delay.get();
        if (acc.dih$cooldown() > limit) {
            acc.dih$setCooldown(limit);
        }
    }
}
