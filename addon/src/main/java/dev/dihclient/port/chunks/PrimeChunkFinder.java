package dev.dihclient.port.chunks;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.port.PacketBus;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.util.Notifications;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.class_156;
import net.minecraft.class_1944;
import net.minecraft.class_2231;
import net.minecraft.class_238;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2269;
import net.minecraft.class_2309;
import net.minecraft.class_2312;
import net.minecraft.class_2315;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2377;
import net.minecraft.class_2401;
import net.minecraft.class_243;
import net.minecraft.class_2426;
import net.minecraft.class_2428;
import net.minecraft.class_2436;
import net.minecraft.class_2453;
import net.minecraft.class_2457;
import net.minecraft.class_2459;
import net.minecraft.class_2537;
import net.minecraft.class_2596;
import net.minecraft.class_2665;
import net.minecraft.class_2667;
import net.minecraft.class_2671;
import net.minecraft.class_2672;
import net.minecraft.class_2676;
import net.minecraft.class_2678;
import net.minecraft.class_2680;
import net.minecraft.class_2804;
import net.minecraft.class_2806;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_3562;
import net.minecraft.class_4076;
import net.minecraft.class_4850;
import net.minecraft.class_5803;
import net.minecraft.class_631;
import net.minecraft.class_638;
import net.minecraft.class_6606;
import net.minecraft.class_8886;
import net.minecraft.class_2338.class_2339;

/**
 * Ported from an open-source client (GPL-3.0).
 * Below Y 0 the server sends light and block changes of chunks other players work in. This module remembers the block light of
 * every section, compares each new light packet with it and flags the chunk when something changed that no lamp, redstone ore or
 * amethyst nearby explains; redstone parts changing below Y 0 flag the chunk as well. The chunks you were in yourself are ignored.
 * The comparing is {@link LightDiff}; this class keeps the state, asks the world and draws.
 */
public final class PrimeChunkFinder extends Module {
    private static final int MAX_FLAGGED_CHUNKS = 256;
    private static final long REDSTONE_NOTIFY_COOLDOWN_MS = 15_000L;
    private static final long TOAST_GAP_MS = 2_000L;
    private static final long[] NO_ORES = new long[0];
    private static final int[] NO_LAMPS = new int[0];
    private static final long GONE_LAMP_MS = 5_000L;
    private static final int MAX_TRACKED_CHUNKS = 5_000;
    private static final int SNAPSHOT_RADIUS = 32;
    private static final double PLANE_Y = 63.0;
    private static final float EDGE_SHADE = 0.77F;

    public final BoolSetting notifications = this.bool("Notifications", "Toast when a chunk is flagged.", true);
    public final ColorSetting chunkColor = this.color("Chunk Color", "Colour of the flagged chunks.", 1342242650);
    public final BoolSetting tracers = this.bool("Tracers", "Draws lines to flagged chunks.", true);
    public final BoolSetting sound = this.bool("Sound", "Plays a sound with the toast.", true).visibleWhen(() -> this.notifications.get());

    /** Block light of every section of the chunks seen, least recently used first. */
    private final LinkedHashMap<Long, Map<Integer, byte[]>> chunkLight = new LinkedHashMap<>(256, 0.75F, true);
    private final LongOpenHashSet passedChunks = new LongOpenHashSet();
    private final LongLinkedOpenHashSet flaggedChunks = new LongLinkedOpenHashSet();
    private final LongOpenHashSet shownChunks = new LongOpenHashSet();
    private final ChunkAreas chunkAreas = new ChunkAreas();
    private final LongOpenHashSet checkedSections = new LongOpenHashSet();
    private final LongOpenHashSet amethystSections = new LongOpenHashSet();
    private final Long2ObjectOpenHashMap<long[]> oreSections = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<int[]> lampSections = new Long2ObjectOpenHashMap<>();
    private final Long2LongOpenHashMap goneLamps = new Long2LongOpenHashMap();
    private final class_2339 cursor = new class_2339();
    private final Long2LongOpenHashMap redstoneNotified = new Long2LongOpenHashMap();
    private final PacketBus.Netty nettyListener = this::onPacket;
    private final BlockUpdates.Listener blockListener = this::onBlockUpdate;
    private class_638 lastWorld;
    private int refreshTicks;
    private long lastToast;
    private int suppressedToasts;

    public PrimeChunkFinder() {
        super("Prime Chunk Finder", Category.BASEFINDING, "Flags chunks with light and redstone changes below Y 0.");
        this.redstoneNotified.defaultReturnValue(-4611686018427387904L);
    }

