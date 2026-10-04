package dev.dihclient.mixin;

import dev.dihclient.DIHClient;
import dev.dihclient.glue.SoftKeys;
import dev.dihclient.util.KeyUtil;
import net.minecraft.class_10185;
import net.minecraft.class_241;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_315;
import net.minecraft.class_743;
import net.minecraft.class_744;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The automation modules walk by pressing the movement keys in software ({@code KeyBinding.setPressed}).
 * Some input mods (Snappy Tappy and similar) build the player input from the physical keyboard and ignore that,
 * so the player stands still while everything else works. This puts the key presses of DIHClient's own modules (noted by
 * KeyBindingSoftMixin in SoftKeys) back into the input. Real key presses are left alone, so those mods keep working.
 */
@Mixin({class_743.class})
public abstract class InputFallbackMixin extends class_744 {
    private static int dih$debugTicks;

    @Inject(
        method = {"method_3129"},
        at = {@At("TAIL")}
    )
    private void dih$softwareKeys(CallbackInfo ci) {
        class_315 o = class_310.method_1551().field_1690;
        if (o == null) {
            return;
        }
        if (o.field_1894.method_1434() && ++dih$debugTicks % 40 == 0) {
            // one line every 2 seconds while "forward" is held (real or by a module): shows where the chain breaks
            class_310 mc = class_310.method_1551();
            DIHClient.LOG.info("[DIH-Debug] forward: keyboard={} software={} input={} player={} screen={}",
                KeyUtil.isPhysicallyDown(o.field_1894), soft(o.field_1894), this.field_54155,
                mc.field_1724 == null ? "?" : String.format("%.3f", mc.field_1724.method_18798().field_1352), mc.field_1755 != null);
        }
        if (!(soft(o.field_1894) || soft(o.field_1881) || soft(o.field_1913) || soft(o.field_1849) || soft(o.field_1903) || soft(o.field_1832))) {
            return;
        }
        boolean forward = o.field_1894.method_1434() || SoftKeys.active(o.field_1894);
        boolean back = o.field_1881.method_1434() || SoftKeys.active(o.field_1881);
        boolean left = o.field_1913.method_1434() || SoftKeys.active(o.field_1913);
        boolean right = o.field_1849.method_1434() || SoftKeys.active(o.field_1849);
        boolean jump = o.field_1903.method_1434() || SoftKeys.active(o.field_1903);
        boolean sneak = o.field_1832.method_1434() || SoftKeys.active(o.field_1832);
        this.field_54155 = new class_10185(forward, back, left, right, jump, sneak, o.field_1867.method_1434());
        float ahead = (forward ? 1.0F : 0.0F) - (back ? 1.0F : 0.0F);
        float side = (left ? 1.0F : 0.0F) - (right ? 1.0F : 0.0F);
        float length = (float) Math.sqrt(ahead * ahead + side * side);
        if (length > 1.0F) {
            ahead /= length;
            side /= length;
        }
        this.field_55868 = new class_241(side, ahead);
    }

    /** Pressed by a DIHClient module, whatever other mods did to the key state meanwhile. */
    private static boolean soft(class_304 key) {
        return SoftKeys.active(key);
    }
}
