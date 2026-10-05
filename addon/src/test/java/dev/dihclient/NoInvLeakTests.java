package dev.dihclient;

import dev.dihclient.port.noinvleak.Canvas;
import dev.dihclient.port.noinvleak.DigitFont;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public final class NoInvLeakTests {
    static int failed;
    static int passed;

    static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + what);
        }
    }

    static Canvas canvas(int[] px, int stride, int w, int h) {
        Canvas c = new Canvas();
        c.wrap(IntBuffer.wrap(px), stride, w, h);
        return c;
    }

    static void premultiplyAndOver() {
        check(Canvas.premultiply(0x80FF8000, 0x80) == 0x80804000 || Canvas.premultiply(0x80FF8000, 0x80) == 0x80804000, "premultiply");
        check(Canvas.premultiply(0xFFFFFFFF, 255) == 0xFFFFFFFF, "premultiply opaque");
        check(Canvas.over(0xFF112233, 0xFFAAAAAA) == 0xFF112233, "opaque source wins");
        check(Canvas.over(0x00000000, 0xFFAAAAAA) == 0xFFAAAAAA, "transparent source keeps dst");

        int r = Canvas.over(0x80000000, 0xFFFFFFFF);
        check(r >>> 24 == 255, "alpha stays opaque: " + Integer.toHexString(r));
        check((r >>> 16 & 0xFF) == 127 && (r >>> 8 & 0xFF) == 127 && (r & 0xFF) == 127, "half black over white is grey: " + Integer.toHexString(r));
    }

    static void rectClipsAndUsesStride() {
        int stride = 6;
        int[] px = new int[stride * 4];
        Canvas c = canvas(px, stride, 5, 4);
        c.rect(-2, -2, 4, 4, 0xFFFF0000);
        c.rect(4, 3, 10, 10, 0xFF00FF00);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < stride; x++) {
                int v = px[y * stride + x];
                boolean red = x < 2 && y < 2;
                boolean green = x == 4 && y == 3;
                int want = red ? 0xFFFF0000 : green ? 0xFF00FF00 : 0;
                check(v == want, "rect pixel " + x + "," + y + " = " + Integer.toHexString(v));
            }
        }
        c.rect(0, 0, 0, 3, 0xFFFFFFFF);
        c.rect(0, 0, 3, 3, 0x00FFFFFF);
        check(px[0] == 0xFFFF0000, "empty and fully transparent rects draw nothing");
    }

    static void rectBlendsTranslucent() {
        int[] px = {0xFFFFFFFF};
        canvas(px, 1, 1, 1).rect(0, 0, 1, 1, 0x80000000);
        check((px[0] >>> 24) == 255 && (px[0] & 0xFF) == 127, "translucent rect blends: " + Integer.toHexString(px[0]));
    }

    static void copyClips() {
        int[] px = new int[4 * 4];
        Canvas c = canvas(px, 4, 4, 4);
        int[] src = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        c.copy(src, 3, 3, -1, 2);

        check(px[2 * 4] == 2 && px[2 * 4 + 1] == 3, "copy row 0 clipped left");
        check(px[3 * 4] == 5 && px[3 * 4 + 1] == 6, "copy row 1 clipped left");
        check(px[0] == 0 && px[1 * 4] == 0, "copy leaves rows above alone");
        c.copy(src, 3, 3, 3, 0);
        check(px[3] == 1 && px[4 + 3] == 4 && px[8 + 3] == 7, "copy clipped right");
        c.copy(src, 3, 3, 10, 0);
        c.copy(src, 3, 3, 0, 10);
        check(true, "copy fully outside does not throw");
    }

    static void blendScalesAndComposes() {
        int[] px = new int[4 * 4];
        Canvas c = canvas(px, 4, 4, 4);
        int[] src = {0xFF0000FF, 0x00000000, 0x00000000, 0xFF00FF00};
        c.blend(src, 2, 2, 0, 0, 2);
        check(px[0] == 0xFF0000FF && px[1] == 0xFF0000FF && px[4] == 0xFF0000FF && px[5] == 0xFF0000FF, "scaled blue block");
        check(px[2] == 0 && px[3] == 0 && px[6] == 0, "transparent source pixel leaves destination");
        check(px[2 * 4 + 2] == 0xFF00FF00 && px[3 * 4 + 3] == 0xFF00FF00, "scaled green block");
        c.blendPixel(0x80000000, 0, 0, 1);
        check((px[0] & 0xFF) == 0x7F, "blendPixel darkens: " + Integer.toHexString(px[0]));
    }

    static final class Rects implements DigitFont.Sink {
        final List<int[]> list = new ArrayList<>();

        @Override
        public void rect(int x, int y, int w, int h, int argb) {
            this.list.add(new int[] {x, y, w, h, argb});
        }

        long area(int argb) {
            long a = 0;
            for (int[] r : this.list) {
                if (r[4] == argb) {
                    a += (long) r[2] * r[3];
                }
            }
            return a;
        }
    }

    static void digitsHaveTheExpectedShape() {

        int[] lit = {19, 10, 15, 14, 14, 17, 17, 11, 17, 17};

        for (int digit = 0; digit <= 9; digit++) {
            if (digit == 1) {
                continue;
            }
            Rects r = new Rects();
            DigitFont.decorations(r, false, 0, 0, digit, 0, 0, 1);
            check(r.area(-1) == lit[digit], "digit " + digit + " white pixels " + r.area(-1) + " want " + lit[digit]);
            check(r.area(0xFF3F3F3F) == lit[digit], "digit " + digit + " has an equal shadow");
        }
    }

    static void countLayoutAndScale() {
        Rects one = new Rects();
        DigitFont.decorations(one, false, 0, 0, 1, 5, 7, 3);
        check(one.list.isEmpty(), "count 1 draws nothing");
        Rects two = new Rects();
        DigitFont.decorations(two, false, 0, 0, 12, 0, 0, 1);

        int maxX = 0;
        int minX = 1000;
        for (int[] r : two.list) {
            if (r[4] == -1) {
                maxX = Math.max(maxX, r[0] + r[2]);
                minX = Math.min(minX, r[0]);
            }
        }
        check(minX >= 17 - 12 && minX < 17 - 6 + 2, "two digit count starts left of one digit: " + minX);
        check(maxX <= 17 - 6 + 5 + 6, "two digit count ends inside the slot: " + maxX);
        Rects big = new Rects();
        Rects small = new Rects();
        DigitFont.decorations(small, false, 0, 0, 7, 0, 0, 1);
        DigitFont.decorations(big, false, 0, 0, 7, 0, 0, 3);
        check(big.area(-1) == small.area(-1) * 9, "scale 3 gives nine times the area");
        Rects moved = new Rects();
        DigitFont.decorations(moved, false, 0, 0, 7, 10, 20, 1);
        check(moved.list.get(0)[0] == small.list.get(0)[0] + 10 && moved.list.get(0)[1] == small.list.get(0)[1] + 20, "origin offsets everything");
    }

    static void durabilityBar() {
        Rects r = new Rects();
        DigitFont.decorations(r, true, 9, 0x00FF00, 1, 0, 0, 2);
        check(r.list.size() == 2, "bar is two rects when the count is 1");
        int[] back = r.list.get(0);
        int[] fill = r.list.get(1);
        check(back[0] == 4 && back[1] == 26 && back[2] == 26 && back[3] == 4 && back[4] == 0xFF000000, "bar background");
        check(fill[0] == 4 && fill[1] == 26 && fill[2] == 18 && fill[3] == 2 && fill[4] == 0xFF00FF00, "bar fill is 9 steps in the bar colour");
        Rects both = new Rects();
        DigitFont.decorations(both, true, 13, 0xFF0000, 64, 0, 0, 1);
        check(both.list.size() > 2 && both.list.get(0)[4] == 0xFF000000, "bar then count");
    }

    static void endToEndSlot() {

        int scale = 2;
        int px = 16 * scale;
        int[] cell = new int[px * px];
        Canvas c = canvas(cell, px, px, px);
        c.copy(new int[px * px], px, px, 0, 0);
        c.blend(new int[] {0xFFFFFFFF}, 1, 1, 0, 0, 1);
        DigitFont.decorations(c, true, 5, 0xFFAA00, 16, 0, 0, scale);
        int lit = 0;
        for (int v : cell) {
            if (v != 0) {
                lit++;
            }
        }
        check(lit > 30 && cell[0] == 0xFFFFFFFF, "slot gets painted: " + lit);
    }

    public static void main(String[] args) {
        premultiplyAndOver();
        rectClipsAndUsesStride();
        rectBlendsTranslucent();
        copyClips();
        blendScalesAndComposes();
        digitsHaveTheExpectedShape();
        countLayoutAndScale();
        durabilityBar();
        endToEndSlot();
        System.out.println("NoInvLeakTests: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            throw new AssertionError(failed + " NoInvLeak tests failed");
        }
    }
}
