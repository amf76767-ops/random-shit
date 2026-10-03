package dev.dihclient.port.chunks;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * What the server sent for one sky-light section of a chunk.
 */
public enum LightSectionStatus {
    NORMAL,
    EMPTY_MASK,
    /** A full light array that is all zero: dark where the sky should reach, the sign of a covered, player-made space. */
    ZEROED_PRESENT,
    UNIFORM_MAX,
    UNKNOWN
}
