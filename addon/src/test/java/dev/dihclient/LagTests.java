package dev.dihclient;

import dev.dihclient.port.lag.LagMap;
import java.util.List;

public class LagTests {
    private static int failed;
    private static int passed;

    private static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + what);
        }
    }

    /** Walks along a line, one sample per second at 4 blocks per second; the server loses TPS within 90 blocks of (sx, sz). */
    private static void walk(LagMap map, double fromX, double toX, double z, double sx, double sz, double radius, double slow) {
        double step = 4.0 * Math.signum(toX - fromX);
        for (double x = fromX; step > 0 ? x <= toX : x >= toX; x += step) {
            double d = Math.hypot(x - sx, z - sz);
            map.add(x, z, d < radius ? slow : 19.9);
        }
    }

    public static void main(String[] args) {
        LagMap quiet = new LagMap();
        walk(quiet, 0, 800, 0, 99999, 99999, 90, 12);
        check(quiet.zones(3.0, 5).isEmpty(), "no source: no zone");
        check(quiet.baseline() > 19.5, "normal baseline is about 20");

        LagMap map = new LagMap();
        walk(map, 0, 800, 0, 500, 40, 90, 13.5);
        walk(map, 800, 0, 100, 500, 40, 90, 13.5);
        walk(map, 0, 800, -100, 500, 40, 90, 13.5);
        List<LagMap.Zone> zones = map.zones(3.0, 5);
        check(zones.size() == 1, "one source: one zone, got " + zones.size());
        if (!zones.isEmpty()) {
            LagMap.Zone z = zones.get(0);
            check(Math.abs(z.x() - 500) < 60, "zone x near the source: " + z.x());
            check(Math.abs(z.z() - 40) < 80, "zone z near the source: " + z.z());
            check(z.avgTps() < 15.0, "average TPS of the zone is low: " + z.avgTps());
            check(z.cells() >= 2, "several cells");
        }

        LagMap spike = new LagMap();
        walk(spike, 200, 300, 0, 99999, 99999, 1, 20);
        for (int i = 0; i < 7; i++) {
            spike.add(40, 0, 8); // a short lag spike in one cell
        }
        check(spike.zones(3.0, 5).isEmpty(), "one lonely cell with few samples is not a zone");
        for (int i = 0; i < 6; i++) {
            spike.add(40, 0, 8); // standing there for a long time
        }
        check(spike.zones(3.0, 5).size() == 1, "a lonely cell with many samples is a zone (standing in a slow place)");

        LagMap global = new LagMap();
        for (double x = 0; x < 800; x += 4) {
            global.add(x, 0, 12.0); // the whole server is slow everywhere
        }
        check(global.zones(3.0, 5).isEmpty(), "the same low TPS everywhere is no zone");

        LagMap few = new LagMap();
        for (int i = 0; i < 3; i++) {
            few.add(10, 10, 5);
            few.add(40, 10, 5);
        }
        check(few.zones(3.0, 5).isEmpty(), "too few samples are not judged");

        LagMap cleared = new LagMap();
        walk(cleared, 0, 800, 0, 500, 40, 90, 13.5);
        cleared.clear();
        check(cleared.cellCount() == 0 && cleared.zones(3.0, 5).isEmpty(), "clear forgets everything");

        System.out.println("LagTests: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
