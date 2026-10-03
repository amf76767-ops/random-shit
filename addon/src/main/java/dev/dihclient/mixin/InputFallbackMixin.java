package dev.dihclient.mixin;

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
 * so the player stands still while everything else works. This puts software key presses back into the input.
 * Real key presses are left alone, so those mods keep working for the player.
 */
@Mixin({class_743.class})
public abstract class InputFallbackMixin extends class_744 {
    @Inject(
        method = {"method_3129"},
        at = {@At("TAIL")}
    )
    private void dih$softwareKeys(CallbackInfo ci) {
        class_315 o = class_310.method_1551().field_1690;
        if (o == null) {
            return;
        }
        if (!(soft(o.field_1894) || soft(o.field_1881) || soft(o.field_1913) || soft(o.field_1849) || soft(o.field_1903) || soft(o.field_1832))) {
            return;
        }
        boolean forward = o.field_1894.method_1434();
        boolean back = o.field_1881.method_1434();
        boolean left = o.field_1913.method_1434();
        boolean right = o.field_1849.method_1434();
        this.field_54155 = new class_10185(forward, back, left, right, o.field_1903.method_1434(), o.field_1832.method_1434(), o.field_1867.method_1434());
        float ahead = (forward ? 1.0F : 0.0F) - (back ? 1.0F : 0.0F);
        float side = (left ? 1.0F : 0.0F) - (right ? 1.0F : 0.0F);
        float length = (float) Math.sqrt(ahead * ahead + side * side);
        if (length > 1.0F) {
            ahead /= length;
            side /= length;
        }
        this.field_55868 = new class_241(side, ahead);
    }

    /** Pressed by a module, not by the keyboard. */
    private static boolean soft(class_304 key) {
        return key.method_1434() && !KeyUtil.isPhysicallyDown(key);
    }
}
