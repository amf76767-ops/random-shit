package dev.dihclient.port.staff;

import java.util.Comparator;
import java.util.UUID;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * One tracked staff member of the list, plus the ordering of the list. No Minecraft classes.
 */
public final class StaffEntry {
    public enum Presence { ONLINE, SPECTATOR, VANISHED }

    public final UUID id;
    public String name = "";
    public String rank = "";
    public int seniority;
    public Presence presence = Presence.ONLINE;
    /** Scans in a row in which the player was missing from the tab list but their body was still around. */
    public int offTabScans;

    public StaffEntry(UUID id) {
        this.id = id;
    }

    public void update(String name, StaffMatcher.Match rank, Presence presence) {
        this.name = name;
        this.rank = rank.rank();
        this.seniority = rank.seniority();
        this.presence = presence;
    }

    /** 0 = standing next to you, 1 = proven in your region, 2 = hidden/spectating, 3 = plain online somewhere. */
    public static int group(StaffEntry s, StaffSighting seen, long now) {
        if (seen != null && seen.nearby) {
            return 0;
        } else if (seen != null && seen.marked(now)) {
            return 1;
        } else {
            return s.presence != Presence.ONLINE ? 2 : 3;
        }
    }

    /** Most relevant first, then the more senior rank, then by name. */
    public static Comparator<StaffEntry> order(java.util.function.Function<UUID, StaffSighting> sightings, long now) {
        return Comparator.<StaffEntry>comparingInt(s -> group(s, sightings.apply(s.id), now))
                .thenComparingInt(s -> s.seniority)
                .thenComparing(s -> s.name, String.CASE_INSENSITIVE_ORDER);
    }
}
