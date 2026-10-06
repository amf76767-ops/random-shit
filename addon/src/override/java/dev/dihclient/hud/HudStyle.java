package dev.dihclient.hud;

import dev.dihclient.gui.theme.Skin;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.Hud;
import dev.dihclient.render.Gfx;
import net.minecraft.class_332;

public final class HudStyle {
    private static Hud hud;

    private HudStyle() {
    }

    public static Hud hud() {
        Hud h = hud;
        if (h == null) {
            h = hud = ModuleManager.of(Hud.class);
        }
        return h;
    }

    public static boolean glass() {
        Hud h = hud();
        return h == null || h.glass();
    }

    public static int bgColor() {
        Hud h = hud();
        int a = h == null ? 170 : h.opacity.get();
        return a << 24 | Skin.c(-233959403) & 0xFFFFFF;
    }

    public static void panel(class_332 g, int x, int y, int w, int h) {
        if (glass()) {
            Hud hud = hud();
            int a = hud == null ? 170 : hud.opacity.get();
            if (a > 40) {
                Gfx.shadow(g, x, y, w, h, 6, 3, a / 255.0F * 0.8F);
            }
            Gfx.rect(g, x, y, w, h, 6, bgColor());
            Gfx.outline(g, x, y, w, h, 6, Math.min(34, a / 6) << 24 | Skin.c(587202559) & 0xFFFFFF);
        }
    }

    public static void accentPanel(class_332 g, int x, int y, int w, int h) {
        panel(g, x, y, w, h);
        if (glass()) {
            Gfx.accentBar(g, x + 5, y, w - 10, 1, 0.0);
        }
    }

    public static int label() {
        return Skin.c(-7564380);
    }
}
