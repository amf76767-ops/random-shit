package dev.dihclient.mixin.port;

import dev.dihclient.port.configs.ConfigDock;
import net.minecraft.class_11905;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Puts the {@link ConfigDock} on top of the DIH GUI and gives it the clicks, keys and scrolling that belong to it. */
@Mixin(targets = "dev.dihclient.gui.MeteorGuiScreen")
public abstract class ConfigDockMixin {
    @Inject(method = "method_25394", at = @At("TAIL"))
    private void dih$drawDock(class_332 graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        class_437 self = (class_437) (Object) this;
        ConfigDock.draw(graphics, mouseX, mouseY, self.field_22789, self.field_22790);
    }

    @Inject(method = "method_25402", at = @At("HEAD"), cancellable = true)
    private void dih$clickDock(class_11909 click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        class_437 self = (class_437) (Object) this;
        if (ConfigDock.click(click.comp_4798(), click.comp_4799(), doubled, self.field_22789, self.field_22790)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "method_25403", at = @At("HEAD"), cancellable = true)
    private void dih$dragDock(class_11909 click, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        class_437 self = (class_437) (Object) this;
        if (ConfigDock.covers(click.comp_4798(), click.comp_4799(), self.field_22789, self.field_22790)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "method_25406", at = @At("HEAD"), cancellable = true)
    private void dih$releaseDock(class_11909 click, CallbackInfoReturnable<Boolean> cir) {
        if (ConfigDock.swallowRelease()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "method_25401", at = @At("HEAD"), cancellable = true)
    private void dih$scrollDock(double x, double y, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
        class_437 self = (class_437) (Object) this;
        if (ConfigDock.scroll(x, y, vertical, self.field_22789, self.field_22790)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "method_25404", at = @At("HEAD"), cancellable = true)
    private void dih$keyDock(class_11908 key, CallbackInfoReturnable<Boolean> cir) {
        if (ConfigDock.key(key.comp_4795(), key.comp_4797())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "method_25400", at = @At("HEAD"), cancellable = true)
    private void dih$charDock(class_11905 chars, CallbackInfoReturnable<Boolean> cir) {
        if (ConfigDock.typing() && chars.method_74227()) {
            ConfigDock.typed(chars.method_74226());
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isTyping", at = @At("RETURN"), cancellable = true)
    private void dih$typingDock(CallbackInfoReturnable<Boolean> cir) {
        if (ConfigDock.typing()) {
            cir.setReturnValue(true);
        }
    }
}