    @Override
    protected void onEnable() {
        if (mc.field_1724 != null && mc.field_1687 != null && mc.field_1724.method_23318() < 0.0) {
            this.reset();
            this.lastWorld = mc.field_1687;
            this.snapshotLoadedChunks();
            PacketBus.netty(this.nettyListener);
            BlockUpdates.add(this.blockListener);
        } else {
            Notifications.warn(this.name(), "Deepslate level only: go below Y 0 first.");
            this.setEnabled(false);
        }
    }

    @Override
    protected void onDisable() {
        PacketBus.remove(this.nettyListener);
        BlockUpdates.remove(this.blockListener);
        this.reset();
    }

    @Override
    public void onWorldChange() {
        this.reset();
    }

    @Override
    public String getInfo() {
        return Integer.toString(this.flaggedChunks.size());
    }

    /** Network thread, before the game handles the packet: the light of the world is still the old one when the queued task runs. */
    private boolean onPacket(class_2596<?> packet) {
        if (packet instanceof class_2678) {
            mc.execute(() -> {
                if (this.isEnabled()) {
                    this.reset();
                }
            });
        } else if (packet instanceof class_2672 chunk) {
            int chunkX = chunk.method_11523();
            int chunkZ = chunk.method_11524();
            class_6606 data = chunk.method_38599();
            mc.execute(() -> {
                if (this.isEnabled() && mc.field_1687 != null) {
                    this.baselineChunk(chunkX, chunkZ, data);
                }
            });
        } else if (packet instanceof class_2676 light) {
            int chunkX = light.method_11558();
            int chunkZ = light.method_11554();
            class_6606 data = light.method_38600();
            mc.execute(() -> {
                if (this.isEnabled() && mc.field_1687 != null) {
                    this.detectChanges(chunkX, chunkZ, data);
                }
            });
        }
        return false;
    }

    private void onBlockUpdate(class_2338 pos, class_2680 oldState, class_2680 newState) {
        if (oldState != newState && mc.field_1687 != null) {
            this.rememberGoneLamp(pos, oldState, newState);
            this.detectRedstone(pos, oldState, newState);
        }
    }

    @Override
    public void onTick() {
        if (mc.field_1687 != this.lastWorld) {
            // new connection or dimension: the remembered light belongs to the old world
            this.reset();
            this.lastWorld = mc.field_1687;
        }
        if (mc.field_1687 == null) {
            return;
        }
        if (mc.field_1724 != null && mc.field_1724.method_23318() < 0.0) {
            this.passedChunks.add(mc.field_1724.method_31476().method_8324());
        }
        this.forgetOldLamps();
        if (++this.refreshTicks % 10 == 0) {
            this.refreshShown(mc.field_1687);
        }
    }

