package dev.dihclient;

import dev.dihclient.glue.LegalPlaceMath;

public final class LegalPlaceTests {
    static int failed;
    static int passed;

    static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + what);
        }
    }

    static boolean near(double[] v, double x, double y, double z) {
        return Math.abs(v[0] - x) < 1e-6 && Math.abs(v[1] - y) < 1e-6 && Math.abs(v[2] - z) < 1e-6;
    }

    public static void main(String[] args) {
        check(near(LegalPlaceMath.look(0, 0), 0, 0, 1), "yaw 0 looks along +z (south)");
        check(near(LegalPlaceMath.look(90, 0), -1, 0, 0), "yaw 90 looks along -x (west)");
        check(near(LegalPlaceMath.look(-90, 0), 1, 0, 0), "yaw -90 looks along +x (east)");
        check(near(LegalPlaceMath.look(180, 0), 0, 0, -1), "yaw 180 looks along -z (north)");
        check(near(LegalPlaceMath.look(0, -90), 0, 1, 0), "pitch -90 looks up");
        check(near(LegalPlaceMath.look(0, 90), 0, -1, 0), "pitch 90 looks down");
        double[] v = LegalPlaceMath.look(37, 21);
        check(Math.abs(Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) - 1) < 1e-9, "unit length");
        check(LegalPlaceMath.inReach(4.5, 0, 0), "4.5 blocks is in reach");
        check(!LegalPlaceMath.inReach(4.6, 0, 0), "4.6 blocks is not");
        check(LegalPlaceMath.inReach(3, 3, 0.1), "3D distance 4.24 is in reach");
        check(!LegalPlaceMath.inReach(3, 3, 3), "3D distance 5.2 is not");
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
