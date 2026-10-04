package dev.dihclient.port.zoom;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import net.minecraft.class_310;
import org.lwjgl.glfw.GLFW;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Hold the key of this module (C by default, change it in the GUI) to zoom in. The mouse wheel changes the zoom while you
 * hold it, and the mouse turns slower the closer you are. The field of view comes from {@code ZoomRenderMixin}, the slower
 * turning and the wheel from {@code ZoomMouseMixin}. The module is always on; the key only works while no screen is open.
 */
public class Zoom extends Module {
    private static final float MIN_ZOOM = 1.1F;
    private static final float MAX_ZOOM = 50.0F;
    private static final float SCROLL_STEP = 1.2F;
    private static final float FAST_RATE = 30.0F;
    private static final float SLOW_RATE = 5.0F;
    private static final float MAX_FRAME_SECONDS = 0.1F;

    private static volatile Zoom instance;
    private static float target = 1.0F;
    private static float current = 1.0F;
    private static long lastNanos;

    public final DoubleSetting zoom = this.dbl("Zoom", "How much the view is magnified at the start.", 4.0, 1.5, 20.0, 0.5);
    public final IntSetting smoothness = this.integer("Smoothness", "0 = the zoom jumps · 100 = a slow, smooth glide.", 60, 0, 100);
    public final BoolSetting scroll = this.bool("Scroll", "The mouse wheel changes the zoom while you hold the key.", true);
    public final BoolSetting lowerSensitivity = this.bool("Lower Sensitivity", "The mouse turns slower the more you zoom in.", true);
    public final BoolSetting cinematic = this.bool("Cinematic Camera", "Smooths the camera movement while zooming.", false);

    private boolean zooming;
    private boolean smoothBefore;
    private boolean smoothPushed;

    public Zoom() {
        super("Zoom", Category.RENDER, "Hold the key to zoom in. The mouse wheel changes the zoom while you hold it.");
        this.setKeybindSilently(GLFW.GLFW_KEY_C);
        instance = this;
    }

    // the key is held, not pressed: the module manager must not toggle the module
    @Override
    public boolean isActionModule() {
        return true;
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    public void onAction() {
        // the state is read from the key itself in onTick
    }

    @Override
    public String getInfo() {
        return this.zooming ? String.format("%.1fx", target) : null;
    }

    @Override
    public void onTick() {
        class_310 mc = class_310.method_1551();
        int key = this.keybind();
        boolean held = key >= 0 && mc.field_1755 == null && mc.field_1724 != null
                && GLFW.glfwGetKey(mc.method_22683().method_4490(), key) == GLFW.GLFW_PRESS;
        if (held && !this.zooming) {
            this.zooming = true;
            target = (float) this.zoom.get().doubleValue();
            if (this.cinematic.get()) {
                this.smoothBefore = mc.field_1690.field_1914;
                mc.field_1690.field_1914 = true;
                this.smoothPushed = true;
            }
        } else if (!held && this.zooming) {
            this.stop(mc);
        }
    }

    @Override
    public void onWorldChange() {
        this.stop(class_310.method_1551());
    }

    private void stop(class_310 mc) {
        this.zooming = false;
        target = 1.0F;
        if (this.smoothPushed) {
            mc.field_1690.field_1914 = this.smoothBefore;
            this.smoothPushed = false;
        }
    }

    /** Called every frame by the render mixin: the field of view divided by the current zoom. */
    public static float fov(float fov) {
        float factor = advance();
        return factor > 1.0001F ? fov / factor : fov;
    }

    /** How much the mouse turn is scaled (1 = not zoomed). */
    public static double turnScale() {
        Zoom module = instance;
        return module != null && module.lowerSensitivity.get() && current > 1.0001F ? 1.0 / current : 1.0;
    }

    /** The mouse wheel while zooming. @return true when the zoom used it */
    public static boolean scroll(double amount) {
        Zoom module = instance;
        if (module != null && module.zooming && module.scroll.get() && amount != 0.0) {
            float next = target * (amount > 0.0 ? SCROLL_STEP : 1.0F / SCROLL_STEP);
            target = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, next));
            return true;
        }
        return false;
    }

    /** Moves the shown zoom towards the wanted one; the frame time keeps it independent of the frame rate. */
    private static float advance() {
        long now = System.nanoTime();
        float seconds = lastNanos == 0L ? 0.0F : Math.min(MAX_FRAME_SECONDS, (float) (now - lastNanos) / 1.0E9F);
        lastNanos = now;
        Zoom module = instance;
        float smooth = module == null ? 0.0F : module.smoothness.get() / 100.0F;
        if (smooth <= 0.0F || current == target) {
            current = target;
            return current;
        }
        float rate = FAST_RATE - (FAST_RATE - SLOW_RATE) * smooth;
        float blend = 1.0F - (float) Math.exp(-seconds * rate);
        double from = Math.log(current);
        double to = Math.log(target);
        current = (float) Math.exp(from + (to - from) * blend);
        if (Math.abs(current - target) < 0.002F) {
            current = target;
        }
        return current;
    }
}
