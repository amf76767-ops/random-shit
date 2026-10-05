package dev.dihclient.util;

import java.util.Random;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_746;

public final class HumanAim {
    private static final class_310 mc = class_310.method_1551();
    private static final Random RNG = new Random();
    private static final float STEP = 0.15F;

    private static final float SPEED_SHARE = 0.6F;

    private static final float ACCEL_SHARE = 0.3F;

    private static float velYaw;
    private static float velPitch;
    private static int lastTick = -1000;

    private HumanAim() {
    }

    public static float distance(float var0, float var1) {
        class_746 var2 = mc.field_1724;
        if (var2 == null) {
            return 999.0F;
        } else {
            float var3 = class_3532.method_15393(var0 - var2.method_36454());
            float var4 = var1 - var2.method_36455();
            return (float) Math.sqrt(var3 * var3 + var4 * var4);
        }
    }

    public static boolean step(float var0, float var1, float var2, float var3) {
        return step(var0, var1, var2, var3, 1.0F);
    }

    public static boolean step(float targetYaw, float targetPitch, float maxSpeed, float tolerance, float noise) {
        class_746 p = mc.field_1724;
        if (p == null) {
            return false;
        }
        int tick = p.field_6012;
        if (tick - lastTick > 3 || tick < lastTick) {
            velYaw = 0.0F;
            velPitch = 0.0F;
        }
        boolean again = tick == lastTick;
        lastTick = tick;

        targetPitch = class_3532.method_15363(targetPitch, -90.0F, 90.0F);
        float yaw = p.method_36454();
        float pitch = p.method_36455();
        float dy = class_3532.method_15393(targetYaw - yaw);
        float dp = targetPitch - pitch;
        float dist = (float) Math.sqrt(dy * dy + dp * dp);
        if (dist <= tolerance) {
            p.method_36456(yaw + snap(dy));
            p.method_36457(class_3532.method_15363(pitch + snap(dp), -90.0F, 90.0F));
            velYaw *= 0.4F;
            velPitch *= 0.4F;
            return true;
        }

        float cap = Math.max(3.0F, maxSpeed * SPEED_SHARE);
        float want = Math.min(cap, Math.max(0.9F, dist * 0.22F));
        want *= 0.92F + RNG.nextFloat() * 0.16F;
        float wy = dy / dist * want;
        float wp = dp / dist * want;
        if (!again) {
            float ay = wy - velYaw;
            float ap = wp - velPitch;
            float al = (float) Math.sqrt(ay * ay + ap * ap);
            float accel = Math.max(1.5F, cap * ACCEL_SHARE);
            if (al > accel) {
                ay *= accel / al;
                ap *= accel / al;
            }
            velYaw += ay;
            velPitch += ap;
        }
        float sy = velYaw;
        float sp = velPitch;

        if (Math.abs(sy) > Math.abs(dy) && sy * dy > 0) {
            sy = dy;
        }
        if (Math.abs(sp) > Math.abs(dp) && sp * dp > 0) {
            sp = dp;
        }
        if (dist > 10.0F) {
            sy += (RNG.nextFloat() - 0.5F) * 0.4F * noise;
            sp += (RNG.nextFloat() - 0.5F) * 0.25F * noise;
        } else if (noise > 1.0F && dist > 3.0F) {
            sy += (RNG.nextFloat() - 0.5F) * 0.15F * (noise - 1.0F);
            sp += (RNG.nextFloat() - 0.5F) * 0.1F * (noise - 1.0F);
        }
        p.method_36456(yaw + snap(sy));
        p.method_36457(class_3532.method_15363(pitch + snap(sp), -90.0F, 90.0F));
        return distance(targetYaw, targetPitch) <= tolerance;
    }

    public static boolean stepTo(class_243 var0, float var1, float var2) {
        return stepTo(var0, var1, var2, 1.0F);
    }

    public static boolean stepTo(class_243 var0, float var1, float var2, float var3) {
        float[] var4 = RotationUtil.rotationsTo(var0);
        return step(var4[0], var4[1], var1, var2, var3);
    }

    private static float snap(float var0) {
        return Math.round(var0 / STEP) * STEP;
    }
}
