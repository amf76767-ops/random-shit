package dev.dihclient.port.donutd;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.port.PacketBus;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayDeque;
import net.minecraft.class_1923;
import net.minecraft.class_1944;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2626;
import net.minecraft.class_2637;
import net.minecraft.class_2672;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_2841;
import net.minecraft.class_638;
import net.minecraft.class_2338.class_2339;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Shows amethyst geodes that anti-xray hides, in three ways:
 * <ul>
 *   <li>Bypass: the chunk data still lists which blocks a section holds (its palette), anti-xray only scrambles where they are;
 *       a section whose palette has an amethyst bud has a geode, marked at an open block next to the geode.</li>
 *   <li>Light: the server sends real block light; an open block with the light a bud gives (1, 2, 4 or 5), no brighter
 *       neighbour and a geode block beside it is a hidden bud.</li>
 *   <li>ANBS+ Scan: {@link GeodeGlowScan}, the glow of the geode seen in the light data.</li>
 * </ul>
 * Everything is read from what the client already received; nothing is sent. Scans are cut to 2 ms per tick.
 */
public final class AmethystBypassModule extends Module {
    /** The setting values; the label is what the GUI shows. */
    public enum Method {
        BYPASS("Bypass"), LIGHT("Light"), ANBS_SCAN("ANBS+ Scan");

        final String label;

        Method(String label) {
            this.label = label;
        }
    }

    private static final int COLOR = -3636481;
    private static final int RESCAN_TICKS = 20;
    private static final long BUDGET_NANOS = 2_000_000L;
    private static final int MAX_GLOW_MARKS = 200;

    public final EnumSetting<Method> method = this.add(new EnumSetting<Method>("Method",
            "Bypass: reads the block palette of the chunk data · Light: reads the block light of the hidden buds · ANBS+ Scan: reads the glow of the geode.",
            Method.BYPASS) {
        @Override
        public String displayValue() {
            return this.get().label;
        }
    });
    public final IntSetting minCells = this.integer("Min Cells", "ANBS+ Scan: glow cells a chunk needs before it is marked.", 12, 1, 100)
            .visibleWhen(() -> this.method.is(Method.ANBS_SCAN));
    public final BoolSetting tracer = this.bool("Tracer", "Draws lines to the marks.", false);

    private final ArrayDeque<class_1923> queue = new ArrayDeque<>();
    private final LongOpenHashSet found = new LongOpenHashSet();
    private final LongOpenHashSet bypassScanned = new LongOpenHashSet();
    private final Long2ObjectOpenHashMap<class_2338> bypassMarkers = new Long2ObjectOpenHashMap<>();
    private final GeodeGlowScan glow = new GeodeGlowScan();
    private final LongArrayList glowCells = new LongArrayList();
    private final PacketBus.Listener appliedListener = this::onPacketApplied;
    private long[] hits = new long[0];
    private int tick;
    private class_638 lastWorld;
    private boolean broken;

    public AmethystBypassModule() {
        super("Amethyst Bypass", Category.BASEFINDING, "Shows amethyst geodes hidden by anti-xray.");
        this.method.onChange(this::restart);
    }

    @Override
    protected void onEnable() {
        this.broken = false;
        this.restart();
        PacketBus.applied(this.appliedListener);
    }

    @Override
    protected void onDisable() {
        PacketBus.remove(this.appliedListener);
        this.restart();
    }

    @Override
    public void onWorldChange() {
        this.restart();
    }

    @Override
    public String getInfo() {
        return Integer.toString(this.hits.length);
    }

    private void restart() {
        this.queue.clear();
        this.found.clear();
        this.bypassScanned.clear();
        this.bypassMarkers.clear();
        this.hits = new long[0];
        this.tick = 0;
    }

    @Override
    public void onTick() {
        class_638 level = mc.field_1687;
        if (level != this.lastWorld) {
            // new connection or dimension: the remembered chunks belong to the old world
            this.restart();
            this.lastWorld = level;
        }
        if (this.broken || level == null || mc.field_1724 == null) {
            return;
        }
        try {
            this.scanTick(level);
        } catch (Throwable t) {
            this.broken = true;
            this.restart();
            DIHClient.LOG.warn("[DIHClient] Amethyst Bypass stopped after an error", t);
        }
    }

