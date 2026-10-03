package dev.dihclient.mixin;

import dev.dihclient.glue.Emotes;
import net.minecraft.class_10042;
import net.minecraft.class_4587;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves and tilts the vanilla body of a player who is doing an emote (the model path does its own). */
@Mixin(targets = "net.minecraft.class_922")
public abstract class EmoteTransformMixin {
    @Inject(
        method = {"method_4058(Lnet/minecraft/class_10042;Lnet/minecraft/class_4587;FF)V"},
        at = {@At("TAIL")}
    )
    private void dih$emote(class_10042 state, class_4587 matrices, float bodyYaw, float scale, CallbackInfo ci) {
        Emotes.transform(state, matrices);
    }
}
