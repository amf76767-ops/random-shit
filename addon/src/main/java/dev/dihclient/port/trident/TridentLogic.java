package dev.dihclient.port.trident;

/**
 * Ported from an open-source client (GPL-3.0).
 * Pure decisions of Riptide, Trident Util and Trident Boost, kept free of game classes so they can be unit tested.
 */
public final class TridentLogic {
    /** Vanilla: a trident has to be charged this many ticks before the release does anything. */
    public static final int VANILLA_CHARGE = 10;
    /** Fewest ticks Riptide waits between two uses and before a release (one below vanilla, like the original module). */
    public static final int MIN_TICKS = 9;

    public enum Step { NONE, START_USE, RELEASE }

    private TridentLogic() {
    }

    /** Charge time the trident code asks for after "Charge Scale": at least one tick. */
    public static int scaledCharge(int vanilla, double scale) {
        return Math.max(1, (int) Math.round(vanilla * scale));
    }

    /** Ticks Riptide charges before it lets go: never below {@link #MIN_TICKS}, even with a tiny charge scale. */
    public static int releaseTicks(int scaledChargeTicks) {
        return Math.max(MIN_TICKS, scaledChargeTicks);
    }

    /** Scales one component of the riptide launch. */
    public static double boosted(double component, double multiplier) {
        return component * multiplier;
    }

    /** True once enough ticks have passed since the last release. */
    public static boolean cooledDown(int age, int releasedTick) {
        return age - releasedTick >= MIN_TICKS;
    }

    /**
     * What Riptide does this tick while right-click is held.
     *
     * @param usingItem the player is using an item right now
     * @param usingRiptide the item in use is a riptide trident
     * @param useTicks how long the item has been in use
     * @param releaseTicks {@link #releaseTicks}
     */
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
