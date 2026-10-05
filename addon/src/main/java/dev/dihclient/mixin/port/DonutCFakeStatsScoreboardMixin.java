package dev.dihclient.mixin.port;

import dev.dihclient.port.donutc.FakeStats;
import net.minecraft.class_266;
import net.minecraft.class_269;
import net.minecraft.class_8646;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ported from an open-source client (GPL-3.0). */
@Mixin(class_269.class)
public abstract class DonutCFakeStatsScoreboardMixin {
    @Inject(method = "method_1158", at = @At("HEAD"), cancellable = true)
    private void dih$fakeSidebar(class_8646 slot, class_266 objective, CallbackInfo ci) {
        if (FakeStats.redirect((class_269) (Object) this, slot, objective)) {
            ci.cancel();
        }
    }
}
