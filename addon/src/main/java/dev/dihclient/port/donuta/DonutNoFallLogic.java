package dev.dihclient.port.donuta;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * State machine of Donut NoFall, free of game classes so it can be tested. After a fall of more than 3 blocks the module
 * reports "in the air" to the server for the next four ground packets, so the landing is never seen as a landing.
 */
public final class DonutNoFallLogic {
    /** Fall distance (of the previous tick, the game zeroes it on landing) that arms the module. */
    public static final double TRIGGER_FALL_DISTANCE = 3.0;
    /** Ground packets that are hidden before the real flag is sent again. */
    public static final int GROUND_PACKETS_TO_RESET = 4;

    /** What to do with one outgoing movement packet. */
    public static final class Send {
        /** Move the player up by 1e-8 first (every second hidden ground packet). */
        public boolean nudge;
        /** The on-ground flag the replacement packet carries. */
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

    /**
     * One client tick.
     *
     * @return true when the player has to be put "in the air" on the client (the game would set the ground flag again every tick)
     */
    public boolean tick(double fallDistance) {
        boolean forceAir = this.armed;
        if (this.lastFallDistance > TRIGGER_FALL_DISTANCE) {
            this.armed = true;
        }
        this.lastFallDistance = fallDistance;
        return forceAir;
    }

    /**
     * One outgoing movement packet.
     *
     * @param packetOnGround ground flag of the packet the game wanted to send
     * @param playerOnGround ground flag of the player entity
     */
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
