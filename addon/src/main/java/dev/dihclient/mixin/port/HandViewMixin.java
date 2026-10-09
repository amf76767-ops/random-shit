package dev.dihclient.mixin.port;

import dev.dihclient.port.handview.HandView;
import net.minecraft.class_11659;
import net.minecraft.class_1268;
import net.minecraft.class_1799;
import net.minecraft.class_4587;
import net.minecraft.class_742;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_759")
public abstract class HandViewMixin {
    @Inject(method = "method_3228", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_759;method_3233(Lnet/minecraft/class_1309;Lnet/minecraft/class_1799;Lnet/minecraft/class_811;Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;I)V"), require = 0)
    private void dih$handItem(class_742 player, float tickDelta, float pitch, class_1268 hand, float swing, class_1799 item, float equip, class_4587 matrices, class_11659 queue, int light, CallbackInfo ci) {
        HandView.applyItem(matrices, hand == class_1268.field_5808);
    }

    @Inject(method = "method_3228", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_759;method_3219(Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;IFFLnet/minecraft/class_1306;)V"), require = 0)
    private void dih$handArm(class_742 player, float tickDelta, float pitch, class_1268 hand, float swing, class_1799 item, float equip, class_4587 matrices, class_11659 queue, int light, CallbackInfo ci) {
        HandView.applyArm(matrices, hand == class_1268.field_5808);
    }
}
