package dev.dihclient.port.noinvleak;

/**
 * Ported from an open-source client (GPL-3.0).
 * <p>
 * The stack count and the durability bar of an item slot, drawn with plain rectangles (a 5x7 digit font) so the overlay does not
 * need the game's font renderer. Pure Java, tested headless.
 */
public final class DigitFont {
    private static final byte[][] DIGITS = {
        {14, 17, 19, 21, 25, 17, 14},
        {4, 12, 4, 4, 4, 4, 14},
        {14, 17, 1, 6, 8, 16, 31},
        {14, 17, 1, 6, 1, 17, 14},
        {2, 6, 10, 18, 31, 2, 2},
        {31, 16, 30, 1, 1, 17, 14},
        {14, 16, 30, 17, 17, 17, 14},
        {31, 1, 2, 4, 8, 8, 8},
        {14, 17, 17, 14, 17, 17, 14},
        {14, 17, 17, 15, 1, 17, 14}
    };
    private static final int SHADOW = 0xFF3F3F3F;
    private static final int WHITE = -1;
    private static final int BLACK = 0xFF000000;

    private DigitFont() {
    }

    /** Receives the rectangles; coordinates are in target pixels, colour is straight ARGB. */
    public interface Sink {
        void rect(int x, int y, int w, int h, int argb);
    }

    /**
     * Slot decorations at the slot's top-left corner (ox, oy): the durability bar when {@code barVisible}, then the count when
     * it is not 1. {@code barStep} is 0..13 (vanilla's bar width), {@code barRgb} its colour.
     */
    public static void decorations(Sink sink, boolean barVisible, int barStep, int barRgb, int count, int ox, int oy, int scale) {
        if (barVisible) {
            sink.rect(ox + 2 * scale, oy + 13 * scale, 13 * scale, 2 * scale, BLACK);
            sink.rect(ox + 2 * scale, oy + 13 * scale, barStep * scale, scale, BLACK | barRgb);
        }
        if (count != 1) {
            count(sink, count, ox, oy, scale);
        }
    }

    static void count(Sink sink, int count, int ox, int oy, int scale) {
        String text = Integer.toString(count);
        int x = ox + (17 - 6 * text.length()) * scale;
        int y = oy + 9 * scale;
        for (int i = 0; i < text.length(); i++) {
            int digit = text.charAt(i) - '0';
            if (digit >= 0 && digit <= 9) {
                int gx = x + i * 6 * scale;
                glyph(sink, DIGITS[digit], gx + scale, y + scale, scale, SHADOW);
                glyph(sink, DIGITS[digit], gx, y, scale, WHITE);
            }
        }
    }

    /** Horizontal runs of set bits, one rectangle per run. */
    private static void glyph(Sink sink, byte[] rows, int x, int y, int scale, int argb) {
        for (int r = 0; r < rows.length; r++) {
            int c = 0;
            while (c < 5) {
                if ((rows[r] >> 4 - c & 1) == 0) {
                    c++;
                } else {
                    int start = c;
                    while (c < 5 && (rows[r] >> 4 - c & 1) != 0) {
                        c++;
                    }
                    sink.rect(x + start * scale, y + r * scale, (c - start) * scale, scale, argb);
                }
            }
        }
    }
}
