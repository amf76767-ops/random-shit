package dev.dihclient;

import dev.dihclient.port.crystal.Bypass;
import dev.dihclient.port.crystal.CrystalMath;
import dev.dihclient.port.crystal.CrystalScore;

public final class CrystalAuraTests {
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

    static void near(double actual, double expected, String what) {
        check(actual == expected || Math.abs(actual - expected) < 1e-3, what + " (got " + actual + ", expected " + expected + ")");
    }

    public static void main(String[] args) {
        damage();
        exposure();
        shield();
        bypass();
        score();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void damage() {
        near(CrystalMath.falloff(0.0), 1.0, "no impact still 1");
        near(CrystalMath.falloff(0.5), 32.5, "half impact");
        near(CrystalMath.falloff(1.0), 85.0, "point blank is 85 (hard: 127.5)");

        near(CrystalMath.difficulty(0, 50), 0.0, "peaceful");
        near(CrystalMath.difficulty(1, 50), 26.0, "easy halves and adds one");
        near(CrystalMath.difficulty(1, 1), 1.0, "easy never raises small damage");
        near(CrystalMath.difficulty(2, 50), 50.0, "normal");
        near(CrystalMath.difficulty(3, 50), 75.0, "hard x1.5");

        near(CrystalMath.armor(85, 20, 8), 71.4, "diamond armor, big hit hits the 20 % floor");
        near(CrystalMath.armor(10, 20, 8), 3.0, "diamond armor, small hit");
        near(CrystalMath.armor(40, 0, 0), 40.0, "no armor");
        near(CrystalMath.enchants(100, 20), 20.0, "20 protection points take 80 %");
        near(CrystalMath.enchants(100, 99), 20.0, "protection is capped at 20");
        near(CrystalMath.enchants(100, 0), 100.0, "no enchants");
        near(CrystalMath.resistance(0), 0.8, "resistance I");
        near(CrystalMath.resistance(1), 0.6, "resistance II");
        near(CrystalMath.resistance(4), 0.0, "resistance V is immune");
        near(CrystalMath.resistance(9), 0.0, "never below zero");

        near(CrystalMath.damage(3.0, 1.0, true, 3, null, 20, 8, 1.0F, 20), 14.1435, "full pipeline");
        near(CrystalMath.damage(3.0, 1.0, true, 3, null, 20, 8, 0.8F, 20), 11.3148, "full pipeline with Resistance I");
        near(CrystalMath.damage(6.0, 0.5, false, 2, null, 0, 0, 1.0F, 0), 14.125, "mob, half exposure, no gear");
        near(CrystalMath.damage(12.5, 1.0, true, 3, null, 0, 0, 1.0F, 0), 0.0, "out of range");
        near(CrystalMath.damage(12.0, 1.0, true, 3, null, 0, 0, 1.0F, 0), 1.5, "range edge is still 1 damage (x1.5)");
        near(CrystalMath.damage(3.0, 0.0, true, 2, null, 0, 0, 1.0F, 0), 1.0, "fully covered still takes the 1 base damage");

        near(CrystalMath.upperBound(2.0, true, 1, 0, 0, 1.0F, 0), 33.5833, "upper bound on easy");
        near(CrystalMath.upperBound(2.0, false, 2, 0, 0, 1.0F, 0), 65.1667, "upper bound on a mob");
        near(CrystalMath.upperBound(13.0, true, 3, 0, 0, 1.0F, 0), 0.0, "upper bound out of range");
        float exact = CrystalMath.damage(2.0, 0.6, true, 3, null, 12, 2, 0.8F, 5);
        float upper = CrystalMath.upperBound(2.0, true, 3, 12, 2, 0.8F, 5);
        check(exact <= upper, "upper bound is never below the real damage");

        near(CrystalMath.damage(3.0, 1.0, true, 2, d -> d * 0.5F, 0, 0, 1.0F, 0), CrystalMath.falloff(0.75) * 0.5, "shield half");
        near(CrystalMath.damage(3.0, 1.0, true, 2, d -> 0.0F, 0, 0, 1.0F, 0), CrystalMath.falloff(0.75), "shield that blocks nothing");

        check(CrystalMath.endangers(10.0F, 13.4F), "10 damage x 1.15 + 2 = 13.5 reaches 13.4 health");
        check(!CrystalMath.endangers(10.0F, 13.6F), "but not 13.6");
        check(!CrystalMath.endangers(10.0F, 20.0F), "plenty of health is fine");
    }

    static void exposure() {
        double w = 0.6;
        double h = 1.8;
        int[] calls = {0};
        double[] minX = {Double.MAX_VALUE};
        double[] maxX = {-Double.MAX_VALUE};
        float all = CrystalMath.seen(0, 0, 0, w, h, w, (x, y, z) -> {
            calls[0]++;
            minX[0] = Math.min(minX[0], x);
            maxX[0] = Math.max(maxX[0], x);
            return true;
        });
        near(all, 1.0, "nothing in the way");
        check(calls[0] == 45, "a standing player is sampled 3 x 5 x 3 = 45 times (was " + calls[0] + ")");
        near(minX[0], (1.0 - 2 * (1.0 / 2.2)) / 2.0, "grid is centred in x");
        check(maxX[0] <= w, "no sample outside the box");
        near(CrystalMath.seen(0, 0, 0, w, h, w, (x, y, z) -> false), 0.0, "all covered");
        near(CrystalMath.seen(0, 0, 0, w, h, w, (x, y, z) -> y >= 0.9), 18.0 / 45.0, "only the upper body is seen");
        near(CrystalMath.seen(0, 0, 0, 0.0, 0.0, 0.0, (x, y, z) -> true), 1.0, "a point-sized box has one sample");
    }

    static void shield() {
        near(CrystalMath.shieldAngle(0, 1, 0, 1), 0.0, "looking straight at the blast");
        near(CrystalMath.shieldAngle(0, 1, 0, -1), Math.PI, "back to the blast");
        near(CrystalMath.shieldAngle(1, 0, 0, 1), Math.PI / 2, "blast at the side");
        near(CrystalMath.shieldAngle(0, 0, 0, 1), Math.PI / 2, "blast right on top counts as sideways");
        near(CrystalMath.shieldAngle(5, 5, 0.7071067811865476, 0.7071067811865476), 0.0, "unnormalised direction");
    }

    static void bypass() {
        Bypass.Profile grim = Bypass.Profile.GRIM_ALDENZ;
        Bypass.Profile off = Bypass.Profile.OFF;
        near(Bypass.clampBreakRange(grim, 6.0F), 3.02, "grim break range cap");
        near(Bypass.clampBreakRange(grim, 2.0F), 2.0, "below the cap stays");
        near(Bypass.clampBreakRange(off, 6.0F), 6.0, "no cap without profile");
        near(Bypass.clampPlaceRange(grim, 6.0F), 4.5, "grim place range cap");
        check(Bypass.requiresLook(grim) && !Bypass.requiresLook(off), "only grim needs the look");
        check(Bypass.minDelay(off, 0) == 1 && Bypass.minDelay(grim, 0) == 0 && Bypass.minDelay(grim, 3) == 3, "min delay");
        check(Bypass.minSwitchInterval(grim) == 1 && Bypass.minSwitchInterval(off) == 2, "switch interval");
        check(Bypass.confirmTicks(grim, 9) == 1 && Bypass.confirmTicks(off, 9) == 9 && Bypass.confirmTicks(off, 0) == 1, "confirm ticks");
        check(Bypass.mayRetryCrystal(grim, 5, 5, 0, 8) == false, "grim: same crystal, hit this tick, wait");
        check(Bypass.mayRetryCrystal(grim, 5, 5, 1, 8), "grim: retry after one tick");
        check(!Bypass.mayRetryCrystal(off, 5, 5, 7, 8) && Bypass.mayRetryCrystal(off, 5, 5, 8, 8), "off: wait the ping window");
        check(Bypass.mayRetryCrystal(off, 6, 5, 0, 8), "another crystal is free at once");
        near(Bypass.aimYaw(10.0F, 20.0F), 20.0, "yaw close by");
        near(Bypass.aimYaw(10.0F, 190.0F), -170.0, "180 degrees away takes the short way round");
        near(Bypass.aimYaw(350.0F, 10.0F), 370.0, "wraps around 360");
        near(Bypass.aimYaw(10.0F, 10.0F), 10.01, "never repeats the same yaw");
        near(Bypass.aimPitch(120.0F), 90.0, "pitch cap down");
        near(Bypass.aimPitch(-120.0F), -90.0, "pitch cap up");
        check(Bypass.Profile.of("Grim-Aldenz").grim() && !Bypass.Profile.of("Off").grim() && !Bypass.Profile.of(null).grim(), "profile names");

        long[] now = {5000L};
        Bypass.Budget budget = new Bypass.Budget(() -> now[0]);
        for (int i = 0; i < 80; i++) {
            check(budget.allows(grim), "action " + i + " allowed");
            budget.note();
        }
        check(!budget.allows(grim), "81st action in the same second is refused");
        check(budget.allows(off), "no limit without profile");
        now[0] = 6000L;
        check(budget.allows(grim), "next second starts fresh");
        budget.note();
        budget.reset();
        now[0] = 6100L;
        for (int i = 0; i < 80; i++) {
            budget.note();
        }
        check(!budget.allows(grim), "limit again after reset");
    }

    static void score() {
        CrystalScore.Config c = new CrystalScore.Config();
        float none = Float.NEGATIVE_INFINITY;

        near(CrystalScore.minimumWorthwhile(c, 20), 4.0, "min damage on a healthy target");
        near(CrystalScore.minimumWorthwhile(c, 10), 0.0, "face place at 5 hearts");
        near(CrystalScore.minimumWorthwhile(c, 3), 0.0, "face place on a nearly dead target");
        c.facePlace = false;
        near(CrystalScore.minimumWorthwhile(c, 10), 4.0, "face place off");
        near(CrystalScore.minimumWorthwhile(c, 3), 3.0, "never more than the target's health");
        c.facePlace = true;

        near(CrystalScore.bound(c, 3.0F, 20.0F), none, "hopeless spot");
        near(CrystalScore.bound(c, 10.0F, 20.0F), 10.0, "normal spot");
        near(CrystalScore.bound(c, 25.0F, 20.0F), 45.0, "spot that may kill gets the bonus");

        check(CrystalScore.selfBlocked(c, 10, 10, false, false), "would kill you");
        check(!CrystalScore.selfBlocked(c, 10, 10, true, false), "creative cannot die");
        check(CrystalScore.selfBlocked(c, 1, 10, true, true), "endangered always blocks");
        c.antiSelfPop = 0.0F;
        check(!CrystalScore.selfBlocked(c, 10, 10, false, false), "anti self pop off");
        c.antiSelfPop = 2.0F;
        check(CrystalScore.selfBlocked(c, 5, 10, false, false), "two crystals would kill you");
        c.antiSelfPop = 1.0F;
        check(CrystalScore.friendBlocked(c, 10, 10), "would kill a friend");
        check(!CrystalScore.friendBlocked(c, 10, -1), "no friend near");
        check(!CrystalScore.friendBlocked(c, 4, 10), "friend survives");

        near(CrystalScore.rank(c, false, 2, 0, 12, 20, false, none), 12.0, "plain placement ranks by target damage");
        near(CrystalScore.rank(c, false, 10, 0, 12, 20, false, none), 12.0 * (1.2 / 1.4), "bad ratio lowers the rank");
        near(CrystalScore.rank(c, false, 13, 0, 12, 20, false, none), none, "too much self damage");
        near(CrystalScore.rank(c, false, 13, 0, 12, 12, true, none), 32.0, "force pop ignores the limits and adds the bonus");
        near(CrystalScore.rank(c, false, 2, 0, 12, 12, true, none), 32.0, "a kill adds the bonus");
        near(CrystalScore.rank(c, false, 2, 9, 12, 20, false, none), 12.0 * (12.0 / 9.0 / 2.0), "friend ratio lowers the rank (9 is over the limit 8 but below the enemy damage)");
        near(CrystalScore.rank(c, false, 2, 13, 12, 20, false, none), none, "friend takes more than the enemy and more than the limit");
        near(CrystalScore.rank(c, false, 2, 0, 3, 20, false, none), none, "below the minimum");
        near(CrystalScore.rank(c, false, 2, 0, 3, 8, false, none), 3.0, "face place target is worth any damage");
        near(CrystalScore.rank(c, false, 2, 0, 3, 3, true, none), 23.0, "a kill is worth it below the minimum");
        near(CrystalScore.rank(c, false, 2, 0, 12, 20, false, 12.0F), none, "must beat the rank to beat");
        near(CrystalScore.rank(c, false, 2, 0, 12, 20, false, 11.9F), 12.0, "beating it by a little");
        near(CrystalScore.rank(c, false, 0, 0, none, 0, false, none), none, "no enemy");
        near(CrystalScore.rank(c, true, 8.5F, 0, 12, 20, false, none), 12.0, "breaking uses the explode limit (9)");
        near(CrystalScore.rank(c, true, 9.5F, 0, 9, 20, false, none), none, "breaking: over the explode limit (9) and not less than the gain");
        near(CrystalScore.rank(c, true, 9.5F, 0, 12, 20, false, none), 12.0, "breaking: over the limit is fine when the target takes more");
        near(CrystalScore.rank(c, true, 1, 0, 2, 20, false, none), none, "breaking: below min explode 2.5");
        near(CrystalScore.rank(c, true, 1, 0, 3, 20, false, none), 3.0, "breaking: above min explode");
        near(CrystalScore.rank(c, true, 5, 0, 5, 20, false, none), 5.0 * (1.0 / 1.1), "breaking: ratio 1.0 < 1.1");

        c.safeMode = true;
        near(CrystalScore.rank(c, false, 0, 0, 12, 20, false, none), 1200.0, "safe mode with no self damage");
        near(CrystalScore.rank(c, false, 4, 0, 12, 20, false, none), 3.0, "safe mode ranks by the ratio");

        c.safeMode = false;
        near(CrystalScore.rank(c, false, 2, 20, false, false, 0, -1, 12, 20, false, none), 12.0, "combined call");
        near(CrystalScore.rank(c, false, 25, 20, false, false, 0, -1, 12, 20, false, none), none, "combined call, self blocked");
        near(CrystalScore.rank(c, false, 2, 20, false, false, 9, 8, 12, 20, false, none), none, "combined call, friend blocked");
    }
}
