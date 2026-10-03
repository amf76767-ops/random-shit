package dev.dihclient.modules.client;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.hud.HudManager;
import dev.dihclient.render.Gfx;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.util.ColorUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.class_1657;
import net.minecraft.class_1934;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_640;
import net.minecraft.class_7532;

/** The TAB list as a clean card: rounded panel, one row per player with head, name, health, distance and a ping meter. */
public class BetterTablist extends Module {
    public final BoolSetting health = this.bool("Health", "Shows the health of loaded players.", true).legacy("betterTab.health");
    public final BoolSetting distance = this.bool("Distance", "Shows the distance to loaded players.", true).legacy("betterTab.distance");
    public final BoolSetting ping = this.bool("Ping", "Shows latency as a small meter.", true);

    private static final int ROW_H = 14;
    private static final int COL_W = 200;

    public BetterTablist() {
        super("BetterTablist", Category.CLIENT, "A clean TAB list: health, distance and a ping meter per player, spectators greyed out.");
    }

    private static int pingColor(int ms) {
        return ms < 0 ? 0xFF777777 : ms < 80 ? 0xFF4ADE80 : ms < 160 ? 0xFFFACC15 : ms < 300 ? 0xFFFB923C : 0xFFEF4444;
    }

    /** Four small bars, filled by how good the connection is. */
    private static void pingMeter(class_332 g, int x, int y, int ms) {
        int filled = ms < 0 ? 0 : ms < 80 ? 4 : ms < 160 ? 3 : ms < 300 ? 2 : 1;
        int color = pingColor(ms);
        for (int i = 0; i < 4; i++) {
            int h = 3 + i * 2;
            Gfx.rect(g, x + i * 3, y + 9 - h, 2, h, 1, i < filled ? color : 0x44FFFFFF);
        }
    }

    public static void renderIfHeld(class_332 g) {
        class_310 mc = class_310.method_1551();
        if (!mc.field_1690.field_1907.method_1434() || mc.method_1562() == null || mc.field_1724 == null) {
            return;
        }
        BetterTablist self = ModuleManager.of(BetterTablist.class);
        List<class_640> players = new ArrayList<>(mc.method_1562().method_45732());
        players.sort(Comparator.<class_640, Boolean>comparing(p -> p.method_2958() == class_1934.field_9219)
                .thenComparing(p -> p.method_2966().name().toLowerCase(Locale.ROOT)));

        int rowsPerCol = Math.max(1, Math.min(20, (mc.method_22683().method_4502() - 90) / ROW_H));
        int cols = Math.max(1, (players.size() + rowsPerCol - 1) / rowsPerCol);
        int rows = Math.min(players.size(), rowsPerCol);
        int width = cols * COL_W + 12;
        int height = rows * ROW_H + 34;
        int x0 = (mc.method_22683().method_4486() - width) / 2;
        int y0 = 12;
        int accent = HudManager.accent();

        Gfx.panel(g, x0, y0, width, height, accent);
        Gfx.text(g, "Players", x0 + 8, y0 + 7, -1);
        String count = players.size() + " online";
        Gfx.text(g, count, x0 + width - 8 - Gfx.width(count), y0 + 7, 0xFF9AA0AE);
        Gfx.rect(g, x0 + 6, y0 + 20, width - 12, 1, 0, 0x33FFFFFF);

        for (int i = 0; i < players.size(); i++) {
            class_640 entry = players.get(i);
            int col = i / rowsPerCol;
            int row = i % rowsPerCol;
            int x = x0 + 6 + col * COL_W;
            int y = y0 + 25 + row * ROW_H;
            boolean spectator = entry.method_2958() == class_1934.field_9219;
            boolean me = entry.method_2966().id().equals(mc.field_1724.method_5667());
            if (me) {
                Gfx.rect(g, x, y - 1, COL_W - 4, ROW_H - 1, 3, (accent & 0x00FFFFFF) | 0x40000000);
            } else if ((row & 1) == 0) {
                Gfx.rect(g, x, y - 1, COL_W - 4, ROW_H - 1, 3, 0x14FFFFFF);
            }

            class_7532.method_52722(g, entry.method_52810(), x + 2, y, 10);
            String name = entry.method_2966().name();
            Gfx.text(g, name, x + 16, y + 1, spectator ? 0xFF8A8F9C : (me ? accent : 0xFFE9ECF2));

            int right = x + COL_W - 8;
            if (self.ping.get()) {
                right -= 12;
                pingMeter(g, right, y, entry.method_2959());
                String ms = entry.method_2959() + "ms";
                right -= Gfx.width(ms) + 4;
                Gfx.text(g, ms, right, y + 1, pingColor(entry.method_2959()));
            }
            class_1657 player = mc.field_1687.method_18470(entry.method_2966().id());
            if (player != null) {
                if (self.distance.get() && player != mc.field_1724) {
                    String d = String.format(Locale.ROOT, "%.0fm", mc.field_1724.method_5739(player));
                    right -= Gfx.width(d) + 6;
                    Gfx.text(g, d, right, y + 1, 0xFF9AA0AE);
                }
                if (self.health.get()) {
                    float hp = player.method_6032() + player.method_6067();
                    String h = String.format(Locale.ROOT, "%.0f❤", hp);
                    right -= Gfx.width(h) + 6;
                    Gfx.text(g, h, right, y + 1, ColorUtil.health(player.method_6032() / Math.max(1.0F, player.method_6063())));
                }
            }
        }
    }
}
