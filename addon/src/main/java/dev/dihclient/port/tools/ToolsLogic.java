package dev.dihclient.port.tools;

/** Ported from an open-source client (GPL-3.0). */
public final class ToolsLogic {
    private ToolsLogic() {
    }

    public static int pickSpear(int[] levels, int selected) {
        int best = -1;
        int bestLevel = 0;
        for (int slot = 0; slot < levels.length; slot++) {
            int level = levels[slot];
            if (level > bestLevel || level > 0 && level == bestLevel && slot == selected) {
                best = slot;
                bestLevel = level;
            }
        }
        return best;
    }

    public static int returnDelay(int backDelay, int spread) {
        return Math.max(1, backDelay + spread);
    }

    public static int hoverSwapButton(boolean offhandHasTotem, boolean hotbarRefill, int hotbarSetting, boolean hotbarHasTotem) {
        if (!offhandHasTotem) {
            return 40;
        }
        if (!hotbarRefill) {
            return -1;
        }
        int slot = Math.max(1, Math.min(9, hotbarSetting)) - 1;
        return hotbarHasTotem ? -1 : slot;
    }
}
