package dev.dihclient.port.donuta;

/** Ported from an open-source client (GPL-3.0). */
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
