package dev.dihclient.port.dao;

import dev.dihclient.module.Category;
import dev.dihclient.scan.ChunkMarkModule;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.class_1923;
import net.minecraft.class_1944;
import net.minecraft.class_2680;
import net.minecraft.class_2804;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_3562;
import net.minecraft.class_4076;

public class DaoFinder extends ChunkMarkModule {
    private static final int DELAY_FIRST = 30;
    private static final int DELAY_SECOND = 140;

    public final IntSetting minY = this.integer("Min Y", "Lowest height that is checked.", -64, -64, 320);
    public final IntSetting maxY = this.integer("Max Y", "Highest height that is checked. 0 = only the deepslate layer.", 0, -64, 320);
    public final IntSetting sensitivity = this.integer("Sensitivity", "Hidden light sources needed in one chunk to mark it.", 3, 1, 50);
    public final IntSetting maxChunks = this.integer("Max Chunks", "Most chunks marked at the same time (strongest first).", 10, 1, 100);
    public final IntSetting speed = this.integer("Scan Speed", "Chunks checked per tick.", 2, 1, 8);
    public final IntSetting spawnRadius = this.integer("Ignore Spawn", "Chunks around 0, 0 that are never marked (public builds). 0 = off.", 0, 0, 200);
    public final IntSetting keep = this.integer("Keep Range", "Marks further away than this many chunks are forgotten.", 48, 8, 200);
    public final EnumSetting<Marker> marker = this.mode("Marker", "Box: the normal coloured marker · Friend: a picture of your friend stands above the chunk instead.", Marker.FRIEND);
    public final ColorSetting color = this.color("Color", "Marker colour.", 0xFFB44CFF);

    private final Map<Long, Integer> marks = new HashMap<>();
    private final Map<Long, int[]> found = new HashMap<>();
    private final Queue<Long> incoming = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<long[]> due = new ArrayDeque<>();
    private final Map<Long, Integer> best = new HashMap<>();
    private int ticks;

    public enum Marker {
        BOX,
        FRIEND
    }

    @Override
    protected boolean showsFriend() {
        return this.marker.get() == Marker.FRIEND;
    }

    public DaoFinder() {
        super("Dao Finder", Category.BASEFINDING, "Finds hidden bases by their light: a chunk can show solid deepslate while the light data still tells that a lamp or torch burns inside.");
        this.opacity.set(120);
        this.action("Clear", "Forgets all marks and checks the loaded chunks again.", this::reset);
    }

