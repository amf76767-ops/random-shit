package dev.dihclient.render;

import dev.dihclient.gui.theme.Skin;
import dev.dihclient.gui.theme.Theme;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.ClickGui;
import dev.dihclient.util.ColorUtil;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_11719;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_5481;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;

/**
 * Drawing helpers of the GUI and the HUD. Compared with 5.6: rounded corners are anti-aliased (the old ones were stepped, which looked
 * pixelated) and text is drawn with a smooth font (Inter, assets/dihclient/font) unless "Smooth Font" in ClickGUI is off.
 */
public final class Gfx {
    public static final int BG = -233959403;
    public static final int BG_SOFT = -15328992;
    public static final int BG_LIGHT = -14868182;
    public static final int BORDER = 587202559;
    public static final int TEXT = -1446670;
    public static final int MUTED = -7564380;
    public static final int GREEN = -11870592;
    public static final int RED = -495247;
    public static final int ORANGE = -278748;
    public static final int SHADOW = 1426063360;
    private static boolean textShadow = true;
    private static final String[] CHARS = new String[128];

    private static final class_2583 STYLE_REGULAR = class_2583.field_24360.method_27704(new class_11719.class_11721(class_2960.method_60655("dihclient", "ui")));
    private static final class_2583 STYLE_BOLD = class_2583.field_24360.method_27704(new class_11719.class_11721(class_2960.method_60655("dihclient", "ui_bold")));
    private static ClickGui gui;

    private Gfx() {
    }

    public static class_327 font() {
        return class_310.method_1551().field_1772;
    }

    public static void setTextShadow(boolean on) {
        textShadow = on;
    }

    // ---------------------------------------------------------------- anti-aliased corners

    /** Coverage of the pixels of one rounded corner (the square of side r in the top left), for the filled shape and for a 1 px outline. */
    private static final class Corner {
        final float[][] fill;
        final float[][] ring;
        final int[] first;

        Corner(int r) {
            this.fill = new float[r][r];
            this.ring = new float[r][r];
            this.first = new int[r];
            final int n = 8;
            for (int i = 0; i < r; i++) {
                for (int c = 0; c < r; c++) {
                    int outer = 0;
                    int inner = 0;
                    for (int sy = 0; sy < n; sy++) {
                        for (int sx = 0; sx < n; sx++) {
                            double dx = r - (c + (sx + 0.5) / n);
                            double dy = r - (i + (sy + 0.5) / n);
                            double d2 = dx * dx + dy * dy;
                            if (d2 <= (double) r * r) {
                                outer++;
                                if (d2 <= (r - 1.0) * (r - 1.0)) {
                                    inner++;
                                }
                            }
                        }
                    }
                    this.fill[i][c] = outer / (float) (n * n);
                    this.ring[i][c] = (outer - inner) / (float) (n * n);
                }
                int f = r;
                while (f > 0 && this.fill[i][f - 1] >= 0.9F) {
                    f--;
                }
                this.first[i] = f;
            }
        }
    }

    private static final Corner[] CORNERS = new Corner[65];

    private static Corner corner(int r) {
        Corner c = CORNERS[r];
        if (c == null) {
            c = CORNERS[r] = new Corner(r);
        }
        return c;
    }

    // ---- the cheap stepped corners of the 5.6 client: used for shadows, when "Smooth Corners" is off and when a frame has used up its budget
    private static final int[][] INSETS = new int[65][];

