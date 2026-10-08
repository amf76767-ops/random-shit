package dev.dihclient.port.tools;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import net.minecraft.class_10042;
import net.minecraft.class_2596;
import net.minecraft.class_2828;
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
    private static boolean sending;
    private static boolean sentThisTick;
    private float serverSpin;

    public final EnumSetting<Mode> mode = this.mode("Mode", "Spin: turns around and around · Jitter: flips left and right · Backwards: looks away from where you look.", Mode.SPIN);
    public final DoubleSetting speed = this.dbl("Speed", "Degrees per tick.", 40.0, 2.0, 120.0, 1.0)
            .visibleWhen(() -> this.mode.get() == Mode.SPIN);
    public final DoubleSetting angle = this.dbl("Jitter Angle", "How far the model flips to each side.", 90.0, 10.0, 180.0, 5.0)
            .visibleWhen(() -> this.mode.get() == Mode.JITTER);
    public final IntSetting interval = this.integer("Jitter Interval", "Ticks between two flips.", 2, 1, 20)
            .visibleWhen(() -> this.mode.get() == Mode.JITTER);
    public final BoolSetting spinHead = this.bool("Spin Head", "The head turns with the body. Off = the head keeps looking where you look.", true);
    public final BoolSetting serverSide = this.bool("Server Side", "Other players see it too: your movement packets carry the spinning rotation. Your own camera stays put.", true);
    public final BoolSetting realWhileUsing = this.bool("Real While Using", "While the use key is held (throwing, eating, shooting) the server gets your real rotation, so items fly where you aim.", true)
            .visibleWhen(this.serverSide::get);
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
        Spinbot was = active;
        active = null;
        if (was != null && was.serverSide.get() && mc.field_1724 != null && mc.field_1724.field_3944 != null) {
            sendLook(mc.field_1724.method_36454(), mc.field_1724.method_36455());
        }
    }

    private static void sendLook(float yaw, float pitch) {
        sending = true;
        try {
            mc.field_1724.field_3944.method_52787(new class_2828.class_2831(yaw, pitch, mc.field_1724.method_24828(), mc.field_1724.field_5976));
        } finally {
            sending = false;
        }
    }

    private float[] serverAngles() {
        float realYaw = mc.field_1724.method_36454();
        float realPitch = mc.field_1724.method_36455();
        float yaw;
        switch (this.mode.get()) {
            case SPIN -> {
                this.serverSpin = class_3532.method_15393(this.serverSpin + this.speed.get().floatValue());
                yaw = this.serverSpin;
            }
            case JITTER -> yaw = realYaw + ((mc.field_1724.field_6012 / this.interval.get()) % 2 == 0 ? 1 : -1) * this.angle.get().floatValue();
            default -> yaw = realYaw + 180.0F;
        }
        float pitch = switch (this.pitch.get()) {
            case DOWN -> 90.0F;
            case UP -> -90.0F;
            case JITTER -> mc.field_1724.field_6012 / 2 % 2 == 0 ? 90.0F : -90.0F;
            default -> realPitch;
        };
        return new float[] {class_3532.method_15393(yaw), pitch};
    }

    private boolean realNow() {
        return this.realWhileUsing.get() && mc.field_1690.field_1904.method_1434();
    }

    public static class_2596<?> rewrite(class_2596<?> packet) {
        Spinbot m = active;
        if (m == null || sending || !m.serverSide.get() || mc.field_1724 == null || !(packet instanceof class_2828 move) || m.realNow()) {
            return packet;
        }
        float[] a = m.serverAngles();
        sentThisTick = true;
        boolean ground = move.method_12273();
        boolean collide = move.method_61225();
        if (move.method_36171()) {
            return new class_2828.class_2830(move.method_12269(mc.field_1724.method_23317()), move.method_12268(mc.field_1724.method_23318()),
                    move.method_12274(mc.field_1724.method_23321()), a[0], a[1], ground, collide);
        }
        return new class_2828.class_2831(a[0], a[1], ground, collide);
    }

    @Override
    public void onTick() {
        if (!this.serverSide.get() || mc.field_1724 == null || mc.field_1724.field_3944 == null) {
            return;
        }
        if (!sentThisTick && !this.realNow()) {
            float[] a = this.serverAngles();
            sendLook(a[0], a[1]);
        }
        sentThisTick = false;
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