    private void refreshShown(class_638 level) {
        this.shownChunks.clear();
        LongIterator it = this.flaggedChunks.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            if (level.method_2935().method_12123(ProtectedChunkStore.chunkX(key), ProtectedChunkStore.chunkZ(key))) {
                this.shownChunks.add(key);
            }
        }
    }

    @Override
    public void onRender3D(Render3D r) {
        if (this.shownChunks.isEmpty() || mc.field_1687 == null) {
            return;
        }
        int fill = this.chunkColor.get();
        this.drawChunks(r, fill);
        if (this.tracers.get()) {
            this.chunkAreas.update(this.shownChunks);
            int color = fill | 0xFF000000;
            for (int i = 0; i < this.chunkAreas.count(); i++) {
                r.tracer(new class_243(this.chunkAreas.x(i), PLANE_Y, this.chunkAreas.z(i)), color);
            }
        }
    }

    /** A flat plane per chunk and an edge line wherever the next chunk is not flagged (a thin box: a zero-height box has no face to draw). */
    private void drawChunks(Render3D r, int fill) {
        int edge = withAlpha(scaleRgb(fill, EDGE_SHADE), Math.min(255, (fill >>> 24) * 3 / 2));
        LongIterator it = this.shownChunks.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            int cx = ProtectedChunkStore.chunkX(key);
            int cz = ProtectedChunkStore.chunkZ(key);
            double x0 = cx << 4;
            double z0 = cz << 4;
            double x1 = x0 + 16.0;
            double z1 = z0 + 16.0;
            r.boxFilled(new class_238(x0, PLANE_Y, z0, x1, PLANE_Y + 0.05, z1), fill, true);
            if (!this.shownChunks.contains(ProtectedChunkStore.key(cx, cz - 1))) {
                r.line(x0, PLANE_Y, z0, x1, PLANE_Y, z0, edge, true);
            }
            if (!this.shownChunks.contains(ProtectedChunkStore.key(cx, cz + 1))) {
                r.line(x0, PLANE_Y, z1, x1, PLANE_Y, z1, edge, true);
            }
            if (!this.shownChunks.contains(ProtectedChunkStore.key(cx - 1, cz))) {
                r.line(x0, PLANE_Y, z0, x0, PLANE_Y, z1, edge, true);
            }
            if (!this.shownChunks.contains(ProtectedChunkStore.key(cx + 1, cz))) {
                r.line(x1, PLANE_Y, z0, x1, PLANE_Y, z1, edge, true);
            }
        }
    }

    private static int scaleRgb(int argb, float scale) {
        int r = Math.min(255, (int) ((argb >> 16 & 0xFF) * scale));
        int g = Math.min(255, (int) ((argb >> 8 & 0xFF) * scale));
        int b = Math.min(255, (int) ((argb & 0xFF) * scale));
        return r << 16 | g << 8 | b;
    }

    private static int withAlpha(int rgb, int alpha) {
        return rgb & 0xFFFFFF | alpha << 24;
    }

    private void flagChunk(long key) {
        this.flaggedChunks.addAndMoveToLast(key);
        this.shownChunks.add(key);
        while (this.flaggedChunks.size() > MAX_FLAGGED_CHUNKS) {
            this.flaggedChunks.removeFirstLong();
        }
    }

    /** One toast per two seconds, the ones in between are counted ("+3 more"). */
    private void toast(String text) {
        if (!this.notifications.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastToast < TOAST_GAP_MS) {
            this.suppressedToasts++;
            return;
        }
        this.lastToast = now;
        String shown = this.suppressedToasts > 0 ? text + " (+" + this.suppressedToasts + " more)" : text;
        this.suppressedToasts = 0;
        ChunkAlerts.warn(this.name(), shown, this.sound.get());
    }

    private void snapshotLoadedChunks() {
        if (mc.field_1687 == null || mc.field_1724 == null) {
            return;
        }
        int centerX = mc.field_1724.method_31477() >> 4;
        int centerZ = mc.field_1724.method_31479() >> 4;
        // only as far as chunks can be loaded at all (the view distance), not a fixed 65 x 65 chunks
        int radius = Math.min(SNAPSHOT_RADIUS, mc.field_1690.method_38521() + 1);
        for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
            for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
                Map<Integer, byte[]> sections = this.snapshotChunk(chunkX, chunkZ);
                if (sections != null) {
                    this.chunkLight.put(ProtectedChunkStore.key(chunkX, chunkZ), sections);
                }
            }
        }
        this.trimChunkCache();
    }

    /** The block light the world holds right now, or null when the chunk is not loaded. */
    private Map<Integer, byte[]> snapshotChunk(int chunkX, int chunkZ) {
        class_638 level = mc.field_1687;
        if (level == null) {
            return null;
        }
        class_631 chunkSource = level.method_2935();
        if (!chunkSource.method_12123(chunkX, chunkZ)) {
            return null;
        }
        class_3562 blockLight = chunkSource.method_12130().method_15562(class_1944.field_9282);
        Map<Integer, byte[]> sections = this.createZeroedChunk();
        for (int sectionY = this.bottomSection(); sectionY <= this.topSection(); sectionY++) {
            class_2804 layer = blockLight.method_15544(class_4076.method_18676(chunkX, sectionY, chunkZ));
            if (layer != null && !layer.method_51379(0)) {
                sections.put(sectionY, Arrays.copyOf(layer.method_12137(), LightDiff.SECTION_BYTES));
            }
        }
        return sections;
    }

    /** A new chunk: its light is the starting point, nothing to compare with. */
    private void baselineChunk(int chunkX, int chunkZ, class_6606 data) {
        Map<Integer, byte[]> sections = this.createZeroedChunk();
        this.apply(data, sections, chunkX, chunkZ, false, null);
        this.chunkLight.put(ProtectedChunkStore.key(chunkX, chunkZ), sections);
        this.trimChunkCache();
    }

    private void detectChanges(int chunkX, int chunkZ, class_6606 data) {
        long key = ProtectedChunkStore.key(chunkX, chunkZ);
        this.checkedSections.clear();
        this.amethystSections.clear();
        this.oreSections.clear();
        this.lampSections.clear();
        Map<Integer, byte[]> sections = this.chunkLight.get(key);
        if (sections == null) {
            sections = this.snapshotChunk(chunkX, chunkZ);
        }
        boolean compare = sections != null && !this.isOwnChunk(key);
        if (sections == null) {
            sections = this.createZeroedChunk();
        }
        LightDiff.Summary summary = new LightDiff.Summary();
        this.apply(data, sections, chunkX, chunkZ, compare, summary);
        this.chunkLight.put(key, sections);
        this.trimChunkCache();
        if (summary.count != 0) {
            this.flagChunk(key);
            this.toast(summary.x + ", " + summary.y + ", " + summary.z);
        }
    }

    private void apply(class_6606 data, Map<Integer, byte[]> sections, int chunkX, int chunkZ, boolean compare, LightDiff.Summary summary) {
        class_638 level = mc.field_1687;
        int lightBottom = level.method_2935().method_12130().method_31929();
        LightDiff.apply(sections, chunkX, chunkZ, data.method_38608(), data.method_38610(), data.method_38609(), lightBottom, this.bottomSection(),
                this.topSection(), compare, this::explained, summary);
    }

    /** A light change that a lamp, redstone ore or amethyst near it explains is not a player. */
    private boolean explained(int x, int y, int z, int oldValue, int newValue) {
        int value = Math.max(oldValue, newValue);
        return this.fromAmethyst(x, y, z, oldValue, newValue) || this.fromRedstoneOre(x, y, z, value) || this.fromLamp(x, y, z, value);
    }

    private void rememberGoneLamp(class_2338 pos, class_2680 previous, class_2680 state) {
        if (isLamp(previous)) {
            int light = previous.method_26213();
            if (!isLamp(state) || state.method_26213() < light) {
                this.goneLamps.put(pos.method_10063(), (class_156.method_658() + GONE_LAMP_MS) << 4 | light);
            }
        }
    }

    private void forgetOldLamps() {
        if (!this.goneLamps.isEmpty()) {
            long now = class_156.method_658();
            LongIterator it = this.goneLamps.values().iterator();
            while (it.hasNext()) {
                if (it.nextLong() >>> 4 < now) {
                    it.remove();
                }
            }
        }
    }

    private void detectRedstone(class_2338 pos, class_2680 previous, class_2680 state) {
        if (pos.method_10264() < 0 && (isRedstone(state) || isRedstone(previous))) {
            long key = ProtectedChunkStore.key(pos.method_10263() >> 4, pos.method_10260() >> 4);
            if (!this.isOwnChunk(key)) {
                this.flagChunk(key);
                long now = class_156.method_658();
                if (this.notifications.get() && now - this.redstoneNotified.get(key) >= REDSTONE_NOTIFY_COOLDOWN_MS) {
                    this.redstoneNotified.put(key, now);
                    this.toast("Redstone " + pos.method_10263() + ", " + pos.method_10264() + ", " + pos.method_10260());
                }
            }
        }
    }

    private static boolean isRedstone(class_2680 state) {
        class_2248 block = state.method_26204();
        return block instanceof class_2457
                || block instanceof class_2312
                || block instanceof class_2459
                || block instanceof class_2453
                || block instanceof class_2426
                || block instanceof class_2665
                || block instanceof class_2671
                || block instanceof class_2667
                || block instanceof class_2315
                || block instanceof class_2377
                || block instanceof class_2428
                || block instanceof class_2309
                || block instanceof class_2401
                || block instanceof class_2269
                || block instanceof class_2231
                || block instanceof class_2537
                || block instanceof class_4850
                || block instanceof class_8886
                || block instanceof class_2436;
    }

    /** Chunks the player was in below Y 0 (the light there is the player's own work). */
    private boolean isOwnChunk(long key) {
        if (this.passedChunks.contains(key)) {
            return true;
        }
        return mc.field_1724 != null && mc.field_1724.method_31476().method_8324() == key;
    }

    private boolean fromLamp(int x, int y, int z, int value) {
        if (value > LightDiff.LAMP_LIGHT) {
            return false;
        }
        class_638 level = mc.field_1687;
        if (level == null) {
            return false;
        }
        int reach = LightDiff.LAMP_LIGHT - value;
        for (int sx = x - reach >> 4; sx <= x + reach >> 4; sx++) {
            for (int sy = y - reach >> 4; sy <= y + reach >> 4; sy++) {
                for (int sz = z - reach >> 4; sz <= z + reach >> 4; sz++) {
                    for (int lamp : this.lamps(level, sx, sy, sz)) {
                        int index = lamp >>> 4;
                        int lx = (sx << 4) + (index & 15);
                        int ly = (sy << 4) + (index >>> 8);
                        int lz = (sz << 4) + (index >>> 4 & 15);
                        if (LightDiff.lampExplains(lx, ly, lz, lamp & 15, x, y, z, value)) {
                            return true;
                        }
                    }
                }
            }
        }
        // lamps that were switched off or broken a moment ago: their light is still in the packet that comes now
        long now = class_156.method_658();
        ObjectIterator<Long2LongMap.Entry> gone = this.goneLamps.long2LongEntrySet().iterator();
        while (gone.hasNext()) {
            Long2LongMap.Entry entry = gone.next();
            long packed = entry.getLongValue();
            if (packed >>> 4 >= now) {
                long pos = entry.getLongKey();
                if (LightDiff.lampExplains(class_2338.method_10061(pos), class_2338.method_10071(pos), class_2338.method_10083(pos), (int) (packed & 15L),
                        x, y, z, value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int[] lamps(class_638 level, int sectionX, int sectionY, int sectionZ) {
        long key = class_4076.method_18685(sectionX, sectionY, sectionZ);
        int[] cached = this.lampSections.get(key);
        if (cached != null) {
            return cached;
        }
        int[] found = scanLamps(level, sectionX, sectionY, sectionZ);
        this.lampSections.put(key, found);
        return found;
    }

    private static int[] scanLamps(class_638 level, int sectionX, int sectionY, int sectionZ) {
        class_2826 section = loadedSection(level, sectionX, sectionY, sectionZ);
        if (section == null || !section.method_12265().method_19526(PrimeChunkFinder::isLamp)) {
            return NO_LAMPS;
        }
        IntArrayList lamps = new IntArrayList();
        for (int index = 0; index < 4096; index++) {
            class_2680 state = section.method_12254(index & 15, index >>> 8, index >>> 4 & 15);
            if (isLamp(state)) {
                lamps.add(index << 4 | state.method_26213());
            }
        }
        return lamps.isEmpty() ? NO_LAMPS : lamps.toIntArray();
    }

    /** The section when it is inside the world, loaded and not empty; else null. */
    private static class_2826 loadedSection(class_638 level, int sectionX, int sectionY, int sectionZ) {
        if (sectionY < level.method_32891() || sectionY > level.method_31597()) {
            return null;
        }
        class_2818 chunk = level.method_2935().method_2857(sectionX, sectionZ, class_2806.field_12803, false);
        if (chunk == null) {
            return null;
        }
        class_2826 section = chunk.method_38259(chunk.method_31603(sectionY));
        return section.method_38292() ? null : section;
    }

    private static boolean isLamp(class_2680 state) {
        return state.method_27852(class_2246.field_10336)
                || state.method_27852(class_2246.field_10099)
                || state.method_27852(class_2246.field_22092)
                || state.method_27852(class_2246.field_22093)
                || state.method_27852(class_2246.field_61902)
                || state.method_27852(class_2246.field_61903)
                || class_5803.method_33618(state);
    }

    private boolean fromAmethyst(int x, int y, int z, int oldValue, int newValue) {
        if (Math.max(oldValue, newValue) > LightDiff.AMETHYST_MAX_LIGHT) {
            return false;
        }
        class_638 level = mc.field_1687;
        if (level == null || !this.amethystSectionNear(level, x, y, z)) {
            return false;
        }
        int reach = LightDiff.AMETHYST_REACH;
        for (int dx = -reach; dx <= reach; dx++) {
            int restX = reach - Math.abs(dx);
            for (int dy = -restX; dy <= restX; dy++) {
                int restY = restX - Math.abs(dy);
                for (int dz = -restY; dz <= restY; dz++) {
                    if (isAmethyst(level.method_8320(this.cursor.method_10103(x + dx, y + dy, z + dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean amethystSectionNear(class_638 level, int x, int y, int z) {
        int reach = LightDiff.AMETHYST_REACH;
        for (int sx = x - reach >> 4; sx <= x + reach >> 4; sx++) {
            for (int sy = y - reach >> 4; sy <= y + reach >> 4; sy++) {
                for (int sz = z - reach >> 4; sz <= z + reach >> 4; sz++) {
                    long key = class_4076.method_18685(sx, sy, sz);
                    if (this.checkedSections.add(key) && sectionHasAmethyst(level, sx, sy, sz)) {
                        this.amethystSections.add(key);
                    }
                    if (this.amethystSections.contains(key)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean sectionHasAmethyst(class_638 level, int sectionX, int sectionY, int sectionZ) {
        class_2826 section = loadedSection(level, sectionX, sectionY, sectionZ);
        return section != null && section.method_12265().method_19526(PrimeChunkFinder::isAmethyst);
    }

    private boolean fromRedstoneOre(int x, int y, int z, int value) {
        if (value > LightDiff.ORE_LIGHT) {
            return false;
        }
        class_638 level = mc.field_1687;
        if (level == null) {
            return false;
        }
        int reach = LightDiff.ORE_LIGHT - value;
        for (int sx = x - reach >> 4; sx <= x + reach >> 4; sx++) {
            for (int sy = y - reach >> 4; sy <= y + reach >> 4; sy++) {
                for (int sz = z - reach >> 4; sz <= z + reach >> 4; sz++) {
                    for (long ore : this.exposedOres(level, sx, sy, sz)) {
                        if (LightDiff.oreExplains(class_2338.method_10061(ore), class_2338.method_10071(ore), class_2338.method_10083(ore), x, y, z, value)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private long[] exposedOres(class_638 level, int sectionX, int sectionY, int sectionZ) {
        long key = class_4076.method_18685(sectionX, sectionY, sectionZ);
        long[] cached = this.oreSections.get(key);
        if (cached != null) {
            return cached;
        }
        long[] found = this.scanExposedOres(level, sectionX, sectionY, sectionZ);
        this.oreSections.put(key, found);
        return found;
    }

    private long[] scanExposedOres(class_638 level, int sectionX, int sectionY, int sectionZ) {
        class_2826 section = loadedSection(level, sectionX, sectionY, sectionZ);
        if (section == null || !section.method_12265().method_19526(PrimeChunkFinder::isRedstoneOre)) {
            return NO_ORES;
        }
        LongArrayList ores = new LongArrayList();
        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    if (isRedstoneOre(section.method_12254(lx, ly, lz))) {
                        int wx = (sectionX << 4) + lx;
                        int wy = (sectionY << 4) + ly;
                        int wz = (sectionZ << 4) + lz;
                        if (this.exposed(level, wx, wy, wz)) {
                            ores.add(class_2338.method_10064(wx, wy, wz));
                        }
                    }
                }
            }
        }
        return ores.isEmpty() ? NO_ORES : ores.toLongArray();
    }

    /** Only ore that touches air or a see-through block lights up (ore inside rock does not shine). */
    private boolean exposed(class_638 level, int x, int y, int z) {
        for (class_2350 direction : class_2350.values()) {
            class_2680 neighbour = level.method_8320(this.cursor.method_10103(x + direction.method_10148(), y + direction.method_10164(), z + direction.method_10165()));
            if (neighbour.method_26215() || !neighbour.method_26225()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRedstoneOre(class_2680 state) {
        return state.method_27852(class_2246.field_10080) || state.method_27852(class_2246.field_29030);
    }

    private static boolean isAmethyst(class_2680 state) {
        return state.method_27852(class_2246.field_27161)
                || state.method_27852(class_2246.field_27162)
                || state.method_27852(class_2246.field_27163)
                || state.method_27852(class_2246.field_27164)
                || state.method_27852(class_2246.field_27160)
                || state.method_27852(class_2246.field_27159);
    }

    private Map<Integer, byte[]> createZeroedChunk() {
        return LightDiff.zeroedChunk(new HashMap<>(), this.bottomSection(), this.topSection());
    }

    private int bottomSection() {
        return mc.field_1687.method_32891();
    }

    /** Only the sections below Y 0 are kept and compared: the module looks for light changes there and nowhere else. */
    private int topSection() {
        return Math.min(mc.field_1687.method_31597(), -1);
    }

    private void trimChunkCache() {
        while (this.chunkLight.size() > MAX_TRACKED_CHUNKS) {
            Iterator<Long> keys = this.chunkLight.keySet().iterator();
            keys.next();
            keys.remove();
        }
    }

    private void reset() {
        this.chunkLight.clear();
        this.passedChunks.clear();
        this.flaggedChunks.clear();
        this.shownChunks.clear();
        this.redstoneNotified.clear();
        this.lampSections.clear();
        this.goneLamps.clear();
        this.suppressedToasts = 0;
    }
}
