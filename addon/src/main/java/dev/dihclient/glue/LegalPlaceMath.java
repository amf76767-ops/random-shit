package dev.dihclient.glue;

/** The small bits of geometry behind {@link LegalPlace}, without any Minecraft class so they can be tested. */
public final class LegalPlaceMath {
    /** The farthest a placement may be from the eye, in blocks. Survival reach is 4.5; creative's 5.0 is not used. */
    public static final double REACH = 4.5;

    private LegalPlaceMath() {
    }

    /** Unit vector the player looks along, for Minecraft's yaw and pitch in degrees (yaw 0 = +z, yaw -90 = +x, pitch -90 = up). */
    public static double[] look(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch);
        return new double[]{-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp};
    }

    /** True when the point that was clicked is not farther from the eye than a player can reach. */
    public static boolean inReach(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz <= (REACH + 0.02) * (REACH + 0.02);
    }
}
