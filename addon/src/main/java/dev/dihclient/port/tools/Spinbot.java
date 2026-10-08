package dev.dihclient.port.tools;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import net.minecraft.class_10042;
import net.minecraft.class_1309;
import net.minecraft.class_3532;

public class Spinbot extends Module {
    public enum Mode {
        SPIN,
        JITTER,
        BACKWARDS
    }

    public enum PitchMode {
        OFF,
        DOWN,
        UP,
        JITTER
    }

    private static volatile Spinbot active;

    public final EnumSetting<Mode> mode = this.mode("Mode", "Spin: turns around and around · Jitter: flips left and right · Backwards: looks away from where you look.", Mode.SPIN);
    public final DoubleSetting speed = this.dbl("Speed", "Degrees per tick.", 40.0, 2.0, 120.0, 1.0)
            .visibleWhen(() -> this.mode.get() == Mode.SPIN);
    public final DoubleSetting angle = this.dbl("Jitter Angle", "How far the model flips to each side.", 90.0, 10.0, 180.0, 5.0)
            .visibleWhen(() -> this.mode.get() == Mode.JITTER);
    public final IntSetting interval = this.integer("Jitter Interval", "Ticks between two flips.", 2, 1, 20)
            .visibleWhen(() -> this.mode.get() == Mode.JITTER);
    public final BoolSetting spinHead = this.bool("Spin Head", "The head turns with the body. Off = the head keeps looking where you look.", true);
    public final EnumSetting<PitchMode> pitch = this.mode("Pitch", "How the head tilts up and down on the model.", PitchMode.OFF);

    public Spinbot() {
        super("Spinbot", Category.RENDER, "Spins your player model for everyone who looks at it. Only the model turns, your camera and your real rotation stay as they are.");
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

    public static void apply(class_1309 entity, class_10042 state) {
        Spinbot m = active;
        if (m == null || entity != mc.field_1724) {
            return;
        }
        double ticks = System.currentTimeMillis() / 50.0;
        float originalHead = state.field_53446 + state.field_53447;
        float body;
        switch (m.mode.get()) {
            case SPIN -> body = (float) (ticks * m.speed.get() % 360.0);
            case JITTER -> {
                long flip = (long) (ticks / m.interval.get());
                body = originalHead + (flip % 2 == 0 ? 1 : -1) * m.angle.get().floatValue();
            }
            default -> body = originalHead + 180.0F;
        }
        state.field_53446 = body;
        state.field_53447 = m.spinHead.get() ? 0.0F : class_3532.method_15393(originalHead - body);
        switch (m.pitch.get()) {
            case DOWN -> state.field_53448 = 90.0F;
            case UP -> state.field_53448 = -90.0F;
            case JITTER -> state.field_53448 = (long) (ticks / 2) % 2 == 0 ? 90.0F : -90.0F;
            default -> {
            }
        }
    }
}
