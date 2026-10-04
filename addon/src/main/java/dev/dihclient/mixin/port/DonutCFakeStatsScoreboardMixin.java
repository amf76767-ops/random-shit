package dev.dihclient.mixin.port;

import dev.dihclient.port.donutc.FakeStats;
import net.minecraft.class_266;
import net.minecraft.class_269;
import net.minecraft.class_8646;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0) (MixinFakeStatsScoreboard).
 * The server sets the sidebar objective through here; Fake Stats shows its own copy of it instead.
 */
@Mixin(class_269.class)
public abstract class DonutCFakeStatsScoreboardMixin {
    @Inject(method = "method_1158", at = @At("HEAD"), cancellable = true)
    private void dih$fakeSidebar(class_8646 slot, class_266 objective, CallbackInfo ci) {
        if (FakeStats.redirect((class_269) (Object) this, slot, objective)) {
            ci.cancel();
        }
    }
}
