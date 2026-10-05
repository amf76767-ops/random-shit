package dev.dihclient.port.profile;

import dev.dihclient.DIHClient;
import dev.dihclient.gui.theme.Theme;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Gfx;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.class_310;
import net.minecraft.class_332;

public class FrameProfiler extends Module {
    public static final int TICK = 0;
    public static final int R2D = 1;
    public static final int R3D = 2;

    private static volatile boolean active;
    private static final Map<String, long[]> RUNNING = new HashMap<>();
    private static long windowStart;
    private static long startedAt;
    private static String startedName;
    private static int startedPhase;

    private record Row(String name, long[] nanos, int[] calls) {
        double percent(long window) {
            return (this.nanos[0] + this.nanos[1] + this.nanos[2]) * 100.0 / window;
        }
    }

    public final IntSetting lines = this.integer("Lines", "How many modules the overlay lists.", 6, 1, 15);
    public final IntSetting x = this.integer("X", "Overlay position.", 6, 0, 2000);
    public final IntSetting y = this.integer("Y", "Overlay position.", 30, 0, 2000);
    public final BoolSetting overlay = this.bool("Overlay", "Shows the list on screen.", true);

    private volatile List<Row> rows = List.of();
    private volatile double totalPercent;
    private volatile int fps;
    private int frames;
    private long frameWindow;

    public FrameProfiler() {
        super("Frame Profiler", Category.CLIENT, "Shows which modules take the most time per second, to find what makes the game slow.");
        this.action("Report", "Writes the biggest modules to the log and shows the top 5.", this::report);
    }

    @Override
    protected void onEnable() {
        RUNNING.clear();
        windowStart = System.nanoTime();
        this.frameWindow = windowStart;
        this.frames = 0;
        active = true;
    }

    @Override
    protected void onDisable() {
        active = false;
        RUNNING.clear();
        this.rows = List.of();
    }

    @Override
    public String getInfo() {
        return String.format(Locale.ROOT, "%d fps · %.0f%%", this.fps, this.totalPercent);
    }

    public static boolean active() {
        return active;
    }

    public static void begin(String name, int phase) {
        startedName = name;
        startedPhase = phase;
        startedAt = System.nanoTime();
    }

    public static void end() {
        if (startedName == null) {
            return;
        }
        long spent = System.nanoTime() - startedAt;
        long[] e = RUNNING.computeIfAbsent(startedName, k -> new long[6]);
        e[startedPhase] += spent;
        e[3 + startedPhase]++;
        startedName = null;
    }

    private void closeWindow() {
        long now = System.nanoTime();
        long window = now - windowStart;
        if (window < 1_000_000_000L) {
            return;
        }
        List<Row> out = new ArrayList<>();
        double total = 0;
        for (Map.Entry<String, long[]> e : RUNNING.entrySet()) {
            long[] v = e.getValue();
            Row r = new Row(e.getKey(), new long[]{v[0], v[1], v[2]}, new int[]{(int) v[3], (int) v[4], (int) v[5]});
            total += r.percent(window);
            out.add(r);
        }
        out.sort((a, b) -> Double.compare(b.percent(window), a.percent(window)));
        this.rows = out;
        this.totalPercent = total;
        RUNNING.clear();
        windowStart = now;
    }

    @Override
    public void onTick() {
        this.closeWindow();
    }

    @Override
    public void onRender2D(class_332 g, float delta) {
        this.frames++;
        long now = System.nanoTime();
        if (now - this.frameWindow >= 1_000_000_000L) {
            this.fps = this.frames;
            this.frames = 0;
            this.frameWindow = now;
        }
        if (!this.overlay.get()) {
            return;
        }
        List<Row> list = this.rows;
        int px = this.x.get();
        int py = this.y.get();
        long window = 1_000_000_000L;
        int count = Math.min(list.size(), this.lines.get());
        int width = 190;
        Gfx.rect(g, px - 4, py - 4, width + 8, 14 + count * 11 + 4, 4, 0xB0101014);
        Gfx.text(g, String.format(Locale.ROOT, "Modules take %.1f%% · %d fps", this.totalPercent, this.fps), px, py, Theme.accent());
        for (int i = 0; i < count; i++) {
            Row r = list.get(i);
            double pct = r.percent(window);
            int color = pct > 5 ? 0xFFFF6A6A : pct > 1.5 ? 0xFFFFD24D : 0xFFE0E0E0;
            Gfx.text(g, Gfx.trim(r.name, 100), px, py + 13 + i * 11, color);
            Gfx.text(g, String.format(Locale.ROOT, "%.1f%%", pct), px + 104, py + 13 + i * 11, color);
            Gfx.text(g, detail(r), px + 136, py + 13 + i * 11, 0xFF9AA0AE);
        }
    }

    private static String detail(Row r) {
        StringBuilder sb = new StringBuilder();
        String[] names = {"t", "2d", "3d"};
        for (int i = 0; i < 3; i++) {
            if (r.calls[i] > 0 && r.nanos[i] > 0) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(names[i]).append(String.format(Locale.ROOT, "%.2f", r.nanos[i] / 1.0E6 / r.calls[i]));
            }
        }
        return sb.toString();
    }

    private void report() {
        List<Row> list = this.rows;
        if (list.isEmpty()) {
            Notifications.info(this.name(), "Nothing measured yet, wait a second");
            return;
        }
        StringBuilder log = new StringBuilder("[DIHClient] Frame Profiler (share of time, ms per call: t = tick, 2d, 3d):");
        for (int i = 0; i < Math.min(15, list.size()); i++) {
            Row r = list.get(i);
            log.append(String.format(Locale.ROOT, "%n  %-28s %5.1f%%  %s", r.name, r.percent(1_000_000_000L), detail(r)));
            if (i < 5) {
                Notifications.info(this.name(), String.format(Locale.ROOT, "%s %.1f%% %s", r.name, r.percent(1_000_000_000L), detail(r)));
            }
        }
        DIHClient.LOG.info(log.toString());
    }
}
