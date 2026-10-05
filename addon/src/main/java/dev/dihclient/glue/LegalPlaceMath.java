package dev.dihclient.glue;

public final class LegalPlaceMath {

    public static final double REACH = 4.5;

    private LegalPlaceMath() {
    }

    public static double[] look(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch);
        return new double[]{-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp};
    }

    public static boolean inReach(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz <= (REACH + 0.02) * (REACH + 0.02);
    }
}
