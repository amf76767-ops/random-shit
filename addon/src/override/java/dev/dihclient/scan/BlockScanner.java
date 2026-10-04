package dev.dihclient.scan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.class_1923;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_2806;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_310;

/**
 * Finds the blocks a module looks for (Block ESP, Xray, Spawner Finder, the chunk finders ...) in the loaded chunks.
 * <p>
 * Faster than the 5.6 version: that one scanned every loaded chunk again and again (a new full pass every four seconds), so a
 * finder never stopped scanning. This one scans every chunk once, then only keeps the results up to date: a block change
 * ({@link #blockChanged}) adds or removes that one position directly, a chunk that is loaded again is scanned again, and a slow
 * safety pass (once a minute) catches anything missed. The list of all results is cached until something changes.
 */
public final class BlockScanner {
    private static final class_310 mc = class_310.method_1551();
    private static final long REFILL_MS = 4000L;
    private static final long FULL_PASS_MS = 60000L;
    /** All scanners alive, for {@link #blockChanged}. */
    private static final Set<BlockScanner> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    private final Predicate<class_2680> filter;
    private final Map<class_2680, Boolean> stateCache = new IdentityHashMap<>();
    private final Predicate<class_2680> cachedFilter = this::matches;
    private class_2680 lastState;
    private boolean lastResult;
    private final Map<Long, List<class_2338>> results = new ConcurrentHashMap<>();
    private final Deque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    /** Chunks scanned since they were loaded (or since the last full pass). */
    private final Set<Long> scanned = new HashSet<>();
    private int chunksPerTick;
    private int maxPerChunk = 512;
    private Object lastWorld;
    private long lastRefill;
    private long lastFullPass;
    private int ticks;
    private List<class_2338> allCache;
    private int countCache = -1;

    public BlockScanner(Predicate<class_2680> filter, int chunksPerTick) {
        this.filter = filter;
        this.chunksPerTick = chunksPerTick;
        synchronized (LIVE) {
            LIVE.add(this);
        }
    }

    /** A block of the client world changed (game thread). Every scanner that cares updates that one position. */
    public static void blockChanged(class_2338 pos, class_2680 oldState, class_2680 newState) {
        List<BlockScanner> all;
        synchronized (LIVE) {
            if (LIVE.isEmpty()) {
                return;
            }
            all = new ArrayList<>(LIVE);
        }
        for (BlockScanner s : all) {
            s.onBlockChanged(pos, oldState, newState);
        }
    }

    private void onBlockChanged(class_2338 pos, class_2680 oldState, class_2680 newState) {
        if (this.lastWorld == null || this.lastWorld != mc.field_1687) {
            return;
        }
        boolean was = oldState != null && this.matches(oldState);
        boolean now = newState != null && this.matches(newState);
        if (was == now) {
            return;
        }
        long key = class_1923.method_8331(pos.method_10263() >> 4, pos.method_10260() >> 4);
        if (!this.scanned.contains(key)) {
            return; // the scan of that chunk will find it
        }
        List<class_2338> old = this.results.get(key);
        List<class_2338> list = old == null ? new ArrayList<>() : new ArrayList<>(old);
        class_2338 at = pos.method_10062();
        if (now) {
            if (!list.contains(at) && list.size() < this.maxPerChunk) {
                list.add(at);
            }
        } else {
            list.remove(at);
        }
        if (list.isEmpty()) {
            this.results.remove(key);
        } else {
            this.results.put(key, list);
        }
        this.changed();
    }

    private void changed() {
        this.allCache = null;
        this.countCache = -1;
    }

    public void setChunksPerTick(int n) {
        this.chunksPerTick = Math.max(1, n);
    }

    public void setMaxPerChunk(int n) {
        this.maxPerChunk = n;
    }

    public void clear() {
        this.stateCache.clear();
        this.lastState = null;
        this.lastRefill = 0L;
        this.lastFullPass = System.currentTimeMillis();
        this.results.clear();
        this.queue.clear();
        this.queued.clear();
        this.scanned.clear();
        this.changed();
    }

