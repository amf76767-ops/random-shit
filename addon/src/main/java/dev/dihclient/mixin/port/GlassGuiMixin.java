package dev.dihclient.mixin.port;

import dev.dihclient.gui.GlassGuiScreen;
import dev.dihclient.gui.MeteorGuiScreen;
import net.minecraft.class_437;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Opens the glass GUI whenever the ClickGUI style is "Glass", and swaps screens when the style is changed while one is open. */
@Mixin(MeteorGuiScreen.class)
public abstract class GlassGuiMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void dih$glassCreate(CallbackInfoReturnable<class_437> cir) {
        if (GlassGuiScreen.wanted()) {
            cir.setReturnValue(new GlassGuiScreen());
        }
    }

    @Inject(method = "onLayoutChanged", at = @At("HEAD"), cancellable = true)
    private static void dih$glassSwap(CallbackInfo ci) {
        GlassGuiScreen.layoutChanged();
        ci.cancel();
    }
}
