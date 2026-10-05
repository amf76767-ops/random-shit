package dev.dihclient.port.spotify;

/** Ported from an open-source client (GPL-3.0). */
public final class GuidBytes {
    private GuidBytes() {
    }

    public static byte[] of(String text) {
        String hex = text.replace("-", "").replace("{", "").replace("}", "");
        if (hex.length() != 32) {
            throw new IllegalArgumentException("not a GUID: " + text);
        }
        byte[] plain = new byte[16];
        for (int i = 0; i < 16; i++) {
            plain[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        byte[] out = new byte[16];

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