    public void tick() {
        if (mc.field_1687 == null || mc.field_1724 == null) {
            return;
        }
        if (this.lastWorld != mc.field_1687) {
            this.lastWorld = mc.field_1687;
            this.clear();
        }
        long now = System.currentTimeMillis();
        if (now - this.lastFullPass > FULL_PASS_MS) {
            this.lastFullPass = now;
            this.scanned.clear(); // the safety pass: everything once more, slowly, in the background
        }
        if (this.queue.isEmpty() && now - this.lastRefill > REFILL_MS) {
            this.lastRefill = now;
            this.refill();
        }
        for (int i = 0; i < this.chunksPerTick && !this.queue.isEmpty() && (i <= 0 || ScanBudget.hasTime()); i++) {
            long key = this.queue.pollFirst();
            this.queued.remove(key);
            this.scan(class_1923.method_8325(key), class_1923.method_8332(key));
        }
        if (++this.ticks % 20 == 0) {
            boolean removed = this.results.keySet().removeIf(k -> !mc.field_1687.method_2935().method_12123(class_1923.method_8325(k), class_1923.method_8332(k)));
            this.scanned.removeIf(k -> !mc.field_1687.method_2935().method_12123(class_1923.method_8325(k), class_1923.method_8332(k)));
            if (removed) {
                this.changed();
            }
        }
    }

    /** A chunk was loaded (or changed a lot): scan it first. */
    public void prioritize(int chunkX, int chunkZ) {
        long key = class_1923.method_8331(chunkX, chunkZ);
        this.scanned.remove(key);
        if (this.queued.add(key)) {
            this.queue.addFirst(key);
        }
    }

    /** Queues the loaded chunks that were not scanned yet, nearest first. */
    private void refill() {
        int radius = mc.field_1690.method_38521() + 1;
        class_1923 center = mc.field_1724.method_31476();
        List<long[]> found = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = center.field_9181 + dx;
                int z = center.field_9180 + dz;
                long key = class_1923.method_8331(x, z);
                if (!this.scanned.contains(key) && mc.field_1687.method_2935().method_12123(x, z)) {
                    found.add(new long[]{key, (long) dx * dx + (long) dz * dz});
                }
            }
        }
        found.sort((a, b) -> Long.compare(a[1], b[1]));
        for (long[] f : found) {
            if (this.queued.add(f[0])) {
                this.queue.addLast(f[0]);
            }
        }
    }

    private boolean matches(class_2680 state) {
        if (state == this.lastState) {
            return this.lastResult;
        }
        Boolean hit = this.stateCache.get(state);
        if (hit == null) {
            hit = this.filter.test(state);
            this.stateCache.put(state, hit);
        }
        this.lastState = state;
        this.lastResult = hit;
        return hit;
    }

    private void scan(int chunkX, int chunkZ) {
        class_2818 chunk = mc.field_1687.method_2935().method_2857(chunkX, chunkZ, class_2806.field_12803, false);
        long key = class_1923.method_8331(chunkX, chunkZ);
        if (chunk == null) {
            if (this.results.remove(key) != null) {
                this.changed();
            }
            return;
        }
        this.scanned.add(key);
        List<class_2338> found = new ArrayList<>();
        class_2826[] sections = chunk.method_12006();
        int bottom = mc.field_1687.method_31607() >> 4;
        int bx = chunkX << 4;
        int bz = chunkZ << 4;
        outer:
        for (int s = 0; s < sections.length; s++) {
            class_2826 section = sections[s];
            // the palette says in a few steps whether a wanted block can be in the section at all
            if (section == null || section.method_38292() || !section.method_19523(this.cachedFilter)) {
                continue;
            }
            int by = bottom + s << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (this.matches(section.method_12254(x, y, z))) {
                            found.add(new class_2338(bx + x, by + y, bz + z));
                            if (found.size() >= this.maxPerChunk) {
                                break outer;
                            }
                        }
                    }
                }
            }
        }
        List<class_2338> before = found.isEmpty() ? this.results.remove(key) : this.results.put(key, found);
        if (before != null || !found.isEmpty()) {
            this.changed();
        }
    }

    public Map<Long, List<class_2338>> results() {
        return Collections.unmodifiableMap(this.results);
    }

    /** All positions found; the list is shared until something changes, so it must not be changed by the caller. */
    public List<class_2338> all() {
        List<class_2338> cached = this.allCache;
        if (cached == null) {
            cached = new ArrayList<>();
            for (List<class_2338> l : this.results.values()) {
                cached.addAll(l);
            }
            this.allCache = cached;
        }
        return new ArrayList<>(cached);
    }

    public int count() {
        int c = this.countCache;
        if (c < 0) {
            c = 0;
            for (List<class_2338> l : this.results.values()) {
                c += l.size();
            }
            this.countCache = c;
        }
        return c;
    }
}
