package dev.dihclient.modules.render;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.CombatUtil;
import net.minecraft.class_1657;
import net.minecraft.class_3966;

/**
 * Shows the player you are fighting. The target is, in this order: the target of a combat module, the player under your
 * crosshair, and the last player you hit (kept for a few seconds, so the panel does not vanish the moment the crosshair
 * slips off while you move or they get knocked back).
 */
public class TargetHud extends Module {
    public final BoolSetting crosshairTarget = this.bool(
        "Crosshair Target", "Also shows the player under your crosshair when no combat module has a target.", true);
    public final IntSetting keepSeconds = this.integer("Keep", "Seconds the last player you hit stays shown.", 4, 0, 15);
    private class_1657 lastHit;
    private long lastHitAt;

    public TargetHud() {
        super("Target HUD", Category.RENDER, "Shows the current combat target's head, name, health and distance. Move it in the HUD editor.");
    }

    /** Called when you swing, from a mixin. */
    public static void onSwing() {
        TargetHud hud = ModuleManager.of(TargetHud.class);
        if (hud != null && hud.isEnabled() && mc.field_1765 instanceof class_3966 hit && hit.method_17782() instanceof class_1657 player
                && player != mc.field_1724) {
            hud.lastHit = player;
            hud.lastHitAt = System.currentTimeMillis();
        }
    }

    public class_1657 current() {
        class_1657 target = CombatUtil.target();
        if (target == null && this.crosshairTarget.get() && mc.field_1765 instanceof class_3966 hit && hit.method_17782() instanceof class_1657 player) {
            target = player;
        }
        if (target == null && this.lastHit != null) {
            if (this.lastHit.method_5805() && System.currentTimeMillis() - this.lastHitAt < this.keepSeconds.get() * 1000L) {
                target = this.lastHit;
            } else {
                this.lastHit = null;
            }
        }
        return target;
    }
}
