package dev.dihclient;

import dev.dihclient.emote.Emote;
import dev.dihclient.emote.EmotePose;

public final class EmoteTests {
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
        for (Emote e : Emote.values()) {
            EmotePose start = e.at(0.0);
            EmotePose end = e.at(e.seconds - 0.001);
            EmotePose after = e.at(e.seconds + 0.5);
            check(Math.abs(start.dy()) < 0.01 && Math.abs(start.pitch()) < 0.5 && Math.abs(start.roll()) < 0.5, e + " starts at rest");
            check(after.isNone(), e + " is over after its time");
            boolean moves = false;
            for (double t = 0; t < e.seconds; t += 0.05) {
                EmotePose p = e.at(t);
                moves |= Math.abs(p.dy()) > 0.05 || Math.abs(p.yaw()) > 5 || Math.abs(p.pitch()) > 5 || Math.abs(p.roll()) > 5;
                check(!Float.isNaN(p.dy()) && !Float.isNaN(p.yaw()) && !Float.isNaN(p.pitch()) && !Float.isNaN(p.roll()), e + " has no NaN at " + t);
            }
            check(moves, e + " moves");
            float turn = ((end.yaw() % 360) + 360) % 360;
            check(Math.min(turn, 360 - turn) < 8 || e != Emote.SPIN, e + " ends where it began");
        }
        check(Emote.byName("dance") == Emote.DANCE && Emote.byName("nope") == null && Emote.byName(null) == null, "names");
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
