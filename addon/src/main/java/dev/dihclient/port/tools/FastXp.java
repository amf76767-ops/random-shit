package dev.dihclient.port.tools;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import net.minecraft.class_1268;
import net.minecraft.class_1802;
import org.lwjgl.glfw.GLFW;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Throws experience bottles every 100 ms while the physical right mouse button is held. It runs per frame (not per tick)
 * so the 100 ms rhythm is not tied to the 50 ms tick; the mouse is polled directly because the game's own use key
 * only fires every 4 ticks.
 */
public class FastXp extends Module {
    private static final long INTERVAL_MS = 100L;

    private long lastThrowTime;
    private boolean broken;

    public FastXp() {
        super("FastXP", Category.COMBAT, "Throws experience bottles every 100 ms while physical right-click is held.");
    }

    @Override
    protected void onEnable() {
        this.lastThrowTime = 0L;
        this.broken = false;
    }

    @Override
    public void onRender3D(Render3D render) {
        if (this.broken || mc.field_1724 == null || mc.field_1761 == null || mc.field_1755 != null || mc.method_22683() == null) {
            return;
        }
        try {
            if (!mc.field_1724.method_6047().method_31574(class_1802.field_8287)) {
                return;
            }
            if (GLFW.glfwGetMouseButton(mc.method_22683().method_4490(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - this.lastThrowTime >= INTERVAL_MS) {
                mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808);
                this.lastThrowTime = now;
            }
        } catch (Throwable t) {
            this.broken = true;
            DIHClient.LOG.warn("[DIHClient] FastXP stopped after an error", t);
        }
    }
}
