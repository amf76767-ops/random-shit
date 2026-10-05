package dev.dihclient.port.crystal;

import dev.dihclient.util.RotationUtil;
import net.minecraft.class_243;

/** Ported from an open-source client (GPL-3.0). */
public record Rotation(float yaw, float pitch) {

    public static Rotation toward(class_243 point) {
        float[] r = RotationUtil.rotationsTo(point);
        return new Rotation(r[0], r[1]);
    }
}
