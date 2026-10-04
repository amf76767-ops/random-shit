package dev.dihclient.gui.theme;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.ClickGui;
import dev.dihclient.util.ColorUtil;

/**
 * Maps the colours of the normal look to the colours of the chosen theme (Classic is left as it is). Same as in 5.6 plus the
 * Glass theme: see-through dark panels over the blurred game, white text, soft rounded corners.
 */
public final class Skin {
    private static boolean active;
    private static ClickGui gui;

    private Skin() {
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
        return g == null ? ClickGui.Look.CLASSIC : g.look.get();
    }

    public static boolean active() {
        return active && look() != ClickGui.Look.CLASSIC;
    }

    public static boolean neon() {
        return active() && look() == ClickGui.Look.NEON;
    }

    public static boolean frost() {
        return active() && look() == ClickGui.Look.FROST;
    }

    public static boolean pixel() {
        return active() && look() == ClickGui.Look.PIXEL;
    }

    public static boolean glass() {
        return active() && look() == ClickGui.Look.GLASS;
    }

    public static int c(int color) {
        ClickGui.Look look = look();
        if (look == ClickGui.Look.CLASSIC) {
            return color;
        }
        if (look == ClickGui.Look.GLASS) {
            return glassColor(color);
        }
        boolean neon = look == ClickGui.Look.NEON;
        boolean frost = look == ClickGui.Look.FROST;
        switch (color) {
            case -535555048:
                return c(-15328992);
            case -300937196:
            case -267382764:
                return c(-233959403);
            case -233959403:
                return neon ? -200865268 : (frost ? -253432069 : -12960443);
            case -16052975:
                return neon ? -16052205 : (frost ? -855638017 : -13750217);
            case -15592422:
                return neon ? -16184048 : (frost ? -1493172225 : -13421252);
            case -15328992:
                return neon ? -15656930 : (frost ? -1191182337 : -12105134);
            case -14868182:
                return neon ? -15063764 : (frost ? -2892306 : -11184033);
            case -14341579:
                return neon ? -14931407 : (frost ? -419430401 : -10723223);
            case -13881027:
                return neon ? -15063764 : (frost ? -3615512 : -14737114);
            case -10788238:
                return neon ? -10983058 : (frost ? -8548434 : -6644056);
            case -7564380:
                return neon ? -7693152 : (frost ? -11903878 : -3157286);
            case -1446670:
                return neon ? -1904914 : (frost ? -15260099 : -855310);
            case 921621:
                return c(-233959403) & 16777215;
            case 352321535:
                return neon ? 402718689 : (frost ? 872415231 : 587202559);
            case 419430399:
                return c(352321535);
            case 587202559:
                return neon ? -2147418143 : (frost ? -855638017 : -15921648);
            case 872415231:
                return c(587202559);
            case 1090519039:
                return neon ? -16711711 : (frost ? 1716219856 : -6644056);
            case 1442840575:
                return c(1090519039);
            case 1711276032:
                return neon ? 1711276032 : (frost ? 1084865753 : 1073741824);
            default:
                return color;
        }
    }

    /** The glass look: dark see-through panels, light see-through "frost" on top of them, white text. */
    private static int glassColor(int color) {
        switch (color) {
            case -535555048:
                return glassColor(-15328992);
            case -300937196:
            case -267382764:
                return glassColor(-233959403);
            case -233959403: // window
                return 0x6612121A;
            case -16052975: // sidebar
                return 0x500C0C12;
            case -15592422:
                return 0x26FFFFFF;
            case -15328992: // surface
                return 0x24FFFFFF;
            case -14868182: // second surface
                return 0x30FFFFFF;
            case -14341579: // hover
                return 0x40FFFFFF;
            case -13881027: // switch off
                return 0x4DFFFFFF;
            case -10788238: // dim text
                return 0x99FFFFFF;
            case -7564380: // muted text
                return 0xCCFFFFFF;
            case -1446670: // text
                return 0xFFFFFFFF;
            case 921621:
                return glassColor(-233959403) & 0xFFFFFF;
            case 352321535:
                return 0x1AFFFFFF;
            case 419430399:
                return glassColor(352321535);
            case 587202559: // border
                return 0x33FFFFFF;
            case 872415231:
                return glassColor(587202559);
            case 1090519039:
                return 0x80FFFFFF;
            case 1442840575:
                return glassColor(1090519039);
            case 1711276032:
                return 0x66000000;
            default:
                return color;
        }
    }

    public static int onCard() {
        return look() == ClickGui.Look.FROST ? -15720390 : -1;
    }

    public static int accent(double phase) {
        float t = (float) (Math.sin(phase * 6.0 + System.currentTimeMillis() / 1600.0) * 0.5 + 0.5);
        switch (look()) {
            case NEON:
                return ColorUtil.blend(-16711711, -4784325, t);
            case FROST:
                return ColorUtil.blend(-12743681, -14628428, t);
            case PIXEL:
                return ColorUtil.blend(-11141238, -13654950, t);
            case GLASS: // soft blue to soft violet; white text stays readable on it
                return ColorUtil.blend(0xFF7FB8FF, 0xFFC9A8FF, t);
            default:
                return 0;
        }
    }

    public static int dimTop(float f) {
        switch (look()) {
            case NEON:
                return (int) (f * 150.0F) << 24 | 198151;
            case FROST:
                return (int) (f * 70.0F) << 24 | 14477562;
            case PIXEL:
                return (int) (f * 120.0F) << 24 | 1053720;
            case GLASS: // the blur does most of the work; only a light veil
                return (int) (f * 40.0F) << 24 | 0x0A0A14;
            default:
                return (int) (f * 112.0F) << 24;
        }
    }

    public static int dimBottom(float f) {
        switch (look()) {
            case NEON:
                return (int) (f * 185.0F) << 24 | 265228;
            case FROST:
                return (int) (f * 130.0F) << 24 | 11978992;
            case PIXEL:
                return (int) (f * 170.0F) << 24 | 1839626;
            case GLASS:
                return (int) (f * 95.0F) << 24 | 0x050510;
            default:
                return (int) (f * 160.0F) << 24;
        }
    }

    public static int radius(int r) {
        switch (look()) {
            case NEON:
                return Math.min(r, 3);
            case FROST:
                return r * 2;
            case PIXEL:
                return 0;
            case GLASS:
                return r * 2;
            default:
                return r;
        }
    }
}
