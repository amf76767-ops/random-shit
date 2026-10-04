package dev.dihclient.mixin.port;

import dev.dihclient.port.noinvleak.NoInvLeakModule;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Ported from an open-source client (GPL-3.0) (ArmorStatus.shownInHud call).
 * The armor element of the DIH HUD reads the worn stacks; No Inv Leak may swap them for fakes.
 */
@Mixin(targets = "dev.dihclient.hud.elements.ArmorElement")
public abstract class NoInvLeakArmorHudMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_746;method_6118(Lnet/minecraft/class_1304;)Lnet/minecraft/class_1799;"))
    private class_1799 dih$worn(class_746 player, class_1304 slot) {
        return NoInvLeakModule.shownInHud(player.method_6118(slot), slot);
    }
}
