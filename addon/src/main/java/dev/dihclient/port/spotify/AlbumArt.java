package dev.dihclient.port.spotify;

import java.nio.ByteBuffer;

/** Ported from an open-source client (GPL-3.0). */
public final class AlbumArt {
    private final int size;
    private final byte[] rgba;

    private AlbumArt(int size, byte[] rgba) {
        this.size = size;
        this.rgba = rgba;
    }

    public static AlbumArt crop(ByteBuffer pixels, int width, int height, int maxSide) {
        int side = Math.min(width, height);
        int left = (width - side) / 2;
        int top = (height - side) / 2;
        int size = Math.min(side, maxSide);
        byte[] out = new byte[size * size * 4];
        int o = 0;
        for (int oy = 0; oy < size; oy++) {
            int y0 = top + (int) ((long) oy * side / size);
            int y1 = Math.max(y0 + 1, top + (int) ((long) (oy + 1) * side / size));
            for (int ox = 0; ox < size; ox++) {
                int x0 = left + (int) ((long) ox * side / size);
                int x1 = Math.max(x0 + 1, left + (int) ((long) (ox + 1) * side / size));
                long r = 0;
                long g = 0;
                long b = 0;
                long a = 0;
                for (int y = y0; y < y1; y++) {
                    int row = (y * width + x0) * 4;
                    for (int x = x0; x < x1; x++, row += 4) {
                        r += pixels.get(row) & 255;
                        g += pixels.get(row + 1) & 255;
                        b += pixels.get(row + 2) & 255;
                        a += pixels.get(row + 3) & 255;
                    }
                }
                long count = (long) (y1 - y0) * (x1 - x0);
                out[o++] = (byte) ((r + count / 2) / count);
                out[o++] = (byte) ((g + count / 2) / count);
                out[o++] = (byte) ((b + count / 2) / count);
                out[o++] = (byte) ((a + count / 2) / count);
            }
        }
        return new AlbumArt(size, out);
    }

    public int size() {
        return this.size;
    }

    public int byteSize() {
        return this.rgba.length;
    }

    public int argb(int x, int y) {
        int i = (y * this.size + x) * 4;
        return (this.rgba[i + 3] & 255) << 24 | (this.rgba[i] & 255) << 16 | (this.rgba[i + 1] & 255) << 8 | this.rgba[i + 2] & 255;
    }
}
