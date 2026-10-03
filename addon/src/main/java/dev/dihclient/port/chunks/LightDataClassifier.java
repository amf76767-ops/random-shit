package dev.dihclient.port.chunks;

import java.util.BitSet;
import java.util.List;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Sorts the sky-light arrays of a chunk / light packet into {@link LightSectionStatus}. No game classes here, so it can be tested.
 */
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

    /**
     * @param mask      sections the packet carries data for (bit i = light section i)
     * @param emptyMask sections the packet says are empty
     * @param updates   the data arrays, in the order of the set bits of {@code mask}
     * @param out       status per light section, only the touched entries change
     */
    public static void applyTrack(BitSet mask, BitSet emptyMask, List<byte[]> updates, LightSectionStatus[] out) {
        int next = 0;
        for (int i = 0; i < out.length; i++) {
            if (mask.get(i)) {
                byte[] payload = next < updates.size() ? updates.get(next) : null;
                next++;
                out[i] = classifyPayload(payload);
            } else if (emptyMask.get(i)) {
                out[i] = LightSectionStatus.EMPTY_MASK;
            }
        }
    }
}
