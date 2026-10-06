package dev.dihclient.gui.theme;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.ClickGui;
import dev.dihclient.util.ColorUtil;

public final class Skin {
    private static final int BLACK = 0xFF000000;
    private static final long CROSSFADE_MS = 380L;

    private static final int[] KEYS = {
        -233959403, -300937196, -267382764, -535555048, -16052975, -15592422, -15328992, -14868182,
        -14341579, -13881027, -1446670, -7564380, -10788238, 587202559, 872415231, 352321535,
        419430399, 1090519039, 1442840575, 1711276032, 921621
    };
    private static final int WINDOW = 0, PANEL = 1, TIP = 2, FIELD = 3, SIDEBAR = 4, ALT = 5, SURFACE = 6, SURFACE2 = 7;
    private static final int HOVER = 8, SWITCH = 9, TEXT = 10, MUTED = 11, DIM = 12, BORDER = 13, BORDER2 = 14, HL1 = 15;
    private static final int HL2 = 16, HL3 = 17, HL4 = 18, SHADE = 19, FADE = 20;

    public enum Header {
        LINE,
        TINT,
        DOT
    }

    public enum Rows {
        PILL,
        BAR
    }

    public record Profile(
            String title,
            int[] palette,
            int accent,
            int accent2,
            float radius,
            float shadow,
            int panelAlpha,
            Header header,
            Rows rows) {
    }

    private static final Profile[] PROFILES = new Profile[ClickGui.Look.values().length];

    static {
        PROFILES[ClickGui.Look.OBSIDIAN.ordinal()] = new Profile("Obsidian",
                build(0x0B0B0D, 0xA4A4AE, 0xE7E7EB, 0x8D8D97, 0x5E5E68), 0xFFB9BDC9, 0xFF878B97, 1.5F, 1.0F, 0xF0, Header.LINE, Rows.PILL);
        PROFILES[ClickGui.Look.GRAPHITE.ordinal()] = new Profile("Graphite",
                build(0x18191C, 0xB2B4BA, 0xE9EAED, 0x93969E, 0x666870), 0xFF8FA2B6, 0xFF6C7C8F, 1.25F, 0.8F, 0xEE, Header.TINT, Rows.PILL);
        PROFILES[ClickGui.Look.ONYX.ordinal()] = new Profile("Onyx",
                build(0x050506, 0x8E8E97, 0xE3E3E8, 0x85858F, 0x575760), 0xFF9C92B8, 0xFF6B6485, 2.0F, 1.2F, 0xF4, Header.DOT, Rows.PILL);
        PROFILES[ClickGui.Look.EMBER.ordinal()] = new Profile("Ember",
                build(0x151211, 0xB4A79F, 0xEBE5E0, 0x9A8F88, 0x6C625C), 0xFFBF8A63, 0xFF8E5F46, 1.25F, 1.0F, 0xEE, Header.LINE, Rows.BAR);
        PROFILES[ClickGui.Look.MIDNIGHT.ordinal()] = new Profile("Midnight",
                build(0x0A0C13, 0x9AA2B9, 0xE4E7F0, 0x8890A6, 0x5B627A), 0xFF7C86B6, 0xFF5B6490, 1.75F, 1.1F, 0xE8, Header.TINT, Rows.PILL);
        PROFILES[ClickGui.Look.MOSS.ordinal()] = new Profile("Moss",
                build(0x0D120F, 0x9DAB9F, 0xE4EAE5, 0x8A988D, 0x5E6C61), 0xFF8BA48A, 0xFF62785F, 1.0F, 0.9F, 0xEE, Header.DOT, Rows.BAR);
        PROFILES[ClickGui.Look.PLUM.ordinal()] = new Profile("Plum",
                build(0x110D12, 0xAB9DAE, 0xEBE4EC, 0x988B9B, 0x6A5E6D), 0xFFA489A8, 0xFF7B6480, 1.5F, 1.0F, 0xEC, Header.LINE, Rows.PILL);
        PROFILES[ClickGui.Look.ABYSS.ordinal()] = new Profile("Abyss",
                build(0x0A1113, 0x98ADAF, 0xE3EBEC, 0x869A9C, 0x586B6D), 0xFF70A09C, 0xFF4E7773, 1.25F, 1.1F, 0xEA, Header.TINT, Rows.BAR);
    }

    private static boolean active;
    private static ClickGui gui;
    private static ClickGui.Look lastLook;
    private static ClickGui.Look fromLook;
    private static long changedAt;

    private Skin() {
    }

