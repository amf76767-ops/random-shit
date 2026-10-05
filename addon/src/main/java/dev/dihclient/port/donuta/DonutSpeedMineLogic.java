package dev.dihclient.port.donuta;

/** Ported from an open-source client (GPL-3.0). */
public final class DonutSpeedMineLogic {
    private DonutSpeedMineLogic() {
    }

    public static float scaleProgress(float progress, double breakAt) {
        return breakAt > 0.0 ? (float) (progress / breakAt) : progress;
    }

    public static boolean dropsDelay(boolean skipDelay, boolean keepInstantDelay, float progress) {
        return skipDelay && (!keepInstantDelay || progress < 1.0F);
    }
}
