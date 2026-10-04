package dev.dihclient;

import dev.dihclient.port.trident.TridentLogic;
import dev.dihclient.port.trident.TridentLogic.Step;

public final class TridentTests {
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

    public static void main(String[] args) {
        charge();
        steps();
        boost();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void charge() {
        check(TridentLogic.scaledCharge(10, 1.0) == 10, "scale 1 = vanilla");
        check(TridentLogic.scaledCharge(10, 0.0) == 1, "scale 0 -> one tick, never zero");
        check(TridentLogic.scaledCharge(10, 0.3) == 3, "scale 0.3 -> 3 (float noise rounded)");
        check(TridentLogic.scaledCharge(10, 0.5) == 5, "scale 0.5 -> 5");
        check(TridentLogic.scaledCharge(10, 0.04) == 1, "tiny scale -> 1");
        check(TridentLogic.releaseTicks(1) == 9, "Riptide never releases before 9 ticks");
        check(TridentLogic.releaseTicks(10) == 10, "vanilla charge is kept");
        check(TridentLogic.releaseTicks(9) == 9, "9 stays 9");
    }

    static void steps() {
        int r = TridentLogic.releaseTicks(10);
        check(TridentLogic.step(false, false, 0, r, 100, -100) == Step.START_USE, "idle, cooled down -> start");
        check(TridentLogic.step(false, false, 0, r, 105, 100) == Step.NONE, "idle, 5 ticks after release -> wait");
        check(TridentLogic.step(false, false, 0, r, 109, 100) == Step.START_USE, "idle, exactly 9 ticks after release -> start");
        check(TridentLogic.step(true, true, 4, r, 200, -100) == Step.NONE, "charging, not charged yet -> wait");
        check(TridentLogic.step(true, true, 10, r, 200, -100) == Step.RELEASE, "charged -> release");
        check(TridentLogic.step(true, true, 10, r, 205, 200) == Step.NONE, "charged but released 5 ticks ago -> wait");
        check(TridentLogic.step(true, false, 50, r, 200, -100) == Step.NONE, "using something else -> leave it alone");
        check(TridentLogic.step(true, true, 9, TridentLogic.releaseTicks(1), 200, -100) == Step.RELEASE, "fast charge releases at 9");
        check(TridentLogic.step(true, true, 8, TridentLogic.releaseTicks(1), 200, -100) == Step.NONE, "fast charge still waits for 9");
    }

    static void boost() {
        check(TridentLogic.boosted(1.5, 2.0) == 3.0, "x2");
        check(TridentLogic.boosted(-0.5, 1.0) == -0.5, "x1 keeps it");
        check(TridentLogic.boosted(0.8, 0.5) == 0.4, "x0.5 slows");
        check(TridentLogic.cooledDown(9, 0), "cooled down at 9");
        check(!TridentLogic.cooledDown(8, 0), "not at 8");
    }
}
