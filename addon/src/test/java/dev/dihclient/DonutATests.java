package dev.dihclient;

import dev.dihclient.port.donuta.AutoRelogLogic;
import dev.dihclient.port.donuta.DonutNoFallLogic;
import dev.dihclient.port.donuta.DonutSpeedMineLogic;

public final class DonutATests {
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
        noFall();
        speedMine();
        relog();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void noFall() {
        DonutNoFallLogic n = new DonutNoFallLogic();
        // normal walking: nothing changes, the real ground flag goes out
        check(!n.tick(0.0), "no force while not armed");
        DonutNoFallLogic.Send s = n.packet(true, true);
        check(!s.nudge && s.onGround, "unarmed: real flag, no nudge");
        // a short fall does not arm
        n.tick(2.9);
        n.tick(0.0);
        check(!n.armed(), "fall of 2.9 does not arm");
        // a long fall arms one tick after the landing resets the distance
        n.tick(3.5);
        check(!n.armed(), "still falling: not armed yet");
        check(!n.tick(0.0) && n.armed(), "armed the tick after the 3.5 block fall");
        check(n.tick(0.0), "armed: player is held in the air");
        // 4 hidden ground packets: nudge on 1 and 3, flag false, then back to normal
        s = n.packet(false, true);
        check(!s.nudge && !s.onGround && n.groundTicks() == 0, "air packet while armed: no count, flag false");
        s = n.packet(true, true);
        check(s.nudge && !s.onGround && n.groundTicks() == 1, "ground packet 1: nudge, flag false");
        s = n.packet(true, true);
        check(!s.nudge && !s.onGround && n.groundTicks() == 2, "ground packet 2: no nudge");
        s = n.packet(true, true);
        check(s.nudge && !s.onGround, "ground packet 3: nudge");
        s = n.packet(true, true);
        check(!s.nudge && !s.onGround && !n.armed() && n.groundTicks() == 0, "ground packet 4: still false, then disarmed");
        s = n.packet(true, true);
        check(!s.nudge && s.onGround, "afterwards the real flag again");
        check(!n.tick(0.0), "no force after the reset");
        // exactly 3.0 does not arm (strictly greater)
        n.reset();
        n.tick(3.0);
        n.tick(0.0);
        check(!n.armed(), "3.0 does not arm");
        n.tick(3.01);
        n.tick(0.0);
        check(n.armed(), "3.01 arms");
        n.reset();
        check(!n.armed() && n.groundTicks() == 0, "reset");
        // the player is in the air (not on ground) in the packet: false either way
        n.tick(10.0);
        n.tick(0.0);
        s = n.packet(false, false);
        check(!s.onGround, "air + armed: false");
    }

    static void speedMine() {
        check(Math.abs(DonutSpeedMineLogic.scaleProgress(0.07F, 0.7) - 0.1F) < 1e-6, "0.07 / 0.7 = 0.1");
        check(DonutSpeedMineLogic.scaleProgress(0.5F, 1.0) == 0.5F, "break at 1.0 changes nothing");
        check(DonutSpeedMineLogic.scaleProgress(0.5F, 0.0) == 0.5F, "break at 0 is ignored, not infinite");
        check(DonutSpeedMineLogic.scaleProgress(0.3F, 0.3) > 0.99F, "break at 0.3 gives a bit more than x3");
        check(!DonutSpeedMineLogic.dropsDelay(false, false, 0.1F), "skip off: never");
        check(!DonutSpeedMineLogic.dropsDelay(false, true, 2.0F), "skip off + keep instant: never");
        check(DonutSpeedMineLogic.dropsDelay(true, false, 5.0F), "skip on, keep instant off: always");
        check(DonutSpeedMineLogic.dropsDelay(true, true, 0.4F), "skip on, slow block: dropped");
        check(!DonutSpeedMineLogic.dropsDelay(true, true, 1.0F), "keep instant: instant block keeps its delay");
        check(!DonutSpeedMineLogic.dropsDelay(true, true, 3.0F), "keep instant: faster than instant keeps delay");
    }

    static void relog() {
        AutoRelogLogic r = new AutoRelogLogic();
        // never fires without having been above the line first
        check(!r.tick(-10.0, 0.0), "below the line from the start: no relog");
        // above for 39 ticks: not armed
        for (int i = 0; i < 39; i++) {
            check(!r.tick(64.0, 0.0), "above");
        }
        check(!r.tick(-1.0, 0.0), "39 ticks above is not enough");
        // armed after 40 ticks, fires once on the way down
        for (int i = 0; i < 45; i++) {
            r.tick(64.0, 0.0);
        }
        check(r.aboveTicks() == AutoRelogLogic.REARM_TICKS, "counter is capped");
        check(!r.tick(2.0, 0.0), "between line and line+4 nothing happens");
        check(!r.tick(0.0, 0.0), "exactly on the line is not below");
        check(r.tick(-0.5, 0.0), "below the line while armed: fire");
        check(!r.tick(-5.0, 0.0), "no second relog");
        // within the 4 block band the counter does not grow: standing at line+3 never arms
        for (int i = 0; i < 100; i++) {
            r.tick(3.0, 0.0);
        }
        check(!r.tick(-1.0, 0.0), "hovering at the line does not arm");
        // a custom line
        for (int i = 0; i < 40; i++) {
            r.tick(-40.0, -60.0);
        }
        check(!r.tick(-59.0, -60.0), "line -60: -59 is above");
        check(r.tick(-61.0, -60.0), "line -60: -61 fires");
        // reset (world change) disarms
        for (int i = 0; i < 40; i++) {
            r.tick(64.0, 0.0);
        }
        r.reset();
        check(!r.tick(-1.0, 0.0), "reset disarms");
    }
}
