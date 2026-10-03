package dev.dihclient;

import dev.dihclient.ai.SupervisorEngine;
import dev.dihclient.ai.SupervisorEngine.Action;
import dev.dihclient.ai.SupervisorEngine.Sample;
import dev.dihclient.ai.SupervisorEngine.Type;
import dev.dihclient.ai.SupervisorEngine.World;
import java.util.ArrayList;
import java.util.List;

public final class SupervisorTests {
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

    static World w(double x, float hp, int strangers) {
        return new World(x, 64, 0, hp, strangers, strangers > 0 ? "Steve" : null, false);
    }

    static Sample s(String id, boolean on, String prog) {
        return new Sample(id, on, prog, false);
    }

    static long count(List<Action> a, Type t, String id) {
        return a.stream().filter(x -> x.type() == t && (id == null || id.equals(x.id()))).count();
    }

    public static void main(String[] args) {
        SupervisorEngine.Config c = new SupervisorEngine.Config();
        c.stuckTicks = 100;
        c.restartDelayTicks = 5;
        c.resumeDelayTicks = 40;
        c.forgiveTicks = 300;
        stuck(c);
        danger(c);
        misc(c);
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void stuck(SupervisorEngine.Config c) {
        SupervisorEngine e = new SupervisorEngine(c);
        List<Sample> none = List.of();
        boolean mineOn = true;
        int restarts = 0;
        boolean offered = false;
        long gaveUp = -1;
        for (long t = 0; t < 2000; t++) {
            List<Action> a = e.step(t, w(0, 20, 0), List.of(s("AutoMine", mineOn, "Status: Mining")), none);
            for (Action x : a) {
                if (x.type() == Type.DISABLE && x.text().equals("restart")) {
                    mineOn = false;
                    restarts++;
                } else if (x.type() == Type.ENABLE) {
                    mineOn = true;
                } else if (x.type() == Type.DISABLE) {
                    mineOn = false;
                    gaveUp = t;
                }
            }
            if (gaveUp >= 0) {
                break;
            }
        }
        check(restarts == 2, "restarted exactly maxRestarts times, got " + restarts);
        check(gaveUp > 0 && !mineOn, "then switched off");

        // progress by text change or by moving prevents any action
        e = new SupervisorEngine(c);
        int actions = 0;
        for (long t = 0; t < 1000; t++) {
            actions += e.step(t, w(0, 20, 0), List.of(s("AutoMine", true, "Mined: " + (t / 50))), none).size();
        }
        check(actions == 0, "changing status is progress");
        e = new SupervisorEngine(c);
        actions = 0;
        for (long t = 0; t < 1000; t++) {
            actions += e.step(t, w(t * 0.1, 20, 0), List.of(s("Goto", true, "same")), none).size();
        }
        check(actions == 0, "walking is progress");
        e = new SupervisorEngine(c);
        actions = 0;
        for (long t = 0; t < 1000; t++) {
            actions += e.step(t, w(0, 20, 0), List.of(new Sample("AutoFarm", true, "same", true)), none).size();
        }
        check(actions == 0, "benign waiting is never stuck");

        // restarts are forgiven after a long stretch of progress
        e = new SupervisorEngine(c);
        boolean on = true;
        int totalRestarts = 0;
        for (long t = 0; t < 3000; t++) {
            // stuck for 120 ticks, then progress for 600, repeating
            String prog = (t % 720) < 120 ? "frozen" : "run" + t;
            for (Action x : e.step(t, w(0, 20, 0), List.of(s("AutoMine", on, prog)), none)) {
                if (x.type() == Type.DISABLE && "restart".equals(x.text())) {
                    on = false;
                    totalRestarts++;
                } else if (x.type() == Type.ENABLE) {
                    on = true;
                } else if (x.type() == Type.DISABLE) {
                    on = false;
                }
            }
        }
        check(on && totalRestarts >= 3, "occasional stalls never exhaust the restart budget (restarts=" + totalRestarts + ")");

        // a module the player turns off is forgotten, not restarted
        e = new SupervisorEngine(c);
        actions = 0;
        for (long t = 0; t < 1000; t++) {
            actions += e.step(t, w(0, 20, 0), List.of(s("AutoMine", false, "x")), none).size();
        }
        check(actions == 0, "disabled modules are left alone");
    }

    static void danger(SupervisorEngine.Config c) {
        SupervisorEngine e = new SupervisorEngine(c);
        List<Sample> watched = List.of(s("AutoMine", true, "a"), s("Tunnel", false, "b"));
        List<Sample> guarded = List.of(s("AutoFish", true, "c"));
        List<Action> a = e.step(0, w(0, 6, 0), watched, guarded);
        check(count(a, Type.DISABLE, "AutoMine") == 1 && count(a, Type.DISABLE, "AutoFish") == 1, "low health pauses watched and guarded modules");
        check(count(a, Type.DISABLE, "Tunnel") == 0, "modules that were off stay untouched");
        check(count(a, Type.WARN, null) == 1, "one warning");

        List<Sample> off = List.of(s("AutoMine", false, "a"), s("Tunnel", false, "b"));
        List<Sample> offG = List.of(s("AutoFish", false, "c"));
        check(e.step(1, w(0, 12, 0), off, offG).isEmpty(), "health between pause and resume level: still paused, silent");
        check(e.step(2, w(0, 17, 0), off, offG).isEmpty(), "healed but not safe long enough");
        a = e.step(2 + c.resumeDelayTicks, w(0, 17, 0), off, offG);
        check(count(a, Type.ENABLE, "AutoMine") == 1 && count(a, Type.ENABLE, "AutoFish") == 1 && count(a, Type.ENABLE, "Tunnel") == 0,
                "resumes exactly what it paused");
        check(!e.inDanger() && e.pausedIds().isEmpty(), "state cleared");

        // player nearby
        e = new SupervisorEngine(c);
        a = e.step(0, w(0, 20, 1), List.of(s("AutoMine", true, "a")), List.of());
        check(count(a, Type.DISABLE, "AutoMine") == 1 && a.get(1).text().contains("Steve"), "stranger pauses and is named");
        e.step(10, w(0, 20, 1), off, offG);
        check(e.step(60, w(0, 20, 1), off, offG).isEmpty(), "stranger still there: no resume");
        e.step(61, w(0, 20, 0), off, offG);
        check(count(e.step(61 + c.resumeDelayTicks, w(0, 20, 0), off, offG), Type.ENABLE, "AutoMine") == 1, "resume after the stranger left");

        // the player turned a module on during danger, supervisor must not fight
        e = new SupervisorEngine(c);
        e.step(0, w(0, 20, 1), List.of(s("AutoMine", true, "a")), List.of());
        a = e.step(1, w(0, 20, 1), List.of(s("AutoMine", true, "a")), List.of());
        check(a.isEmpty(), "no repeated pausing while the danger lasts");
        a = e.step(1000, w(0, 20, 0), List.of(s("AutoMine", true, "a")), List.of());
        a = e.step(1000 + c.resumeDelayTicks, w(0, 20, 0), List.of(s("AutoMine", true, "a")), List.of());
        check(count(a, Type.ENABLE, null) == 0, "a module the player already re-enabled is not toggled again");

        // flapping: danger comes back before the delay ran out
        e = new SupervisorEngine(c);
        List<Sample> on = List.of(s("AutoMine", true, "a"));
        e.step(0, w(0, 20, 1), on, List.of());
        e.step(10, w(0, 20, 0), off, offG);
        e.step(30, w(0, 20, 1), off, offG);
        check(e.step(60, w(0, 20, 0), off, offG).isEmpty(), "the safe timer restarts when danger returns");

        // pause switches
        SupervisorEngine.Config no = new SupervisorEngine.Config();
        no.pauseForPlayers = false;
        no.pauseOnLowHealth = false;
        e = new SupervisorEngine(no);
        check(e.step(0, w(0, 1, 5), on, List.of()).isEmpty(), "both checks can be switched off");
    }

    static void misc(SupervisorEngine.Config c) {
        SupervisorEngine e = new SupervisorEngine(c);
        e.step(0, w(0, 20, 1), List.of(s("AutoMine", true, "a")), List.of());
        List<Action> a = e.step(1, new World(0, 0, 0, 0, 0, null, true), List.of(s("AutoMine", false, "a")), List.of());
        check(a.isEmpty() && !e.inDanger() && e.pausedIds().isEmpty(), "death clears everything");
        SupervisorEngine.Config off = new SupervisorEngine.Config();
        off.detectStuck = false;
        e = new SupervisorEngine(off);
        int n = 0;
        for (long t = 0; t < 5000; t++) {
            n += e.step(t, w(0, 20, 0), List.of(s("AutoMine", true, "x")), List.of()).size();
        }
        check(n == 0, "stuck detection can be switched off");
    }
}