    private void scanTick(class_638 level) {
        boolean bypass = this.method.is(Method.BYPASS);
        if (this.queue.isEmpty()) {
            if (++this.tick < RESCAN_TICKS) {
                return;
            }
            this.tick = 0;
            class_1923 center = mc.field_1724.method_31476();
            int radius = mc.field_1690.method_42503().method_41753();
            if (bypass) {
                // forget chunks that left the view or were unloaded, queue the loaded ones not scanned yet
                this.bypassScanned.removeIf(key -> !DonutDLogic.inChunkRange(class_1923.method_8325(key), class_1923.method_8332(key), center.field_9181, center.field_9180, radius)
                        || !isLoaded(level, class_1923.method_8325(key), class_1923.method_8332(key)));
                this.bypassMarkers.keySet().removeIf(key -> !this.bypassScanned.contains(key));
                for (int x = center.field_9181 - radius; x <= center.field_9181 + radius; x++) {
                    for (int z = center.field_9180 - radius; z <= center.field_9180 + radius; z++) {
                        if (isLoaded(level, x, z) && !this.bypassScanned.contains(class_1923.method_8331(x, z))) {
                            this.queue.add(new class_1923(x, z));
                        }
                    }
                }
            } else {
                this.found.clear();
                for (int x = center.field_9181 - radius; x <= center.field_9181 + radius; x++) {
                    for (int z = center.field_9180 - radius; z <= center.field_9180 + radius; z++) {
                        if (isLoaded(level, x, z)) {
                            this.queue.add(new class_1923(x, z));
                        }
                    }
                }
            }
        }
        long deadline = System.nanoTime() + BUDGET_NANOS;
        while (!this.queue.isEmpty() && System.nanoTime() < deadline) {
            class_1923 pos = this.queue.poll();
            if (!isLoaded(level, pos.field_9181, pos.field_9180)) {
                continue;
            }
            class_2818 chunk = level.method_8497(pos.field_9181, pos.field_9180);
            if (bypass) {
                collectPaletteLeak(chunk, this.bypassMarkers);
                this.bypassScanned.add(pos.method_8324());
            } else if (this.method.is(Method.ANBS_SCAN)) {
                this.collectGlow(level, chunk);
            } else {
                collectHiddenBuds(level, chunk, this.found);
            }
        }
        if (this.queue.isEmpty()) {
            this.hits = bypass ? markersToLongArray(this.bypassMarkers) : this.found.toLongArray();
        }
    }

    private void collectGlow(class_638 level, class_2818 chunk) {
        this.glowCells.clear();
        if (this.glow.scan(level, chunk, this.glowCells::add) >= this.minCells.get()) {
            int marks = Math.min(this.glowCells.size(), MAX_GLOW_MARKS);
            for (int i = 0; i < marks; i++) {
                this.found.add(this.glowCells.getLong(i));
            }
        }
    }

    private static boolean isLoaded(class_638 level, int chunkX, int chunkZ) {
        return level.method_2935().method_12123(chunkX, chunkZ);
    }

    private static long[] markersToLongArray(Long2ObjectOpenHashMap<class_2338> markers) {
        long[] out = new long[markers.size()];
        int i = 0;
        ObjectIterator<class_2338> it = markers.values().iterator();
        while (it.hasNext()) {
            out[i++] = it.next().method_10063();
        }
        return out;
    }

    /** Game thread, after the game handled the packet: a changed chunk has to be looked at again. */
    private void onPacketApplied(net.minecraft.class_2596<?> packet) {
        if (!this.method.is(Method.BYPASS)) {
            return;
        }
        if (packet instanceof class_2672 chunk) {
            this.dirtyBypassChunk(chunk.method_11523(), chunk.method_11524());
        } else if (packet instanceof class_2626 update) {
            this.dirtyBypassChunk(update.method_11309().method_10263() >> 4, update.method_11309().method_10260() >> 4);
        } else if (packet instanceof class_2637 sectionUpdate) {
            sectionUpdate.method_30621((pos, state) -> this.dirtyBypassChunk(pos.method_10263() >> 4, pos.method_10260() >> 4));
        }
    }

    private void dirtyBypassChunk(int chunkX, int chunkZ) {
        long key = class_1923.method_8331(chunkX, chunkZ);
        if (this.bypassScanned.remove(key)) {
            this.bypassMarkers.remove(key);
        }
    }

    /** Bypass: the first section whose palette holds a bud gives one marker for the chunk. */
    private static void collectPaletteLeak(class_2818 chunk, Long2ObjectOpenHashMap<class_2338> out) {
        class_2826[] sections = chunk.method_12006();
        int minSectionY = chunk.method_32891();
        class_1923 pos = chunk.method_12004();
        for (int i = 0; i < sections.length; i++) {
            class_2826 section = sections[i];
            if (section == null) {
                continue;
            }
            class_2841<class_2680> states = section.method_12265();
            // hasAny(false) is true for a palette that is empty; a section without a palette entry for a bud has no geode
            if (!states.method_19526(state -> false) && states.method_19526(AmethystBypassModule::isBud)) {
                int baseY = (minSectionY + i) * 16;
                class_2338 marker = pickMarker(chunk, section, baseY, pos);
                out.put(pos.method_8324(), marker != null ? marker : new class_2338(pos.method_8326() + 8, baseY + 8, pos.method_8328() + 8));
                return;
            }
        }
    }

