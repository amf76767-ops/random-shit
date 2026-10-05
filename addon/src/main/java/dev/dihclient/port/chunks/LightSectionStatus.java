package dev.dihclient.port.chunks;

/** Ported from an open-source client (GPL-3.0). */
public enum LightSectionStatus {
    NORMAL,
    EMPTY_MASK,

    ZEROED_PRESENT,
    UNIFORM_MAX,
    UNKNOWN
}
