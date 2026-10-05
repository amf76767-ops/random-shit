package dev.dihclient.port.trident;

/** Ported from an open-source client (GPL-3.0). */
public final class TridentLogic {

    public static final int VANILLA_CHARGE = 10;

    public static final int MIN_TICKS = 9;

    public enum Step { NONE, START_USE, RELEASE }

    private TridentLogic() {
    }

    public static int scaledCharge(int vanilla, double scale) {
        return Math.max(1, (int) Math.round(vanilla * scale));
    }

    public static int releaseTicks(int scaledChargeTicks) {
        return Math.max(MIN_TICKS, scaledChargeTicks);
    }

    public static double boosted(double component, double multiplier) {
        return component * multiplier;
    }

    public static boolean cooledDown(int age, int releasedTick) {
        return age - releasedTick >= MIN_TICKS;
    }

    public static Step step(boolean usingItem, boolean usingRiptide, int useTicks, int releaseTicks, int age, int releasedTick) {
        if (!usingItem) {
            return cooledDown(age, releasedTick) ? Step.START_USE : Step.NONE;
        }
        if (usingRiptide && useTicks >= releaseTicks && cooledDown(age, releasedTick)) {
            return Step.RELEASE;
        }
        return Step.NONE;
    }
}
