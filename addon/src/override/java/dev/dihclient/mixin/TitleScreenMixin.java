package dev.dihclient.mixin;

import dev.dihclient.gui.AccountsScreen;
import dev.dihclient.hud.HudManager;
import dev.dihclient.render.Gfx;
import net.minecraft.class_11909;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;
import net.minecraft.class_442;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_442.class)
public abstract class TitleScreenMixin {
    private static final int BTN_X = 6;
    private static final int BTN_Y = 6;
    private static final int BTN_W = 96;
    private static final int BTN_H = 20;

    @Inject(method = "method_25394", at = @At("TAIL"))
    private void dih$drawAccounts(class_332 graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int accent = HudManager.accent();
        boolean hover = Gfx.inside(mouseX, mouseY, BTN_X, BTN_Y, BTN_W, BTN_H);
        Gfx.round(graphics, BTN_X, BTN_Y, BTN_W, BTN_H, hover ? accent : 0xFF2A2A35);
        Gfx.textCentered(graphics, "Accounts", BTN_X + BTN_W / 2, BTN_Y + 6, -1);
    }

    @Inject(method = "method_25402", at = @At("HEAD"), cancellable = true)
    private void dih$clickAccounts(class_11909 click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (Gfx.inside((int) click.comp_4798(), (int) click.comp_4799(), BTN_X, BTN_Y, BTN_W, BTN_H)) {
            class_310.method_1551().method_1507(new AccountsScreen((class_437) (Object) this));
            cir.setReturnValue(true);
        }
    }
}
