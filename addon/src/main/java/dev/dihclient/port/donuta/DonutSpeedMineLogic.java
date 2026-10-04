package dev.dihclient.port.donuta;

/**
 * Ported from an open-source client (GPL-3.0).
 * The two numbers of Donut Speed Mine: how the break progress is scaled and when the delay between two blocks is dropped.
 */
public final class DonutSpeedMineLogic {
    private DonutSpeedMineLogic() {
    }

    /** The game breaks the block at 1.0; dividing by "break at" makes that happen at that share of the real time. */
    public static float scaleProgress(float progress, double breakAt) {
        return breakAt > 0.0 ? (float) (progress / breakAt) : progress;
    }

    /**
     * @param progress unscaled break progress per tick of the block (1.0 or more = instant break in vanilla)
     * @return true when the cooldown between two blocks is to be zeroed
     */
    public static boolean dropsDelay(boolean skipDelay, boolean keepInstantDelay, float progress) {
        return skipDelay && (!keepInstantDelay || progress < 1.0F);
    }
}
