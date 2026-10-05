package dev.dihclient.modules.basefinding;

import dev.dihclient.module.Category;
import dev.dihclient.render.Render3D;
import dev.dihclient.scan.ChunkMarkModule;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.IntSetting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import net.minecraft.class_1923;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2769;
import net.minecraft.class_2818;
import net.minecraft.class_2826;

public class SusChunkFinder
extends ChunkMarkModule {
    public static volatile Supplier<Map<Long, float[]>> amethystSource;
    public final IntSetting simulationDistance = this.integer("Simulation Distance", "Chunk radius around a growing chunk that is treated as simulated (those chunks are never marked).", 4, 2, 16);
    public final IntSetting sensitivity = this.integer("Sensitivity", "Minimum grown things in range needed to mark a chunk as sus.", 3, 1, 20);
    public final IntSetting maxChunks = this.integer("Max Chunks", "Most sus chunks marked at the same time - only the strongest ones are shown (closest first on ties).", 10, 1, 100);
    public final BoolSetting kelp = (BoolSetting)this.bool("Kelp", "Kelp blocks.", true).legacy("sus.kelp");
    public final BoolSetting caveVines = this.bool("Cave Vines", "Cave vines.", true);
    public final BoolSetting vines = (BoolSetting)this.bool("Vines", "Vines.", true).legacy("sus.vines");
    public final BoolSetting amethyst = (BoolSetting)this.bool("Amethyst", "Amethyst buds / clusters (also fed by the Amethyst Bypass geode data).", true).legacy("sus.amethyst");
    public final BoolSetting bamboo = this.bool("Bamboo", "Bamboo.", true);
    public final BoolSetting beeNest = this.bool("Bee Nest", "Bee nests with honey mean someone loaded the chunk.", true);
    public final BoolSetting rotatedDeepslate = this.bool("Rotated Deepslate", "Outlines rotated deepslate buried between Y 0-60.", true);
    public final BoolSetting maybe = this.bool("Show Maybe", "Chunks with at least half the needed evidence get a dim mark.", false);
    public final ColorSetting color = this.color("Color", "Marker colour.", -61424);
    public final ColorSetting maybeColor = (ColorSetting)this.color("Maybe Color", "Colour of \"maybe\" chunks.", -26064).visibleWhen(this.maybe::get);
    public final IntSetting speed = this.integer("Scan Speed", "Chunks scanned per tick.", 3, 1, 16);
    private final Map<Long, Integer> chunkHeatmap = new HashMap<Long, Integer>();
    private final Map<Long, Integer> trackedChunks = new HashMap<Long, Integer>();
    private final Set<Long> loadedChunkPositions = new HashSet<Long>();
    private final Map<Long, List<class_2338>> chunkDeepslateMap = new HashMap<Long, List<class_2338>>();
    private final Map<Long, Integer> chunkGrowthCounts = new HashMap<Long, Integer>();
    private final Map<Long, Boolean> chunkHasFullyGrown = new HashMap<Long, Boolean>();
    private final Map<Long, Integer> effective = new HashMap<Long, Integer>();
    private final Map<Long, Integer> marks = new HashMap<Long, Integer>();
    private final Map<Long, Integer> counts = new HashMap<Long, Integer>();
    private final Set<Long> announced = new HashSet<Long>();
    private final Queue<Long> incoming = new ConcurrentLinkedQueue<Long>();
    private final ArrayDeque<Long> pending = new ArrayDeque<Long>();
    private final Set<Long> queued = new HashSet<Long>();
    private int ticks;

    public SusChunkFinder() {
        super("Sus ChunkFinder", Category.BASEFINDING, "Finds probable base locations using plant/amethyst growth and rotated deepslate.");
        this.opacity.set(80);
        this.friend.set(true);
        for (BoolSetting boolSetting : List.of(this.kelp, this.caveVines, this.vines, this.amethyst, this.bamboo, this.beeNest, this.rotatedDeepslate)) {
            boolSetting.onChange(this::rescan);
        }
        this.simulationDistance.onChange(this::rescan);
        this.action("Clear", "Removes all marks and scans again.", this::rescan);
    }

    private static long key(int n, int n2) {
        return class_1923.method_8331((int)n, (int)n2);
    }

    private void reset() {
        this.chunkHeatmap.clear();
        this.trackedChunks.clear();
        this.loadedChunkPositions.clear();
        this.chunkDeepslateMap.clear();
        this.chunkGrowthCounts.clear();
        this.chunkHasFullyGrown.clear();
        this.effective.clear();
        this.marks.clear();
        this.counts.clear();
        this.announced.clear();
        this.incoming.clear();
        this.pending.clear();
        this.queued.clear();
    }

    private void rescan() {
        this.reset();
        if (SusChunkFinder.mc.field_1724 == null || SusChunkFinder.mc.field_1687 == null) {
            return;
        }
        int n = (Integer)SusChunkFinder.mc.field_1690.method_42503().method_41753() + 1;
        class_1923 class_19232 = SusChunkFinder.mc.field_1724.method_31476();
        for (int i = -n; i <= n; ++i) {
            for (int j = -n; j <= n; ++j) {
                if (SusChunkFinder.mc.field_1687.method_2935().method_21730(class_19232.field_9181 + i, class_19232.field_9180 + j) == null) continue;
                this.enqueue(class_19232.field_9181 + i, class_19232.field_9180 + j);
            }
        }
    }

    private void enqueue(int n, int n2) {
        long l = SusChunkFinder.key(n, n2);
        if (this.queued.add(l)) {
            this.pending.add(l);
        }
    }

    @Override
    protected void onEnable() {
        this.rescan();
    }

    @Override
    public void onWorldChange() {
        this.reset();
    }

    @Override
    public void onChunkLoaded(int n, int n2) {
        this.incoming.add(SusChunkFinder.key(n, n2));
    }

    @Override
    public Map<Long, Integer> chunkMarks() {
        return this.marks;
    }

    @Override
    public void onTick() {
        Long l;
        if (SusChunkFinder.mc.field_1687 == null || SusChunkFinder.mc.field_1724 == null) {
            return;
        }
        while ((l = this.incoming.poll()) != null) {
            this.enqueue(class_1923.method_8325((long)l), class_1923.method_8332((long)l));
        }
        for (int i = 0; i < (Integer)this.speed.get() && !this.pending.isEmpty(); ++i) {
            long l2 = this.pending.poll();
            this.queued.remove(l2);
            int n = class_1923.method_8325((long)l2);
            int n2 = class_1923.method_8332((long)l2);
            class_2818 class_28182 = SusChunkFinder.mc.field_1687.method_2935().method_21730(n, n2);
            if (class_28182 == null) continue;
            this.loadedChunkPositions.add(l2);
            this.updateChunkData(n, n2, class_28182);
        }
        if (this.ticks++ % 10 == 0) {
            this.refreshMarks();
        }
    }

    private void spreadHeat(int n, int n2, int n3) {
        int n4 = (Integer)this.simulationDistance.get();
        for (int i = -n4; i <= n4; ++i) {
            for (int j = -n4; j <= n4; ++j) {
                long l = SusChunkFinder.key(n + i, n2 + j);
                int n5 = this.chunkHeatmap.getOrDefault(l, 0) + n3;
                if (n5 <= 0) {
                    this.chunkHeatmap.remove(l);
                    continue;
                }
                this.chunkHeatmap.put(l, n5);
            }
        }
    }

    private void spreadTracked(int n, int n2, int n3) {
        int n4 = (Integer)this.simulationDistance.get();
        for (int i = -n4; i <= n4; ++i) {
            for (int j = -n4; j <= n4; ++j) {
                long l = SusChunkFinder.key(n + i, n2 + j);
                int n5 = this.trackedChunks.getOrDefault(l, 0) + n3;
                if (n5 <= 0) {
                    this.trackedChunks.remove(l);
                    continue;
                }
                this.trackedChunks.put(l, n5);
            }
        }
    }

    private void updateChunkData(int n, int n2, class_2818 class_28182) {
        long l = SusChunkFinder.key(n, n2);
        int n3 = this.chunkGrowthCounts.getOrDefault(l, 0);
        boolean bl = Boolean.TRUE.equals(this.chunkHasFullyGrown.get(l));
        this.chunkDeepslateMap.remove(l);
        if (n3 > 0) {
            this.spreadHeat(n, n2, -n3);
        }
        if (bl) {
            this.spreadTracked(n, n2, -1);
        }
        ChunkScan chunkScan = this.scanChunkBlocks(n, n2, class_28182);
        this.chunkGrowthCounts.put(l, chunkScan.notGrown);
        this.chunkHasFullyGrown.put(l, chunkScan.hasGrown);
        if (chunkScan.hasGrown) {
            this.spreadTracked(n, n2, 1);
        }
        if (chunkScan.notGrown > 0) {
            this.spreadHeat(n, n2, chunkScan.notGrown);
        }
        if (!chunkScan.deepslate.isEmpty()) {
            this.chunkDeepslateMap.put(l, chunkScan.deepslate);
        }
    }

    private boolean relevant(class_2680 class_26802) {
        return (Boolean)this.kelp.get() != false && class_26802.method_27852(class_2246.field_9993) || (Boolean)this.caveVines.get() != false && class_26802.method_27852(class_2246.field_28675) || (Boolean)this.vines.get() != false && class_26802.method_27852(class_2246.field_10597) || (Boolean)this.amethyst.get() != false && (SusChunkFinder.isAmethystBud(class_26802) || SusChunkFinder.isAmethystCluster(class_26802)) || (Boolean)this.bamboo.get() != false && class_26802.method_27852(class_2246.field_10211) || (Boolean)this.beeNest.get() != false && class_26802.method_27852(class_2246.field_20421) || (Boolean)this.rotatedDeepslate.get() != false && class_26802.method_27852(class_2246.field_28888);
    }

    private ChunkScan scanChunkBlocks(int n, int n2, class_2818 class_28182) {
        int n3;
        class_2826[] class_2826Array = class_28182.method_12006();
        int n4 = class_28182.method_32891();
        int n5 = n << 4;
        int n6 = n2 << 4;
        int n7 = 0;
        int n8 = 0;
        int n9 = 0;
        int n10 = 0;
        int n11 = 0;
        int n12 = 0;
        int n13 = 0;
        int n14 = 0;
        int n15 = 0;
        int n16 = 0;
        int n17 = 0;
        int n18 = 0;
        boolean bl = (Boolean)this.amethyst.get() != false && this.isDonutFolia();
        boolean bl2 = false;
        boolean bl3 = false;
        ArrayList<class_2338> arrayList = new ArrayList<class_2338>();
        for (n3 = 0; n3 < class_2826Array.length; ++n3) {
            class_2826 class_28262 = class_2826Array[n3];
            if (class_28262 == null || class_28262.method_38292()) continue;
            int n19 = n4 + n3 << 4;
            if (bl) {
                if (!bl2 && class_28262.method_19523(SusChunkFinder::isAmethystBud)) {
                    bl2 = true;
                }
                if (!bl3 && class_28262.method_19523(SusChunkFinder::isAmethystCluster)) {
                    bl3 = true;
                }
            }
            if (!class_28262.method_19523(this::relevant)) continue;
            for (int i = 0; i < 16; ++i) {
                for (int j = 0; j < 16; ++j) {
                    for (int k = 0; k < 16; ++k) {
                        class_2680 belowState;
                        class_2680 aboveState;
                        boolean bl4;
                        class_2338 class_23384;
                        int n20;
                        class_2680 class_26802 = class_28262.method_12254(i, j, k);
                        if (class_26802.method_26215()) continue;
                        int n21 = n5 + i;
                        int n22 = n19 + j;
                        int n23 = n6 + k;
                        if (((Boolean)this.kelp.get()).booleanValue() && class_26802.method_27852(class_2246.field_9993) && class_26802.method_28498((class_2769)class_2741.field_12517)) {
                            n20 = (Integer)class_26802.method_11654((class_2769)class_2741.field_12517);
                            class_23384 = new class_2338(n21, n22 + 1, n23);
                            bl4 = SusChunkFinder.mc.field_1687.method_8320(class_23384).method_27852(class_2246.field_10382);
                            if (n20 != 25 && bl4) {
                                ++n7;
                            } else {
                                ++n8;
                            }
                        }
                        if (((Boolean)this.caveVines.get()).booleanValue() && class_26802.method_27852(class_2246.field_28675) && class_26802.method_28498((class_2769)class_2741.field_12517)) {
                            n20 = (Integer)class_26802.method_11654((class_2769)class_2741.field_12517);
                            class_23384 = new class_2338(n21, n22 - 1, n23);
                            bl4 = SusChunkFinder.mc.field_1687.method_8320(class_23384).method_26215();
                            if (n20 != 25 && bl4) {
                                ++n9;
                            } else {
                                ++n10;
                            }
                        }
                        if (((Boolean)this.vines.get()).booleanValue() && class_26802.method_27852(class_2246.field_10597) && !(belowState = SusChunkFinder.mc.field_1687.method_8320(new class_2338(n21, n22, n23).method_10074())).method_27852(class_2246.field_10597)) {
                            if (!belowState.method_26215()) {
                                ++n12;
                            } else if (((Boolean)class_26802.method_11654((class_2769)class_2741.field_12489)).booleanValue() || ((Boolean)class_26802.method_11654((class_2769)class_2741.field_12487)).booleanValue() || ((Boolean)class_26802.method_11654((class_2769)class_2741.field_12540)).booleanValue() || ((Boolean)class_26802.method_11654((class_2769)class_2741.field_12527)).booleanValue()) {
                                ++n11;
                            } else {
                                ++n12;
                            }
                        }
                        if (((Boolean)this.amethyst.get()).booleanValue() && !bl) {
                            if (class_26802.method_27852(class_2246.field_27161)) {
                                ++n14;
                            } else if ((class_26802.method_27852(class_2246.field_27164) || class_26802.method_27852(class_2246.field_27163) || class_26802.method_27852(class_2246.field_27162)) && class_26802.method_28498((class_2769)class_2741.field_12525)) {
                                class_2350 class_23502 = (class_2350)class_26802.method_11654((class_2769)class_2741.field_12525);
                                class_23384 = new class_2338(n21, n22, n23).method_10093(class_23502.method_10153());
                                if (SusChunkFinder.mc.field_1687.method_8320(class_23384).method_27852(class_2246.field_27160)) {
                                    ++n13;
                                } else {
                                    ++n14;
                                }
                            }
                        }
                        if (((Boolean)this.bamboo.get()).booleanValue() && class_26802.method_27852(class_2246.field_10211) && class_26802.method_28498((class_2769)class_2741.field_12549) && !(aboveState = SusChunkFinder.mc.field_1687.method_8320(new class_2338(n21, n22 + 1, n23))).method_27852(class_2246.field_10211)) {
                            if ((Integer)class_26802.method_11654((class_2769)class_2741.field_12549) == 1) {
                                ++n16;
                            } else if (aboveState.method_26215()) {
                                ++n15;
                            }
                        }
                        if (((Boolean)this.beeNest.get()).booleanValue() && class_26802.method_27852(class_2246.field_20421) && class_26802.method_28498((class_2769)class_2741.field_20432)) {
                            if ((Integer)class_26802.method_11654((class_2769)class_2741.field_20432) == 5) {
                                ++n18;
                            } else {
                                ++n17;
                            }
                        }
                        if (!((Boolean)this.rotatedDeepslate.get()).booleanValue() || !class_26802.method_27852(class_2246.field_28888) || !class_26802.method_28498((class_2769)class_2741.field_12496) || class_26802.method_11654((class_2769)class_2741.field_12496) == class_2350.class_2351.field_11052 || n22 < 0 || n22 > 60) continue;
                        class_2338 class_23385 = new class_2338(n21, n22, n23);
                        boolean bl5 = true;
                        for (class_2350 class_23503 : class_2350.values()) {
                            if (!SusChunkFinder.mc.field_1687.method_8320(class_23385.method_10093(class_23503)).method_26215()) continue;
                            bl5 = false;
                            break;
                        }
                        if (!bl5) continue;
                        arrayList.add(class_23385);
                    }
                }
            }
        }
        if (bl) {
            if (bl2) {
                ++n13;
            } else if (bl3) {
                ++n14;
            }
        }
        n3 = ((Boolean)this.kelp.get() != false ? n7 : 0) + ((Boolean)this.caveVines.get() != false ? n9 : 0) + ((Boolean)this.vines.get() != false ? n11 : 0) + ((Boolean)this.amethyst.get() != false ? n13 : 0) + ((Boolean)this.bamboo.get() != false ? n15 : 0) + ((Boolean)this.beeNest.get() != false ? n17 : 0);
        int n24 = ((Boolean)this.kelp.get() != false ? n8 : 0) + ((Boolean)this.caveVines.get() != false ? n10 : 0) + ((Boolean)this.vines.get() != false ? n12 : 0) + ((Boolean)this.amethyst.get() != false ? n14 : 0) + ((Boolean)this.bamboo.get() != false ? n16 : 0) + ((Boolean)this.beeNest.get() != false ? n18 : 0);
        return new ChunkScan(n24, n3 > 0, arrayList);
    }

    private void refreshMarks() {
        int n;
        long l;
        int n2;
        Map<Long, float[]> map;
        this.effective.clear();
        this.effective.putAll(this.chunkHeatmap);
        Supplier<Map<Long, float[]>> supplier = amethystSource;
        if (supplier != null && ((Boolean)this.amethyst.get()).booleanValue() && (map = supplier.get()) != null) {
            n2 = (Integer)this.simulationDistance.get();
            for (Map.Entry<Long, float[]> entry3 : map.entrySet()) {
                int n3 = (int)Math.ceil(entry3.getValue()[0]);
                int n4 = class_1923.method_8325((long)entry3.getKey());
                int n5 = class_1923.method_8332((long)entry3.getKey());
                for (int i = -n2; i <= n2; ++i) {
                    for (int j = -n2; j <= n2; ++j) {
                        this.effective.merge(SusChunkFinder.key(n4 + i, n5 + j), n3, Integer::sum);
                    }
                }
            }
        }
        int n6 = (Integer)this.sensitivity.get();
        n2 = Math.max(1, n6 / 2);
        boolean bl = (Boolean)this.maybe.get();
        double d = SusChunkFinder.mc.field_1724.method_23317();
        double d2 = SusChunkFinder.mc.field_1724.method_23321();
        ArrayList<Map.Entry<Long, Integer>> arrayList = new ArrayList<Map.Entry<Long, Integer>>();
        for (Map.Entry<Long, Integer> entry4 : this.effective.entrySet()) {
            int n7 = entry4.getValue();
            if (n7 < (bl ? n2 : n6)) continue;
            l = entry4.getKey();
            n = class_1923.method_8325((long)l);
            int n8 = class_1923.method_8332((long)l);
            if (this.trackedChunks.containsKey(l) || !SusChunkFinder.mc.field_1687.method_8393(n, n8)) continue;
            int n9 = 0;
            for (int i = -1; i <= 1; ++i) {
                for (int j = -1; j <= 1; ++j) {
                    if (i == 0 && j == 0 || !this.loadedChunkPositions.contains(SusChunkFinder.key(n + i, n8 + j))) continue;
                    ++n9;
                }
            }
            if (n9 < 3) continue;
            arrayList.add(entry4);
        }
        arrayList.sort((entry, entry2) -> {
            int cmp = Integer.compare((Integer)entry2.getValue(), (Integer)entry.getValue());
            return cmp != 0 ? cmp : Double.compare(SusChunkFinder.dist((Long)entry.getKey(), d, d2), SusChunkFinder.dist((Long)entry2.getKey(), d, d2));
        });
        this.marks.clear();
        this.counts.clear();
        int n10 = (Integer)this.maxChunks.get();
        for (Map.Entry<Long, Integer> entry5 : arrayList) {
            if (this.marks.size() >= n10) break;
            l = (Long)entry5.getKey();
            n = (Integer)entry5.getValue();
            this.counts.put(l, n);
            if (n >= n6) {
                this.marks.put(l, n >= n6 * 2 ? SusChunkFinder.hotter((Integer)this.color.get()) : (Integer)this.color.get());
                if (!this.announced.add(l)) continue;
                this.announce(l, "Suspicious chunk: " + n + " grown things in range");
                continue;
            }
            this.marks.put(l, (Integer)this.maybeColor.get());
        }
    }

    private static double dist(long l, double d, double d2) {
        double d3 = (double)((class_1923.method_8325((long)l) << 4) + 8) - d;
        double d4 = (double)((class_1923.method_8332((long)l) << 4) + 8) - d2;
        return d3 * d3 + d4 * d4;
    }

    private static int hotter(int n) {
        int n2 = n >> 16 & 0xFF;
        int n3 = n >> 8 & 0xFF;
        int n4 = n & 0xFF;
        return 0xFF000000 | Math.min(255, n2 + 40) << 16 | (int)((double)n3 * 0.55) << 8 | (int)((double)n4 * 0.55);
    }

    @Override
    protected double chunkY(long l) {
        return Double.NaN;
    }

    @Override
    protected float chunkIntensity(long l) {
        Integer n = this.counts.get(l);
        return n == null ? 0.3f : Math.min(1.0f, (float)n.intValue() / ((float)((Integer)this.sensitivity.get()).intValue() * 2.0f));
    }

    @Override
    protected String chunkSubLabel(long l) {
        Integer n = this.counts.get(l);
        return n == null ? null : n + " grown things in range";
    }

    @Override
    protected String chunkLabel(long l) {
        Integer n = this.counts.get(l);
        return n == null ? null : (n >= (Integer)this.sensitivity.get() ? "SUS " : "maybe ") + n;
    }

    @Override
    public void onRender3D(Render3D render3D) {
        super.onRender3D(render3D);
        if (SusChunkFinder.mc.field_1687 == null || !((Boolean)this.rotatedDeepslate.get()).booleanValue() || this.chunkDeepslateMap.isEmpty()) {
            return;
        }
        int n = -16711681;
        int n2 = (Integer)this.opacity.get();
        for (Map.Entry<Long, List<class_2338>> entry : this.chunkDeepslateMap.entrySet()) {
            long l = entry.getKey();
            if (!SusChunkFinder.mc.field_1687.method_8393(class_1923.method_8325((long)l), class_1923.method_8332((long)l))) continue;
            for (class_2338 class_23382 : entry.getValue()) {
                render3D.box(new class_238(class_23382), n, n2, true);
            }
        }
    }

    @Override
    public List<String> details() {
        ArrayList<String> arrayList = new ArrayList<String>();
        int n = 0;
        for (int n2 : this.counts.values()) {
            if (n2 < (Integer)this.sensitivity.get()) continue;
            ++n;
        }
        int n3 = 0;
        for (List<class_2338> list : this.chunkDeepslateMap.values()) {
            n3 += list.size();
        }
        arrayList.add("Suspicious chunks: " + n + " - sensitivity " + String.valueOf(this.sensitivity.get()) + " - max " + String.valueOf(this.maxChunks.get()));
        arrayList.add("Rotated deepslate blocks: " + n3 + " - queued chunks: " + this.pending.size());
        return arrayList;
    }

    private boolean isDonutFolia() {
        if (mc.method_1562() == null) {
            return false;
        }
        String string = mc.method_1562().method_52790();
        return string != null && string.contains("DonutFolia");
    }

    private static boolean isAmethystCluster(class_2680 class_26802) {
        return class_26802.method_26204() == class_2246.field_27161;
    }

    private static boolean isAmethystBud(class_2680 class_26802) {
        return class_26802.method_26204() == class_2246.field_27164 || class_26802.method_26204() == class_2246.field_27163 || class_26802.method_26204() == class_2246.field_27162;
    }

    private record ChunkScan(int notGrown, boolean hasGrown, List<class_2338> deepslate) {
    }
}
