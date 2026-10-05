package dev.dihclient.port.chunks;

import java.util.Arrays;

/** Ported from an open-source client (GPL-3.0). */
public final class ChunkLightSnapshot {

    public static final int BASE_DETECT_CEILING_Y = 62;

    public final int sectionCount;
    public final int bottomSectionY;

    public final LightSectionStatus[] sky;

    public ChunkLightSnapshot(int sectionCount, int bottomSectionY) {
        this.sectionCount = sectionCount;
        this.bottomSectionY = bottomSectionY;
        this.sky = new LightSectionStatus[sectionCount + 2];
        Arrays.fill(this.sky, LightSectionStatus.UNKNOWN);
    }

    public int signalSections() {
        int i = 1;
        while (i < this.sectionCount + 1 && (this.bottomSectionY + i - 1) * 16 + 15 < BASE_DETECT_CEILING_Y) {
            i++;
        }
        return i;
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
