package dev.dihclient.port.chunks;

import java.util.Arrays;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Sky-light status of every section of one chunk, as the server last sent it.
 */
public final class ChunkLightSnapshot {
    /** Sections whose top block is below this height count for the base signal. */
    public static final int BASE_DETECT_CEILING_Y = 62;

    public final int sectionCount;
    public final int bottomSectionY;
    /** One entry per light section: the world sections plus one below and one above. */
    public final LightSectionStatus[] sky;

    public ChunkLightSnapshot(int sectionCount, int bottomSectionY) {
        this.sectionCount = sectionCount;
        this.bottomSectionY = bottomSectionY;
        this.sky = new LightSectionStatus[sectionCount + 2];
        Arrays.fill(this.sky, LightSectionStatus.UNKNOWN);
    }

    public boolean hasBaseSignal() {
        for (int i = 1; i < this.sectionCount + 1; i++) {
            int top = (this.bottomSectionY + i - 1) * 16 + 15;
            if (top < BASE_DETECT_CEILING_Y && this.sky[i] == LightSectionStatus.ZEROED_PRESENT) {
                return true;
            }
        }
        return false;
    }
}