    private static int inset(int r, int row) {
        double d = r - row - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0.0, r * r - d * d)));
    }

    private static int[] insets(int r) {
        int[] in = r < INSETS.length ? INSETS[r] : null;
        if (in == null) {
            in = new int[r];
            for (int i = 0; i < r; i++) {
                in[i] = inset(r, i);
            }
            if (r < INSETS.length) {
                INSETS[r] = in;
            }
        }
        return in;
    }

    private static int stepRows(class_332 g, int x, int y, int w, int h, int r, int color, boolean bottom) {
        int[] in = insets(r);
        int rows = 0;
        while (rows < r && in[rows] > 0) {
            rows++;
        }
        int i = 0;
        while (i < rows) {
            int inset = in[i];
            int j = i + 1;
            while (j < rows && in[j] == inset) {
                j++;
            }
            g.method_25294(x + inset, y + i, x + w - inset, y + j, color);
            if (bottom) {
                g.method_25294(x + inset, y + h - j, x + w - inset, y + h - i, color);
            }
            i = j;
        }
        return rows;
    }

    private static void stepOutline(class_332 g, int x, int y, int w, int h, int r, int color) {
        int[] in = insets(r);
        int i = 0;
        while (i < r) {
            int inset = in[i];
            int run = Math.max(1, inset - (i + 1 < r ? in[i + 1] : 0));
            int j = i + 1;
            while (j < r && in[j] == inset && Math.max(1, inset - (j + 1 < r ? in[j + 1] : 0)) == run) {
                j++;
            }
            g.method_25294(x + inset, y + i, x + inset + run, y + j, color);
            g.method_25294(x + w - inset - run, y + i, x + w - inset, y + j, color);
            g.method_25294(x + inset, y + h - j, x + inset + run, y + h - i, color);
            g.method_25294(x + w - inset - run, y + h - j, x + w - inset, y + h - i, color);
            i = j;
        }
    }

    /** Smooth corners cost many small fills; each pass (one GuiGraphics) may spend this many, then the cheap corners are used. */
    private static final int AA_BUDGET = 3000;
    private static class_332 pass;
    private static int budget;

    private static boolean smoothCorners(class_332 g, int cost) {
        if (!corners()) {
            return false;
        }
        if (g != pass) {
            pass = g;
            budget = AA_BUDGET;
        }
        if (budget < cost) {
            return false;
        }
        budget -= cost;
        return true;
    }

    private static void dot(class_332 g, int x, int y, int color, float cover) {
        int a = (int) ((color >>> 24) * cover);
        if (a > 1) {
            g.method_25294(x, y, x + 1, y + 1, a << 24 | (color & 0xFFFFFF));
        }
    }

    private static void fillRows(class_332 g, int x, int y, int w, int h, int r, int color, boolean bottom) {
        Corner k = corner(r);
        for (int i = 0; i < r; i++) {
            int f = k.first[i];
            if (x + f < x + w - f) {
                g.method_25294(x + f, y + i, x + w - f, y + i + 1, color);
                if (bottom) {
                    g.method_25294(x + f, y + h - 1 - i, x + w - f, y + h - i, color);
                }
            }
            for (int c = 0; c < f; c++) {
                float cover = k.fill[i][c];
                if (cover > 0.12F) {
                    dot(g, x + c, y + i, color, cover);
                    dot(g, x + w - 1 - c, y + i, color, cover);
                    if (bottom) {
                        dot(g, x + c, y + h - 1 - i, color, cover);
                        dot(g, x + w - 1 - c, y + h - 1 - i, color, cover);
                    }
                }
            }
        }
    }

    public static void rect(class_332 g, int x, int y, int w, int h, int r, int color) {
        if (w > 0 && h > 0 && color >>> 24 != 0) {
            if (Skin.active()) {
                r = Skin.radius(r);
            }
            r = Math.max(0, Math.min(r, Math.min(Math.min(w, h) / 2, 64)));
            if (r == 0) {
                g.method_25294(x, y, x + w, y + h, color);
            } else if (color >>> 24 >= 40 && smoothCorners(g, r * 8)) {
                fillRows(g, x, y, w, h, r, color, true);
                g.method_25294(x, y + r, x + w, y + h - r, color);
            } else {
                int rows = stepRows(g, x, y, w, h, r, color, true);
                g.method_25294(x, y + rows, x + w, y + h - rows, color);
            }
        }
    }

    public static void rectTop(class_332 g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(Math.min(w, h) / 2, 64)));
        if (r == 0) {
            g.method_25294(x, y, x + w, y + h, color);
        } else if (color >>> 24 >= 40 && smoothCorners(g, r * 4)) {
            fillRows(g, x, y, w, h, r, color, false);
            g.method_25294(x, y + r, x + w, y + h, color);
        } else {
            int rows = stepRows(g, x, y, w, h, r, color, false);
            g.method_25294(x, y + rows, x + w, y + h, color);
        }
    }

    public static void outline(class_332 g, int x, int y, int w, int h, int r, int color) {
        if (w > 1 && h > 1) {
            if (Skin.active()) {
                r = Skin.radius(r);
            }
            r = Math.max(0, Math.min(r, Math.min(Math.min(w, h) / 2, 64)));
            g.method_25294(x + r, y, x + w - r, y + 1, color);
            g.method_25294(x + r, y + h - 1, x + w - r, y + h, color);
            g.method_25294(x, y + r, x + 1, y + h - r, color);
            g.method_25294(x + w - 1, y + r, x + w, y + h - r, color);
            if (r == 0) {
                return;
            }
            if (color >>> 24 < 40 || !smoothCorners(g, r * 6)) {
                stepOutline(g, x, y, w, h, r, color);
                return;
            }
            Corner k = corner(r);
            for (int i = 0; i < r; i++) {
                for (int c = 0; c < r; c++) {
                    float cover = k.ring[i][c];
                    if (cover > 0.12F) {
                        dot(g, x + c, y + i, color, cover);
                        dot(g, x + w - 1 - c, y + i, color, cover);
                        dot(g, x + c, y + h - 1 - i, color, cover);
                        dot(g, x + w - 1 - c, y + h - 1 - i, color, cover);
                    }
                }
            }
        }
    }

    public static void round(class_332 g, int x, int y, int w, int h, int color) {
        rect(g, x, y, w, h, 2, color);
    }

    public static void outline(class_332 g, int x, int y, int w, int h, int color) {
        outline(g, x, y, w, h, 2, color);
    }

    public static void shadow(class_332 g, int x, int y, int w, int h, int r, int size, float strength) {
        for (int i = size; i > 0; i--) {
            int a = (int) (strength * 60.0F * (1.0F - (float) i / (size + 1)) / size * 2.0F);
            if (a > 0) {
                // a soft translucent layer needs no smooth edge
                int rr = Math.max(0, Math.min(r + i, Math.min(w + i * 2, h + i * 2) / 2));
                int rows = rr == 0 ? 0 : stepRows(g, x - i, y - i + 1, w + i * 2, h + i * 2, rr, a << 24, true);
                g.method_25294(x - i, y - i + 1 + rows, x + w + i, y - i + 1 + h + i * 2 - rows, a << 24);
            }
        }
    }

    public static void hGradient(class_332 g, int x, int y, int w, int h, int from, int to) {
        if (w > 0) {
            int step = w > 160 ? 2 : 1;
            for (int i = 0; i < w; i += step) {
                int c = ColorUtil.blend(from, to, (float) i / Math.max(1, w - 1));
                g.method_25294(x + i, y, x + Math.min(w, i + step), y + h, c);
            }
        }
    }

    public static void vGradient(class_332 g, int x, int y, int w, int h, int from, int to) {
        g.method_25296(x, y, x + w, y + h, from, to);
    }

    public static void accentBar(class_332 g, int x, int y, int w, int h, double phase) {
        int step = Math.max(1, w / 40);
        for (int i = 0; i < w; i += step) {
            g.method_25294(x + i, y, x + Math.min(w, i + step), y + h, Theme.accentAt(phase + (double) i / Math.max(1, w) * 0.5));
        }
    }

    public static void panel(class_332 g, int x, int y, int w, int h, int accent) {
        shadow(g, x, y, w, h, 4, 3, 0.8F);
        rect(g, x, y, w, h, 4, -435154409);
        outline(g, x, y, w, h, 4, 419430399);
        accentBar(g, x + 3, y, w - 6, 1, 0.0);
    }

    public static void toggle(class_332 g, int x, int y, float on, int color) {
        int w = 18;
        int h = 10;
        int track = ColorUtil.blend(Skin.c(-13881027), color, on);
        rect(g, x, y, w, h, 5, track);
        int knob = (int) (x + 1 + on * (w - 10));
        rect(g, knob, y + 1, 8, 8, 4, -1);
    }

    public static void slider(class_332 g, int x, int y, int w, float value, int color, boolean big) {
        value = Math.max(0.0F, Math.min(1.0F, value));
        rect(g, x, y, w, 4, 2, Skin.c(-13881027));
        int filled = Math.max(4, (int) (w * value));
        rect(g, x, y, filled, 4, 2, color);
        int knobX = x + (int) (w * value) - 3;
        int size = big ? 8 : 6;
        rect(g, knobX - (size - 6) / 2, y + 2 - size / 2, size, size, size / 2, -1);
    }

    public static void bar(class_332 g, int x, int y, int w, int h, float value, int back, int front) {
        rect(g, x, y, w, h, h / 2, back);
        int filled = (int) (w * Math.max(0.0F, Math.min(1.0F, value)));
        if (filled > 0) {
            rect(g, x, y, Math.max(filled, h), h, h / 2, front);
        }
    }

    // ---------------------------------------------------------------- text

    private static boolean smooth() {
        ClickGui g = gui;
        if (g == null) {
            g = gui = ModuleManager.of(ClickGui.class);
        }
        return g == null || g.smoothFont.get();
    }

    private static boolean corners() {
        ClickGui g = gui;
        if (g == null) {
            g = gui = ModuleManager.of(ClickGui.class);
        }
        return g == null || g.smoothCorners.get();
    }

    /** A string ready to draw in the smooth font (a leading "\u00a7l" means the bold cut), with its width. */
    private record Line(class_5481 text, int width) {
    }

    private static final Map<String, Line> LINES = new HashMap<>();

    private static Line line(String s) {
        Line l = LINES.get(s);
        if (l == null) {
            boolean bold = s.startsWith("\u00a7l");
            class_5481 seq = class_2561.method_43470(bold ? s.substring(2) : s).method_10862(bold ? STYLE_BOLD : STYLE_REGULAR).method_30937();
            l = new Line(seq, font().method_30880(seq));
            if (LINES.size() > 4096) {
                LINES.clear();
            }
            LINES.put(s, l);
        }
        return l;
    }

    private static void draw(class_332 g, String s, int x, int y, int color) {
        boolean shadow = textShadow && !Skin.frost();
        if (smooth()) {
            g.method_51430(font(), line(s).text, x, y, color, shadow);
        } else {
            g.method_51433(font(), s, x, y, color, shadow);
        }
    }

    /** Replaces the direct {@code drawString(font, component, ...)} calls of the old GUI classes (see Patcher.routeComponentText). */
    public static void drawComponent(class_332 g, class_327 font, class_2561 text, int x, int y, int color, boolean shadow) {
        if (smooth() && text.method_10855().isEmpty()) {
            String plain = text.getString();
            g.method_51430(font, line(text.method_10866().method_10984() ? "\u00a7l" + plain : plain).text, x, y, color, shadow);
        } else {
            g.method_51439(font, text, x, y, color, shadow);
        }
    }

    public static void text(class_332 g, String s, int x, int y, int color) {
        if (color >>> 24 >= 8) {
            draw(g, s, x, y, color);
        }
    }

    public static void text(class_332 g, String s, float x, float y, int color, float scale) {
        if (color >>> 24 >= 8) {
            g.method_51448().pushMatrix();
            g.method_51448().translate(x, y);
            g.method_51448().scale(scale, scale);
            draw(g, s, 0, 0, color);
            g.method_51448().popMatrix();
        }
    }

    public static void textCentered(class_332 g, String s, int x, int y, int color) {
        text(g, s, x - width(s) / 2, y, color);
    }

    public static void accentText(class_332 g, String s, int x, int y, double phase) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            String part = ch < CHARS.length ? CHARS[ch] : String.valueOf(ch);
            text(g, part, cx, y, Theme.accentAt(phase + i * 0.04));
            cx += width(part);
        }
    }

    public static int width(String s) {
        return smooth() ? line(s).width : font().method_1727(s);
    }

    public static String trim(String s, int max) {
        if (s == null) {
            return "";
        } else if (width(s) <= max) {
            return s;
        } else {
            String dots = "…";
            int room = max - width(dots);
            int lo = 0;
            int hi = s.length();
            while (lo < hi) {
                int mid = lo + hi + 1 >>> 1;
                if (width(s.substring(0, mid)) > room) {
                    hi = mid - 1;
                } else {
                    lo = mid;
                }
            }
            return s.substring(0, lo) + dots;
        }
    }

    public static boolean inside(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    static {
        for (int i = 0; i < CHARS.length; i++) {
            CHARS[i] = String.valueOf((char) i);
        }
    }
}