    /** An open block in the section that touches a geode block, the same one every scan (lowest {@link DonutDLogic#scatter}). */
    private static class_2338 pickMarker(class_2818 chunk, class_2826 section, int baseY, class_1923 pos) {
        class_2339 neighbour = new class_2339();
        class_2338 best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int y = 0; y < 16; y++) {
            int worldY = baseY + y;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (!section.method_12254(x, y, z).method_26215()) {
                        continue;
                    }
                    int worldX = pos.method_8326() + x;
                    int worldZ = pos.method_8328() + z;
                    boolean touchesGeode = false;
                    for (class_2350 direction : class_2350.values()) {
                        int nx = worldX + direction.method_10148();
                        int nz = worldZ + direction.method_10165();
                        if (nx >> 4 == pos.field_9181 && nz >> 4 == pos.field_9180) {
                            neighbour.method_10103(nx, worldY + direction.method_10164(), nz);
                            class_2248 block = chunk.method_8320(neighbour).method_26204();
                            if (block == class_2246.field_27159 || block == class_2246.field_27160) {
                                touchesGeode = true;
                                break;
                            }
                        }
                    }
                    if (touchesGeode) {
                        int score = DonutDLogic.scatter(worldX, worldY, worldZ);
                        if (score < bestScore) {
                            bestScore = score;
                            best = new class_2338(worldX, worldY, worldZ);
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Light: open blocks with a bud's light that are the light's peak and touch a geode block. */
    private static void collectHiddenBuds(class_638 level, class_2818 chunk, LongOpenHashSet out) {
        class_2826[] sections = chunk.method_12006();
        int minSectionY = chunk.method_32891();
        class_1923 pos = chunk.method_12004();
        class_2339 cursor = new class_2339();
        int[] around = new int[6];
        for (int i = 0; i < sections.length; i++) {
            class_2826 section = sections[i];
            if (section == null || section.method_38292() || !section.method_12265().method_19526(AmethystBypassModule::isGeode)) {
                continue;
            }
            int baseY = (minSectionY + i) * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        // light-emitting blocks (lamps, torches) would pass for buds
                        if (section.method_12254(x, y, z).method_26213() > 0) {
                            continue;
                        }
                        cursor.method_10103(pos.method_8326() + x, baseY + y, pos.method_8328() + z);
                        int light = level.method_8314(class_1944.field_9282, cursor);
                        if (DonutDLogic.isBudLight(light) && touchesGeode(level, cursor) && isLocalMaximum(level, cursor, light, around)) {
                            out.add(cursor.method_10063());
                        }
                    }
                }
            }
        }
    }

    private static boolean touchesGeode(class_638 level, class_2338 pos) {
        for (class_2350 direction : class_2350.values()) {
            if (isGeode(level.method_8320(pos.method_10093(direction)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLocalMaximum(class_638 level, class_2338 pos, int light, int[] around) {
        class_2350[] directions = class_2350.values();
        for (int i = 0; i < directions.length; i++) {
            around[i] = level.method_8314(class_1944.field_9282, pos.method_10093(directions[i]));
        }
        return DonutDLogic.isLocalMaximum(light, around);
    }

    private static boolean isBud(class_2680 state) {
        class_2248 block = state.method_26204();
        return block == class_2246.field_27164 || block == class_2246.field_27163 || block == class_2246.field_27162;
    }

    private static boolean isGeode(class_2680 state) {
        return state.method_27852(class_2246.field_27159) || state.method_27852(class_2246.field_27160);
    }

    @Override
    public void onRender3D(Render3D r) {
        class_638 level = mc.field_1687;
        long[] current = this.hits;
        if (level == null || current.length == 0) {
            return;
        }
        boolean drawTracers = this.tracer.get();
        for (long packed : current) {
            class_2338 pos = class_2338.method_10092(packed);
            if (!isLoaded(level, pos.method_10263() >> 4, pos.method_10260() >> 4)) {
                continue;
            }
            r.boxOutline(new class_238(pos), COLOR, true);
            if (drawTracers) {
                r.tracer(class_243.method_24953(pos), COLOR);
            }
        }
    }
}
