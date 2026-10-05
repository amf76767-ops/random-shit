package dev.dihclient.mixin.port;

import dev.dihclient.modules.fun.Hats;
import net.minecraft.class_1657;
import net.minecraft.class_243;
import net.minecraft.class_3532;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Hats.class)
public abstract class HatTiltMixin {

    private static final double STAND_HEIGHT = 1.8;

    @Shadow
    private class_243 pivot;
    @Shadow
    private float yawRad;
    @Shadow
    private double scale;

    private double dih$tilt;
    private double dih$feetX;
    private double dih$feetY;
    private double dih$feetZ;
    private double dih$lift;

    @Inject(method = "drawFor", at = @At("HEAD"), require = 0)
    private void dih$measureTilt(class_1657 player, Hats.Hat hat, float delta, CallbackInfo ci) {
        this.dih$tilt = 0.0;
        double amount;
        if (player.method_6128()) {
            float ticks = player.method_6003() + delta;
            amount = class_3532.method_15363(ticks * ticks / 100.0F, 0.0F, 1.0F);
        } else if (player.method_20232()) {
            amount = player.method_6024(delta);
        } else {
            return;
        }
        if (amount <= 0.001) {
            return;
        }
        float pitch = class_3532.method_16439(delta, player.field_6004, player.method_36455());
        this.dih$tilt = Math.toRadians(amount * (90.0 + pitch));
        this.dih$feetX = class_3532.method_16436(delta, player.field_6038, player.method_23317());
        this.dih$feetY = class_3532.method_16436(delta, player.field_5971, player.method_23318());
        this.dih$feetZ = class_3532.method_16436(delta, player.field_5989, player.method_23321());

        this.dih$lift = STAND_HEIGHT - player.method_17682();
    }

    @Inject(method = "world", at = @At("RETURN"), cancellable = true, require = 0)
    private void dih$tiltPoint(double x, double y, double z, CallbackInfoReturnable<class_243> cir) {
        if (this.dih$tilt == 0.0 || this.pivot == null) {
            return;
        }

        double lx = x * this.scale;
        double ly = (y - 0.45) * this.scale + 0.45;
        double lz = z * this.scale;
        double sin = Math.sin(this.yawRad);
        double cos = Math.cos(this.yawRad);
        double up = this.pivot.field_1351 - this.dih$feetY + this.dih$lift + ly;
        double forward = lz;
        double side = lx;
        double t = this.dih$tilt;
        double up2 = up * Math.cos(t) - forward * Math.sin(t);
        double forward2 = up * Math.sin(t) + forward * Math.cos(t);

        double wx = this.dih$feetX + side * cos - forward2 * sin;
        double wz = this.dih$feetZ + side * sin + forward2 * cos;
        cir.setReturnValue(new class_243(wx, this.dih$feetY + up2, wz));
    }
}
