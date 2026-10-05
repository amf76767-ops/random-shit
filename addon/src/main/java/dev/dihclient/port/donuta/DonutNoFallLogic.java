package dev.dihclient.port.donuta;

/** Ported from an open-source client (GPL-3.0). */
public final class DonutNoFallLogic {

    public static final double TRIGGER_FALL_DISTANCE = 3.0;

    public static final int GROUND_PACKETS_TO_RESET = 4;

    public static final class Send {

        public boolean nudge;

        public boolean onGround;
    }

    private boolean armed;
    private double lastFallDistance;
    private int groundTicks;

    public void reset() {
        this.armed = false;
        this.lastFallDistance = 0.0;
        this.groundTicks = 0;
    }

    public boolean armed() {
        return this.armed;
    }

    public int groundTicks() {
        return this.groundTicks;
    }

    public boolean tick(double fallDistance) {
        boolean forceAir = this.armed;
        if (this.lastFallDistance > TRIGGER_FALL_DISTANCE) {
            this.armed = true;
        }
        this.lastFallDistance = fallDistance;
        return forceAir;
    }

    public Send packet(boolean packetOnGround, boolean playerOnGround) {
        if (this.armed && packetOnGround) {
            this.groundTicks++;
        }
        Send out = new Send();
        out.nudge = this.groundTicks % 2 == 1;
        out.onGround = !this.armed && playerOnGround;
        if (this.groundTicks == GROUND_PACKETS_TO_RESET) {
            this.armed = false;
            this.groundTicks = 0;
        }
        return out;
    }
}