    private void reset() {
        this.marks.clear();
        this.found.clear();
        this.best.clear();
        this.incoming.clear();
        this.due.clear();
        if (mc.field_1687 != null && mc.field_1724 != null) {
            class_1923 me = mc.field_1724.method_31476();
            int r = Math.min(24, mc.field_1690.method_42503().method_41753() + 1);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    this.schedule(me.field_9181 + dx, me.field_9180 + dz, 0);
                }
            }
        }
    }

    private void schedule(int cx, int cz, int delay) {
        this.due.add(new long[] {this.ticks + delay, class_1923.method_8331(cx, cz)});
    }

    @Override
    protected void onEnable() {
        this.reset();
    }

    @Override
    public void onWorldChange() {
        this.marks.clear();
        this.found.clear();
        this.best.clear();
        this.incoming.clear();
        this.due.clear();
    }

    @Override
    public void onChunkLoaded(int cx, int cz) {
        this.incoming.add(class_1923.method_8331(cx, cz));
    }

    @Override
    public Map<Long, Integer> chunkMarks() {
        return this.marks;
    }

    @Override
    protected double chunkY(long key) {
        int[] f = this.found.get(key);
        return f == null ? Double.NaN : f[1];
    }

    @Override
    protected float chunkIntensity(long key) {
        int[] f = this.found.get(key);
        return f == null ? 0.5F : Math.min(1.0F, f[0] / (this.sensitivity.get() * 4.0F));
    }

    @Override
    protected String chunkLabel(long key) {
        return this.found.containsKey(key) ? "Hidden light" : null;
    }

    @Override
    protected String chunkSubLabel(long key) {
        int[] f = this.found.get(key);
        return f == null ? null : f[0] + " lit blocks in rock · Y " + f[1];
    }

    @Override
    public void onTick() {
        if (mc.field_1687 == null || mc.field_1724 == null) {
            return;
        }
        this.ticks++;
        Long k;
        while ((k = this.incoming.poll()) != null) {
            int cx = class_1923.method_8325(k);
            int cz = class_1923.method_8332(k);
            this.schedule(cx, cz, DELAY_FIRST);
            this.schedule(cx, cz, DELAY_SECOND);
        }
        int budget = this.speed.get();
        while (budget > 0 && !this.due.isEmpty() && this.due.peekFirst()[0] <= this.ticks) {
            long[] job = this.due.pollFirst();
            int cx = class_1923.method_8325(job[1]);
            int cz = class_1923.method_8332(job[1]);
            this.scan(cx, cz);
            budget--;
        }
        if (this.ticks % 20 == 0) {
            this.refresh();
        }
    }

    private void scan(int cx, int cz) {
        class_2818 chunk = mc.field_1687.method_2935().method_21730(cx, cz);
        if (chunk == null) {
            return;
        }
        long key = class_1923.method_8331(cx, cz);
        int spawn = this.spawnRadius.get();
        if (spawn > 0 && Math.abs(cx) <= spawn && Math.abs(cz) <= spawn) {
            this.best.remove(key);
            this.found.remove(key);
            return;
        }
        class_3562 view = mc.field_1687.method_22336().method_15562(class_1944.field_9282);
        class_2826[] sections = chunk.method_12006();
        int bottom = chunk.method_32891();
        int lo = this.minY.get();
        int hi = this.maxY.get();
        int count = 0;
        long ySum = 0;
        for (int i = 0; i < sections.length; i++) {
            class_2826 sec = sections[i];
            int sy = bottom + i;
            int y0 = sy << 4;
            if (sec == null || sec.method_38292() || y0 + 15 < lo || y0 > hi) {
                continue;
            }
            class_2804 arr = view.method_15544(class_4076.method_18676(cx, sy, cz));
            if (arr == null || arr.method_51380()) {
                continue;
            }
            byte[] b = arr.method_12137();
            if (b == null || b.length < 2048) {
                continue;
            }
            for (int idx = 0; idx < 4096; idx++) {
                int v = b[idx >> 1] >> ((idx & 1) << 2) & 15;
                if (v == 0) {
                    continue;
                }
                int y = idx >> 8;
                int wy = y0 + y;
                if (wy < lo || wy > hi) {
                    continue;
                }
                class_2680 state = sec.method_12254(idx & 15, y, idx >> 4 & 15);
                if (state.method_26225()) {
                    count++;
                    ySum += wy;
                }
            }
        }
        if (count >= this.sensitivity.get()) {
            int prev = this.best.getOrDefault(key, 0);
            if (count >= prev) {
                this.best.put(key, count);
                this.found.put(key, new int[] {count, (int) (ySum / count)});
            }
        } else if (count == 0) {
            this.best.remove(key);
            this.found.remove(key);
        }
    }

    private void refresh() {
        class_1923 me = mc.field_1724.method_31476();
        int range = this.keep.get();
        Iterator<Long> it = this.found.keySet().iterator();
        while (it.hasNext()) {
            long k = it.next();
            if (Math.abs(class_1923.method_8325(k) - me.field_9181) > range || Math.abs(class_1923.method_8332(k) - me.field_9180) > range) {
                it.remove();
                this.best.remove(k);
            }
        }
        java.util.List<Map.Entry<Long, int[]>> list = new java.util.ArrayList<>(this.found.entrySet());
        list.sort((a, b) -> {
            int c = Integer.compare(b.getValue()[0], a.getValue()[0]);
            if (c != 0) {
                return c;
            }
            return Double.compare(dist(a.getKey(), me), dist(b.getKey(), me));
        });
        Map<Long, Integer> fresh = new HashMap<>();
        int limit = this.maxChunks.get();
        for (Map.Entry<Long, int[]> e : list) {
            if (fresh.size() >= limit) {
                break;
            }
            fresh.put(e.getKey(), this.color.get());
            if (!this.marks.containsKey(e.getKey())) {
                this.announce(e.getKey(), "Hidden light: " + e.getValue()[0] + " lit blocks in rock");
            }
        }
        this.marks.clear();
        this.marks.putAll(fresh);
    }

    private static double dist(long k, class_1923 me) {
        double dx = class_1923.method_8325(k) - me.field_9181;
        double dz = class_1923.method_8332(k) - me.field_9180;
        return dx * dx + dz * dz;
    }
}
