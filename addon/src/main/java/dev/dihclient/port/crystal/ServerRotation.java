package dev.dihclient.port.crystal;

import net.minecraft.class_10185;
import net.minecraft.class_3532;
import net.minecraft.class_746;

/** Ported from an open-source client (GPL-3.0). */
public final class ServerRotation {
    private static Rotation requested;
    private static Rotation sent;
    private static float ownYaw;
    private static float ownPitch;
    private static boolean swapped;
    private static Rotation walked;

    private ServerRotation() {
    }

    public static void request(Rotation rotation) {
        requested = rotation;
    }

    public static Rotation sent() {
        return sent;
    }

    public static void apply(class_746 player) {
        if (requested != null && !swapped) {
            ownYaw = player.method_36454();
            ownPitch = player.method_36455();
            float anchor = sent != null ? sent.yaw() : ownYaw;
            player.method_36456(Bypass.aimYaw(anchor, requested.yaw()));
            player.method_36457(Bypass.aimPitch(requested.pitch()));
            swapped = true;
        }
    }

    public static void applyForMovement(class_746 player) {
        if (requested != null && !swapped && correctable(player)) {
            if (player.field_3944.method_76760() && !player.method_5765()) {
                apply(player);
                walked = new Rotation(player.method_36454(), player.method_36455());
            }
        }
    }

    public static boolean correctable(class_746 player) {
        return !player.method_6128() && !player.method_6123();
    }

    public static class_10185 correctInput(class_10185 keys) {
        if (walked == null) {
            return keys;
        } else {
            int forward = impulse(keys.comp_3159(), keys.comp_3160());
            int strafe = impulse(keys.comp_3161(), keys.comp_3162());
            if (forward == 0 && strafe == 0) {
                return keys;
            } else {
                double[] wanted = heading(ownYaw, strafe, forward);
                int bestForward = forward;
                int bestStrafe = strafe;
                double bestDot = Double.NEGATIVE_INFINITY;

                for (int f = -1; f <= 1; f++) {
                    for (int s = -1; s <= 1; s++) {
                        if (f != 0 || s != 0) {
                            double[] way = heading(walked.yaw(), s, f);
                            double dot = way[0] * wanted[0] + way[1] * wanted[1];
                            if (dot > bestDot) {
                                bestDot = dot;
                                bestForward = f;
                                bestStrafe = s;
                            }
                        }
                    }
                }

                return bestForward == forward && bestStrafe == strafe
                    ? keys
                    : new class_10185(bestForward > 0, bestForward < 0, bestStrafe > 0, bestStrafe < 0, keys.comp_3163(), keys.comp_3164(), keys.comp_3165());
            }
        }
    }

    public static void finishTick(class_746 player) {
        if (swapped) {
            restore(player);
        }

        requested = null;
    }

    private static int impulse(boolean positive, boolean negative) {
        return positive == negative ? 0 : (positive ? 1 : -1);
    }

    private static double[] heading(float yaw, int strafe, int forward) {
        double sin = class_3532.method_15374(yaw * (float) (Math.PI / 180.0));
        double cos = class_3532.method_15362(yaw * (float) (Math.PI / 180.0));
        double x = strafe * cos - forward * sin;
        double z = forward * cos + strafe * sin;
        double length = Math.sqrt(x * x + z * z);
        return new double[]{x / length, z / length};
    }

    public static boolean yawFree(class_746 player) {
        class_10185 keys = player.field_3913.field_54155;
        if (keys.comp_3159() || keys.comp_3160() || keys.comp_3161() || keys.comp_3162()) {
            return false;
        } else {
            return keys.comp_3163() && player.method_5624() ? false : !player.method_6128() && !player.method_6123();
        }
    }

    public static void beforeSend(class_746 player) {
        if (!swapped && requested != null) {
            if (!yawFree(player)) {
                requested = null;
            } else {
                ownYaw = player.method_36454();
                ownPitch = player.method_36455();
                float anchor = sent != null ? sent.yaw() : ownYaw;
                player.method_36456(Bypass.aimYaw(anchor, requested.yaw()));
                player.method_36457(Bypass.aimPitch(requested.pitch()));
                swapped = true;
            }
        }
    }

    public static void afterSend(class_746 player) {
        sent = new Rotation(player.method_36454(), player.method_36455());
        requested = null;
        if (swapped) {
            restore(player);
        }
    }

    private static void restore(class_746 player) {
        if (walked != null) {
            player.field_3932 = player.field_3932 + (ownYaw - walked.yaw()) * 0.5F;
            player.field_3916 = player.field_3916 + (ownPitch - walked.pitch()) * 0.5F;
            walked = null;
        }

        player.method_36456(ownYaw);
        player.method_36457(ownPitch);
        swapped = false;
    }
}
