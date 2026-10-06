package dev.dihclient.gui.theme;

import dev.dihclient.modules.client.ClickGui;
import dev.dihclient.util.ColorUtil;
import java.awt.Color;

public final class Theme {
    public static final int WINDOW = -233959403;
    public static final int SIDEBAR = -16052975;
    public static final int SURFACE = -15328992;
    public static final int SURFACE_2 = -14868182;
    public static final int HOVER = -14341579;
    public static final int BORDER = 587202559;
    public static final int TEXT = -1446670;
    public static final int MUTED = -7564380;
    public static final int DIM = -10788238;
    public static final int GREEN = -11870592;
    public static final int RED = -495247;
    public static final int ORANGE = -278748;
    public static final int SWITCH_OFF = -13881027;
    public static final int DEFAULT_ACCENT = -1754827;
    public static final int DEFAULT_ACCENT_2 = -30147;

    private Theme() {
    }

    private static ClickGui gui() {
        return Skin.gui();
    }

    private static boolean custom(ClickGui g) {
        return g != null && g.customAccent.get();
    }

    public static int accent() {
        ClickGui g = gui();
        if (custom(g)) {
            return g.accent.get() | 0xFF000000;
        }
        return Skin.accent1();
    }

    public static int accent2() {
        ClickGui g = gui();
        if (custom(g)) {
            return g.accent2.get() | 0xFF000000;
        }
        return Skin.accent2();
    }

    public static int accentAt(double phase) {
        ClickGui g = gui();
        ColorMode mode = g == null ? ColorMode.STATIC : g.colorMode.get();
        double speed = g == null ? 1.0 : g.colorSpeed.get();
        double t = System.currentTimeMillis() / 1000.0 * speed;
        switch (mode) {
            case STATIC:
                return accent();
            case RAINBOW: {
                float h = (float) ((t * 0.15 + phase) % 1.0);
                if (h < 0.0F) {
                    h += 1.0F;
                }
                return 0xFF000000 | Color.HSBtoRGB(h, 0.45F, 0.85F);
            }
            default: {
                float k = (float) (Math.sin((t + phase * 6.0) * 1.2) * 0.5 + 0.5);
                return ColorUtil.blend(accent(), accent2(), k);
            }
        }
    }

    public static int withAlpha(int color, float f) {
        return ColorUtil.withAlpha(color, (int) (Math.max(0.0F, Math.min(1.0F, f)) * (color >>> 24 & 0xFF)));
    }

    public enum ColorMode {
        STATIC,
        GRADIENT,
        RAINBOW;
    }
}
