package dev.dihclient;

import dev.dihclient.port.donutd.DonutDLogic;
import java.util.HashSet;
import java.util.Set;

public final class DonutDTests {
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
        scatter();
        light();
        glowEdge();
        spawnerText();
        spawnerColors();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void scatter() {
        boolean inRange = true;
        Set<Integer> seen = new HashSet<>();
        for (int x = -20; x < 20; x++) {
            for (int z = -20; z < 20; z++) {
                int s = DonutDLogic.scatter(x, -30, z);
                inRange &= s >= 0 && s < 100;
                seen.add(s);
            }
        }
        check(inRange, "scatter stays in 0-99");
        check(seen.size() > 90, "scatter spreads over the range");
        check(DonutDLogic.scatter(5, -40, 9) == DonutDLogic.scatter(5, -40, 9), "scatter is stable");
        check(DonutDLogic.scatter(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE) >= 0, "scatter never negative");
    }

    static void light() {
        check(DonutDLogic.isBudLight(1) && DonutDLogic.isBudLight(2) && DonutDLogic.isBudLight(4) && DonutDLogic.isBudLight(5), "bud lights");
        check(!DonutDLogic.isBudLight(0) && !DonutDLogic.isBudLight(3) && !DonutDLogic.isBudLight(6) && !DonutDLogic.isBudLight(15), "not bud lights");
        check(DonutDLogic.isLocalMaximum(4, new int[]{3, 0, 2, 0, 0, 1}), "peak: all neighbours darker");
        check(!DonutDLogic.isLocalMaximum(4, new int[]{3, 0, 4, 0, 0, 1}), "equal neighbour is no peak");
        check(!DonutDLogic.isLocalMaximum(2, new int[]{0, 0, 0, 0, 0, 5}), "brighter neighbour is no peak");
        check(DonutDLogic.glowSlot(-1, -1, 0) == 0 && DonutDLogic.glowSlot(1, 1, 7) == 71, "glow slots span 0-71");
        Set<Integer> slots = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int s = 0; s < 8; s++) {
                    slots.add(DonutDLogic.glowSlot(dx, dz, s));
                }
            }
        }
        check(slots.size() == 72, "glow slots are unique");
        check(DonutDLogic.inChunkRange(3, -2, 0, 0, 3) && !DonutDLogic.inChunkRange(4, 0, 0, 0, 3) && !DonutDLogic.inChunkRange(0, -4, 0, 0, 3), "chunk range is square");
    }

    static void glowEdge() {
        check(DonutDLogic.isGlowEdge(new int[]{0, 0, 4, 0, 0, 0}, side -> true), "one lit open side");
        check(!DonutDLogic.isGlowEdge(new int[]{0, 0, 4, 0, 0, 0}, side -> false), "lit side is solid");
        check(!DonutDLogic.isGlowEdge(new int[]{0, 0, 0, 0, 0, 0}, side -> true), "no light around");
        check(!DonutDLogic.isGlowEdge(new int[]{0, 5, 4, 0, 0, 0}, side -> true), "brighter than 4 around");
        check(!DonutDLogic.isGlowEdge(new int[]{3, 3, 3, 3, 3, 3}, side -> true), "dim only");
        check(DonutDLogic.isGlowEdge(new int[]{4, 2, 4, 0, 0, 0}, side -> side == 2), "second lit side is open");
        int[] asked = {0};
        DonutDLogic.isGlowEdge(new int[]{0, 0, 0, 0, 0, 0}, side -> {
            asked[0]++;
            return true;
        });
        check(asked[0] == 0, "block lookup only for lit sides");
    }

    static void spawnerText() {
        check(DonutDLogic.title(null).equals("Spawner") && DonutDLogic.title("").equals("Spawner"), "unknown mob");
        check(DonutDLogic.title("Zombie").equals("Zombie Spawner"), "mob title");
        check(DonutDLogic.detail(12.4, 16, true).equals("12m  ·  16m range"), "detail with range");
        check(DonutDLogic.detail(12.6, 16, false).equals("13m"), "detail without range");
        check(DonutDLogic.detail(30, -1, true).equals("30m") && DonutDLogic.detail(30, 0, true).equals("30m"), "no range known");
        check(DonutDLogic.textScale(0) == 1.0F && DonutDLogic.textScale(40) == 4.0F && DonutDLogic.textScale(1000) == 8.0F, "text scale clamps");
        check(DonutDLogic.isInside(100, 16) && !DonutDLogic.isInside(256, 16) && DonutDLogic.isInside(1e9, -1), "inside the activation range");
    }

    static void spawnerColors() {
        check(DonutDLogic.fade(0, 64) == 1.0F && DonutDLogic.fade(64, 64) == 0.0F && DonutDLogic.fade(100, 64) == 0.0F, "fade ends");
        check(DonutDLogic.fade(60, 64) == 0.5F, "fade over the outer eighth");
        check(DonutDLogic.ringAlpha(true, 1.0F) == 204 && DonutDLogic.ringAlpha(false, 1.0F) == 102 && DonutDLogic.ringAlpha(true, 0.0F) == 0, "ring alpha");
        check(DonutDLogic.withAlpha(0x112233, 0x80) == 0x80112233, "alpha packed");
        check(DonutDLogic.withAlpha(0xFF112233, 300) == 0xFF112233 && DonutDLogic.withAlpha(0x112233, -5) == 0x00112233, "alpha clamped, rgb masked");
        check(Math.abs(DonutDLogic.ringX(0, 64, 16) - 16) < 1e-9 && Math.abs(DonutDLogic.ringZ(16, 64, 16) - 16) < 1e-9, "ring points");
        check(Math.abs(DonutDLogic.ringX(64, 64, 16) - DonutDLogic.ringX(0, 64, 16)) < 1e-9, "ring closes");
    }
}
