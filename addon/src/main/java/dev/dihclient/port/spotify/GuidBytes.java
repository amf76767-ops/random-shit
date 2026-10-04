package dev.dihclient.port.spotify;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Memory layout of a Windows GUID: the first three groups little-endian, the last two as written. Kept apart from the
 * JNA code so it can be tested without native libraries (Anubis used jna-platform's GUID class, which we do not rely on).
 */
final class GuidBytes {
    private GuidBytes() {
    }

    static byte[] of(String text) {
        String hex = text.replace("-", "").replace("{", "").replace("}", "");
        if (hex.length() != 32) {
            throw new IllegalArgumentException("not a GUID: " + text);
        }
        byte[] plain = new byte[16];
        for (int i = 0; i < 16; i++) {
            plain[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        byte[] out = new byte[16];
        // Data1 (4 bytes), Data2 (2), Data3 (2) are stored little-endian
        for (int i = 0; i < 4; i++) {
            out[i] = plain[3 - i];
        }
        out[4] = plain[5];
        out[5] = plain[4];
        out[6] = plain[7];
        out[7] = plain[6];
        System.arraycopy(plain, 8, out, 8, 8);
        return out;
    }
}
