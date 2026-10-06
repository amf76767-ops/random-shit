package dev.dihclient.port.tools;

import dev.dihclient.mixin.port.BreakAccessorMixin;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;

public class FastBreak extends Module {
    public enum Mode {
        NORMAL,
        HASTE,
        DAMAGE
    }

    private static final float SAFE_FACTOR = 1.4F;
    private static volatile FastBreak active;

    public final EnumSetting<Mode> mode = this.mode("Mode",
            "Normal: breaking speed times Modifier · Haste: like a Haste effect of the chosen level · Damage: the block breaks as soon as its progress reaches Damage.",
            Mode.NORMAL);
    public final DoubleSetting modifier = this.dbl("Modifier", "How much faster blocks break.", 1.3, 1.0, 5.0, 0.05)
            .visibleWhen(() -> this.mode.get() == Mode.NORMAL);
    public final IntSetting hasteLevel = this.integer("Haste Level", "Level of the fake Haste effect (each level adds 20 percent).", 2, 0, 9)
            .visibleWhen(() -> this.mode.get() == Mode.HASTE);
    public final DoubleSetting damage = this.dbl("Damage", "Break progress at which the block is finished (1.0 = normal).", 0.7, 0.2, 1.0, 0.05)
            .visibleWhen(() -> this.mode.get() == Mode.DAMAGE);
    public final BoolSetting safe = this.bool("Safe Limit", "Keeps the speed inside what a normal server accepts (up to about 1.4 times, Damage not below 0.7). Off = anything goes, blocks may reappear.", true);
    public final BoolSetting onlyOnGround = this.bool("Only On Ground", "Works only while you stand on the ground.", false);
    public final BoolSetting noDelay = this.bool("No Break Delay", "Removes the pause between two broken blocks.", true);

    public FastBreak() {
        super("Fast Break", Category.WORLD, "Breaks blocks faster: a speed multiplier, a fake Haste level or an early finish at a chosen progress.");
    }

    public static float factor() {
        FastBreak m = active;
        if (m == null) {
            return 1.0F;
        }
        if (m.onlyOnGround.get() && (mc.field_1724 == null || !mc.field_1724.method_24828())) {
            return 1.0F;
        }
        float f;
        switch (m.mode.get()) {
            case NORMAL -> f = m.modifier.get().floatValue();
            case HASTE -> f = 1.0F + 0.2F * (m.hasteLevel.get() + 1);
            default -> f = 1.0F;
        }
        if (m.safe.get()) {
            f = Math.min(f, SAFE_FACTOR);
        }
        return Math.max(1.0F, f);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    @Override
    public String getInfo() {
        return this.mode.displayValue();
    }

    @Override
    public void onTick() {
        if (mc.field_1761 == null || mc.field_1724 == null) {
            return;
        }
        BreakAccessorMixin acc = (BreakAccessorMixin) mc.field_1761;
        if (this.noDelay.get() && acc.dih$cooldown() > 0) {
            acc.dih$setCooldown(0);
        }
        if (this.mode.get() == Mode.DAMAGE && acc.dih$breaking()) {
            float limit = this.damage.get().floatValue();
            if (this.safe.get()) {
                limit = Math.max(limit, 0.7F);
            }
            float p = acc.dih$progress();
            if (p >= limit && p < 1.0F) {
                acc.dih$setProgress(1.0F);
            }
        }
    }
}
