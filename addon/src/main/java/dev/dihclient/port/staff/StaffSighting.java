package dev.dihclient.port.staff;

/** Ported from an open-source client (GPL-3.0). */
public final class StaffSighting {
    public static final long MARK_MS = 600000L;
    public static final long COOLDOWN_MS = 120000L;
    public static final long HIGHLIGHT_MS = 4000L;
    public static final long NEARBY_GRACE_MS = 10000L;

    public long provenAt;
    public long alertedAt;
    public long highlightUntil;
    public boolean nearby;
    public float distance;
    public long bodyLostAt;
    public int bodyTick;
    public int switchAlertTick = -1;
    public boolean switchedToSpectator;
    public long untrackedSince;

    public boolean marked(long now) {
        return this.nearby || this.provenAt != 0L && now - this.provenAt < MARK_MS;
    }

    public boolean coolingDown(long now) {
        return this.alertedAt != 0L && now - this.alertedAt < COOLDOWN_MS;
    }

    public float alertLevel(long now) {
        return Math.max(0.0F, Math.min(1.0F, (float) (this.highlightUntil - now) / (float) HIGHLIGHT_MS));
    }
}
