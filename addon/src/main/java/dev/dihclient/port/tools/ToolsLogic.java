package dev.dihclient.port.tools;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Pure decisions of Spear Swap and Hover Totem, kept free of game classes so they can be unit tested.
 */
public final class ToolsLogic {
    private ToolsLogic() {
    }

    /**
     * Picks the spear to lunge with: the highest Lunge level wins, on a tie the one already in hand.
     *
     * @param levels Lunge level per inventory slot, 0 (or less) for "not a usable lunge spear"
     * @param selected the selected hotbar slot
     * @return the slot, or -1 when nothing qualifies
     */
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

    /** Ticks until the swap back to the old slot: at least one, the jitter spreads it out. */
    public static int returnDelay(int backDelay, int spread) {
        return Math.max(1, backDelay + spread);
    }

    /**
     * What to click for the totem under the mouse: 40 = swap into the offhand, 0-8 = swap into that hotbar slot, -1 = nothing.
     *
     * @param offhandHasTotem the offhand already holds a totem
     * @param hotbarRefill the setting "Hotbar Refill"
     * @param hotbarSetting the setting "Hotbar Slot" (1-9)
     * @param hotbarHasTotem that hotbar slot already holds a totem
     */
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
