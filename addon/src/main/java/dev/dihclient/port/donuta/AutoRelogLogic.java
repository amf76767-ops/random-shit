package dev.dihclient.port.donuta;

/**
 * Ported from an open-source client (GPL-3.0).
 * The trigger of Auto Relog, free of game classes so it can be tested: fires once when the player goes below the line after
 * having been clearly above it for two seconds. Walking up and down at the line does not fire it again until the player
 * has been {@link #REARM_HEIGHT} blocks above for {@link #REARM_TICKS} ticks.
 */
public final class AutoRelogLogic {
    public static final int REARM_HEIGHT = 4;
    public static final int REARM_TICKS = 40;

    private int aboveTicks;

    public void reset() {
        this.aboveTicks = 0;
    }

    public int aboveTicks() {
        return this.aboveTicks;
    }

    /** @return true on the tick the player went below {@code line} while armed */
    public boolean tick(double y, double line) {
        if (y >= line + REARM_HEIGHT) {
            if (this.aboveTicks < REARM_TICKS) {
                this.aboveTicks++;
            }
        } else if (y < line) {
            boolean armed = this.aboveTicks >= REARM_TICKS;
            this.aboveTicks = 0;
            return armed;
        }
        return false;
    }
}
