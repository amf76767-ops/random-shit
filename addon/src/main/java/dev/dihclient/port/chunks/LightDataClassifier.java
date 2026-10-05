package dev.dihclient.port.chunks;

import java.util.BitSet;
import java.util.List;

/** Ported from an open-source client (GPL-3.0). */
public final class LightDataClassifier {
    private static final int NIBBLES_PER_SECTION = 2048;

    private LightDataClassifier() {
    }

    public static LightSectionStatus classifyPayload(byte[] payload) {
        if (payload == null || payload.length != NIBBLES_PER_SECTION) {
            return LightSectionStatus.UNKNOWN;
        }
        boolean allZero = true;
        boolean allMax = true;
        for (byte b : payload) {
            if (b != 0) {
                allZero = false;
            }
            if (b != -1) {
                allMax = false;
            }
            if (!allZero && !allMax) {
                return LightSectionStatus.NORMAL;
            }
        }
        if (allZero) {
            return LightSectionStatus.ZEROED_PRESENT;
        }
        return allMax ? LightSectionStatus.UNIFORM_MAX : LightSectionStatus.NORMAL;
    }

    public static void applyTrack(BitSet mask, BitSet emptyMask, List<byte[]> updates, LightSectionStatus[] out) {
        applyTrack(mask, emptyMask, updates, out, out.length);
    }

    public static void applyTrack(BitSet mask, BitSet emptyMask, List<byte[]> updates, LightSectionStatus[] out, int classifyBelow) {
        int next = 0;
        for (int i = 0; i < out.length; i++) {
            if (mask.get(i)) {
                byte[] payload = next < updates.size() ? updates.get(next) : null;
                next++;
                out[i] = i < classifyBelow ? classifyPayload(payload) : LightSectionStatus.NORMAL;
            } else if (emptyMask.get(i)) {
                out[i] = LightSectionStatus.EMPTY_MASK;
            }
        }
    }
}
