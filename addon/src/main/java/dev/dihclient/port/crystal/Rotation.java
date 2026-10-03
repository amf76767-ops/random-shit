package dev.dihclient.port.crystal;

import dev.dihclient.util.RotationUtil;
import net.minecraft.class_243;

/** Ported from Anubis Client 0.9.8 (GPL-3.0). Yaw and pitch in degrees. */
public record Rotation(float yaw, float pitch) {
    /** Look from the player's eyes at a point, with the DIH rotation helper so all modules agree on the angles. */
    public static Rotation toward(class_243 point) {
        float[] r = RotationUtil.rotationsTo(point);
        return new Rotation(r[0], r[1]);
    }
}
