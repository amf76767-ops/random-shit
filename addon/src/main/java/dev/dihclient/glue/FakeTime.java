package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import java.lang.reflect.Method;

public class FakeTime extends Module {
    public enum Mode { FIXED, CYCLE }

    private static final long DAY = 24000L;

    public final EnumSetting<Mode> mode = this.mode("Mode", "Fixed: the time stays where you put it · Cycle: the day runs at your own speed.", Mode.FIXED);
    public final IntSetting time = this.integer("Time", "Time of day in ticks: 0 sunrise · 6000 noon · 12000 sunset · 18000 midnight.", 6000, 0, 23999);
    public final DoubleSetting speed = this.dbl("Speed", "Cycle: how fast the day runs. 1 = like the game (20 minutes per day), 20 = one day per minute.",
            20.0, 0.1, 200.0, 0.1).visibleWhen(() -> this.mode.get() == Mode.CYCLE);

    private double cycle;
    private long baseDay;
    private Method setTime;
    private Method getProps;
    private Method propsGameTime;
    private Method propsDayTime;
    private boolean broken;

    public FakeTime() {
        super("Fake Time", Category.MISC, "Sets the time of day on your screen (always noon, always night, or your own speed). Only you see it.");
    }

    @Override
    protected void onEnable() {
        this.cycle = this.time.get();
        this.baseDay = 0;
        this.broken = false;
        try {
            if (mc.field_1687 != null) {
                this.lookup();
                this.baseDay = Math.floorDiv(this.dayTime(), DAY) * DAY;
            }
        } catch (Throwable t) {
            this.fail(t);
        }
    }

    @Override
    public void onWorldChange() {
        this.baseDay = 0;
    }

    @Override
    public String getInfo() {
        long t = this.mode.get() == Mode.FIXED ? this.time.get() : (long) this.cycle % DAY;
        return String.valueOf(t);
    }

    @Override
    public void onTick() {
        if (mc.field_1687 == null || this.broken) {
            return;
        }
        try {
            this.lookup();
            if (this.mode.get() == Mode.CYCLE) {
                this.cycle = (this.cycle + this.speed.get()) % DAY;
            }
            long t = this.mode.get() == Mode.FIXED ? this.time.get() : (long) this.cycle;
            this.setTime.invoke(mc.field_1687, this.gameTime(), this.baseDay + t, false);
        } catch (Throwable t) {
            this.fail(t);
        }
    }

    private void lookup() throws ReflectiveOperationException {
        if (this.setTime != null) {
            return;
        }
        Class<?> world = mc.field_1687.getClass();
        this.setTime = world.getMethod("method_29089", long.class, long.class, boolean.class);
        this.getProps = world.getMethod("method_28104");
        Class<?> props = this.getProps.invoke(mc.field_1687).getClass();
        this.propsGameTime = props.getMethod("method_188");
        this.propsDayTime = props.getMethod("method_217");
    }

    private long gameTime() throws ReflectiveOperationException {
        return (long) this.propsGameTime.invoke(this.getProps.invoke(mc.field_1687));
    }

    private long dayTime() throws ReflectiveOperationException {
        return (long) this.propsDayTime.invoke(this.getProps.invoke(mc.field_1687));
    }

    private void fail(Throwable t) {
        this.broken = true;
        DIHClient.LOG.warn("[DIHClient] Fake Time does not work with this Minecraft version", t);
        dev.dihclient.util.Notifications.error(this.name(), "Does not work here (see log)");
    }
}
