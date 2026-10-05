package dev.dihclient.gui.theme;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.ClickGui;
import dev.dihclient.util.ColorUtil;

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

    private static boolean glassStyle() {
        ClickGui g = gui();
        return g != null && g.layout.get() == ClickGui.Layout.GLASS;
    }

    public static boolean active() {
        return active && glassStyle();
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
        return active();
    }

    public static int c(int color) {
        return glassStyle() ? glassColor(color) : color;
    }

    private static int glassColor(int color) {
        switch (color) {
            case -535555048:
                return glassColor(-15328992);
            case -300937196:
            case -267382764:
                return glassColor(-233959403);
            case -233959403:
                return 0x6612121A;
            case -16052975:
                return 0x500C0C12;
            case -15592422:
                return 0x26FFFFFF;
            case -15328992:
                return 0x24FFFFFF;
            case -14868182:
                return 0x30FFFFFF;
            case -14341579:
                return 0x40FFFFFF;
            case -13881027:
                return 0x4DFFFFFF;
            case -10788238:
                return 0x99FFFFFF;
            case -7564380:
                return 0xCCFFFFFF;
            case -1446670:
                return 0xFFFFFFFF;
            case 921621:
                return glassColor(-233959403) & 0xFFFFFF;
            case 352321535:
                return 0x1AFFFFFF;
            case 419430399:
                return glassColor(352321535);
            case 587202559:
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
        return -1;
    }

    public static int accent(double phase) {
        float t = (float) (Math.sin(phase * 6.0 + System.currentTimeMillis() / 1600.0) * 0.5 + 0.5);

        return ColorUtil.blend(0xFF7FB8FF, 0xFFC9A8FF, t);
    }

    public static int dimTop(float f) {

        return glassStyle() ? (int) (f * 40.0F) << 24 | 0x0A0A14 : (int) (f * 112.0F) << 24;
    }

    public static int dimBottom(float f) {
        return glassStyle() ? (int) (f * 95.0F) << 24 | 0x050510 : (int) (f * 160.0F) << 24;
    }

    public static int radius(int r) {
        return glassStyle() ? r * 2 : r;
    }
}
