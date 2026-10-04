package dev.dihclient.port.spotify;

/** Ported from an open-source client (GPL-3.0). How the lyric lines move when the song goes on to the next line. */
public enum LyricMotion {
    /** The lines slide up one after the other. */
    WAVE,
    /** The lines slide up together. */
    GLIDE,
    /** No sliding, the old lines fade out and the new ones in. */
    FADE
}
