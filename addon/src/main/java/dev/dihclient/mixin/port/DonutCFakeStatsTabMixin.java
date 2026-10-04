package dev.dihclient.mixin.port;

import dev.dihclient.port.donutc.FakeStats;
import net.minecraft.class_2561;
import net.minecraft.class_269;
import net.minecraft.class_355;
import net.minecraft.class_5348;
import net.minecraft.class_640;
import net.minecraft.class_9013;
import net.minecraft.class_9015;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ported from an open-source client (GPL-3.0) (MixinFakeStatsPlayerTabOverlay).
 * The vanilla tab list (PlayerTabOverlay): fake money in your own row, in its score column and in header/footer.
 * Only inside the vanilla tab list: BetterTablist cancels the whole vanilla list in InGameHudMixin, then none of this runs.
 * Each handler touches only the value it is given, so it stacks with other mixins on the same methods.
 */
@Mixin(class_355.class)
public abstract class DonutCFakeStatsTabMixin {
    /** PlayerTabOverlay#getNameForDisplay(PlayerInfo) */
    @Inject(method = "method_1918", at = @At("RETURN"), cancellable = true)
    private void dih$fakeStatsTabName(class_640 info, CallbackInfoReturnable<class_2561> cir) {
        class_2561 original = cir.getReturnValue();
        if (info != null && info.method_2966() != null) {
            class_2561 shown = FakeStats.tabName(info.method_2966().id(), original);
            if (shown != original) {
                cir.setReturnValue(shown);
            }
        }
    }

    /** The score column of the tab list: Scoreboard#getPlayerScoreInfo inside render(). */
    @Redirect(method = "method_1919",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_269;method_55430(Lnet/minecraft/class_9015;Lnet/minecraft/class_266;)Lnet/minecraft/class_9013;"))
    private class_9013 dih$fakeStatsTabScore(class_269 board, class_9015 holder, net.minecraft.class_266 objective) {
        return FakeStats.tabScore(holder, board.method_55430(holder, objective));
    }

    /** Header: the first Font#split call of render(). */
    @ModifyArg(method = "method_1919",
            at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/class_327;method_1728(Lnet/minecraft/class_5348;I)Ljava/util/List;"),
            index = 0)
    private class_5348 dih$fakeStatsTabHeader(class_5348 header) {
        return FakeStats.tabText(header);
    }

    /** Footer: the second Font#split call of render(). */
    @ModifyArg(method = "method_1919",
            at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/class_327;method_1728(Lnet/minecraft/class_5348;I)Ljava/util/List;"),
            index = 0)
    private class_5348 dih$fakeStatsTabFooter(class_5348 footer) {
        return FakeStats.tabText(footer);
    }
}