    private static int[] build(int base, int lift, int text, int muted, int dim) {
        int b = base | BLACK;
        int l = lift | BLACK;
        int[] p = new int[KEYS.length];
        p[WINDOW] = 0xF2000000 | base;
        p[PANEL] = 0xEE000000 | base;
        p[TIP] = 0xF2000000 | ColorUtil.blend(b, l, 0.06F) & 0xFFFFFF;
        p[FIELD] = 0xE6000000 | ColorUtil.blend(b, l, 0.07F) & 0xFFFFFF;
        p[SIDEBAR] = ColorUtil.blend(b, BLACK, 0.25F);
        p[ALT] = ColorUtil.blend(b, l, 0.05F);
        p[SURFACE] = ColorUtil.blend(b, l, 0.09F);
        p[SURFACE2] = ColorUtil.blend(b, l, 0.15F);
        p[HOVER] = ColorUtil.blend(b, l, 0.22F);
        p[SWITCH] = ColorUtil.blend(b, l, 0.30F);
        p[TEXT] = text | BLACK;
        p[MUTED] = muted | BLACK;
        p[DIM] = dim | BLACK;
        p[BORDER] = 0x1C000000 | lift;
        p[BORDER2] = 0x2A000000 | lift;
        p[HL1] = 0x10000000 | lift;
        p[HL2] = 0x16000000 | lift;
        p[HL3] = 0x34000000 | lift;
        p[HL4] = 0x48000000 | lift;
        p[SHADE] = 0x70000000 | (base & 0x0A0A0A);
        p[FADE] = p[WINDOW] & 0xFFFFFF;
        return p;
    }

    public static void begin() {
        active = true;
    }

    public static void end() {
        active = false;
    }

    static ClickGui gui() {
        ClickGui g = gui;
        if (g == null) {
            g = gui = ModuleManager.of(ClickGui.class);
        }
        return g;
    }

    public static ClickGui.Look look() {
        ClickGui g = gui();
        ClickGui.Look now = g == null ? ClickGui.Look.OBSIDIAN : g.look.get();
        if (now != lastLook) {
            fromLook = lastLook;
            lastLook = now;
            changedAt = fromLook == null ? 0L : System.currentTimeMillis();
        }
        return now;
    }

    public static Profile profile() {
        return PROFILES[look().ordinal()];
    }

    public static boolean active() {
        return active;
    }

    public static boolean neon() {
        return false;
    }

    public static boolean frost() {
        return false;
    }

    public static boolean pixel() {
        return false;
    }

    public static boolean glass() {
        return false;
    }

    private static float mix() {
        if (changedAt == 0L) {
            return 1.0F;
        }
        float t = (System.currentTimeMillis() - changedAt) / (float) CROSSFADE_MS;
        if (t >= 1.0F) {
            changedAt = 0L;
            return 1.0F;
        }
        return Anim.easeOutCubic(t);
    }

    private static int slot(int color) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i] == color) {
                return i;
            }
        }
        return -1;
    }

    private static int tone(Profile p, int color) {
        switch (color) {
            case -535555048:
                return p.palette[FIELD];
            case -855638017:
                return 0xCC000000 | p.palette[TEXT];
        }
        int s = slot(color);
        return s < 0 ? color : p.palette[s];
    }

    public static int c(int color) {
        Profile p = profile();
        int now = tone(p, color);
        float t = mix();
        if (t >= 1.0F || fromLook == null) {
            return now;
        }
        return ColorUtil.blend(tone(PROFILES[fromLook.ordinal()], color), now, t);
    }

    public static int onCard() {
        return profile().palette[TEXT];
    }

    public static int accent1() {
        return blendAccent(true);
    }

    public static int accent2() {
        return blendAccent(false);
    }

    private static int blendAccent(boolean first) {
        Profile p = profile();
        int now = first ? p.accent : p.accent2;
        float t = mix();
        if (t >= 1.0F || fromLook == null) {
            return now;
        }
        Profile o = PROFILES[fromLook.ordinal()];
        return ColorUtil.blend(first ? o.accent : o.accent2, now, t);
    }

    public static int accent(double phase) {
        float t = (float) (Math.sin(phase * 6.0 + System.currentTimeMillis() / 1600.0) * 0.5 + 0.5);
        return ColorUtil.blend(accent1(), accent2(), t);
    }

    public static int dimTop(float f) {
        return (int) (f * 70.0F) << 24 | 0x040406;
    }

    public static int dimBottom(float f) {
        return (int) (f * 150.0F) << 24 | 0x020203;
    }

    public static int radius(int r) {
        return Math.round(r * profile().radius);
    }

    public static float shadow() {
        return profile().shadow;
    }

    public static int panelAlpha() {
        return profile().panelAlpha;
    }
}
