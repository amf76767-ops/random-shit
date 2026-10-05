package dev.dihclient;

import dev.dihclient.port.chunks.ChunkAreas;
import dev.dihclient.port.chunks.ChunkEntryGuard;
import dev.dihclient.port.chunks.ChunkFlagTracker;
import dev.dihclient.port.chunks.ChunkLightSnapshot;
import dev.dihclient.port.chunks.LightDataClassifier;
import dev.dihclient.port.chunks.LightDiff;
import dev.dihclient.port.chunks.LightSectionStatus;
import dev.dihclient.port.chunks.ProtectedChunkStore;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChunksTests {
    static int failed;
    static int passed;

    static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + what);
        }
    }

    static byte[] filled(int b) {
        byte[] a = new byte[2048];
        Arrays.fill(a, (byte) b);
        return a;
    }

    static BitSet bits(int... set) {
        BitSet b = new BitSet();
        for (int i : set) {
            b.set(i);
        }
        return b;
    }

    public static void main(String[] args) throws Exception {
        classifier();
        snapshot();
        flagTracker();
        entryGuard();
        store();
        areas();
        lightDiff();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void classifier() {
        check(LightDataClassifier.classifyPayload(filled(0)) == LightSectionStatus.ZEROED_PRESENT, "all zero = zeroed");
        check(LightDataClassifier.classifyPayload(filled(0xFF)) == LightSectionStatus.UNIFORM_MAX, "all 0xFF = uniform max");
        check(LightDataClassifier.classifyPayload(filled(0x77)) == LightSectionStatus.NORMAL, "uniform but not 0 / max = normal");
        byte[] last = filled(0);
        last[2047] = 1;
        check(LightDataClassifier.classifyPayload(last) == LightSectionStatus.NORMAL, "one lit byte at the very end = normal");
        byte[] first = filled(0xFF);
        first[0] = 0;
        check(LightDataClassifier.classifyPayload(first) == LightSectionStatus.NORMAL, "one dark byte in max light = normal");
        check(LightDataClassifier.classifyPayload(null) == LightSectionStatus.UNKNOWN, "null = unknown");
        check(LightDataClassifier.classifyPayload(new byte[2047]) == LightSectionStatus.UNKNOWN, "wrong size = unknown");
        check(LightDataClassifier.classifyPayload(new byte[2049]) == LightSectionStatus.UNKNOWN, "wrong size (long) = unknown");

        LightSectionStatus[] out = new LightSectionStatus[6];
        Arrays.fill(out, LightSectionStatus.UNKNOWN);

        LightDataClassifier.applyTrack(bits(1, 4), bits(2), List.of(filled(0), filled(0x33)), out);
        check(out[0] == LightSectionStatus.UNKNOWN && out[3] == LightSectionStatus.UNKNOWN && out[5] == LightSectionStatus.UNKNOWN,
                "sections not in the packet keep their status");
        check(out[1] == LightSectionStatus.ZEROED_PRESENT, "first data array goes to the first set bit");
        check(out[4] == LightSectionStatus.NORMAL, "second data array goes to the second set bit");
        check(out[2] == LightSectionStatus.EMPTY_MASK, "empty-mask section");

        LightDataClassifier.applyTrack(bits(0, 1), new BitSet(), List.of(filled(0)), out);
        check(out[0] == LightSectionStatus.ZEROED_PRESENT && out[1] == LightSectionStatus.UNKNOWN, "missing data array = unknown, no exception");

        LightDataClassifier.applyTrack(bits(4), new BitSet(), List.of(filled(0)), out);
        check(out[4] == LightSectionStatus.ZEROED_PRESENT && out[0] == LightSectionStatus.ZEROED_PRESENT, "update overwrites only its sections");

        LightDataClassifier.applyTrack(bits(40), bits(41), List.of(filled(0)), out);
        check(out.length == 6, "bits beyond the section count are ignored");
    }

    static void snapshot() {

        ChunkLightSnapshot s = new ChunkLightSnapshot(24, -4);
        check(s.sky.length == 26, "two extra light sections");
        check(!s.hasBaseSignal(), "all unknown = no signal");
        s.sky[1] = LightSectionStatus.ZEROED_PRESENT;
        check(s.hasBaseSignal(), "zeroed deep section = signal");
        s.sky[1] = LightSectionStatus.NORMAL;
        s.sky[0] = LightSectionStatus.ZEROED_PRESENT;
        s.sky[25] = LightSectionStatus.ZEROED_PRESENT;
        check(!s.hasBaseSignal(), "the two outer light sections never count");
        s.sky[8] = LightSectionStatus.ZEROED_PRESENT;
        check(!s.hasBaseSignal(), "a section reaching y 62 or higher does not count");
        s.sky[7] = LightSectionStatus.ZEROED_PRESENT;
        check(s.hasBaseSignal(), "section 2 (top 47) counts");
        s.sky[7] = LightSectionStatus.UNIFORM_MAX;
        s.sky[3] = LightSectionStatus.EMPTY_MASK;
        check(!s.hasBaseSignal(), "only ZEROED_PRESENT counts");

        ChunkLightSnapshot n = new ChunkLightSnapshot(8, 0);
        n.sky[4] = LightSectionStatus.ZEROED_PRESENT;
        check(!n.hasBaseSignal(), "bottom section offset is respected");
        n.sky[3] = LightSectionStatus.ZEROED_PRESENT;
        check(n.hasBaseSignal(), "bottom section offset is respected (2)");
    }

    static final class FakeProtection implements ChunkFlagTracker.Protection {
        final Set<Long> blocked = new HashSet<>();

        @Override
        public boolean flagAllowed(long chunk, boolean alreadyFlagged) {
            return alreadyFlagged || !blocked.contains(chunk);
        }
    }

    static void flagTracker() {
        FakeProtection protection = new FakeProtection();
        ChunkFlagTracker t = new ChunkFlagTracker(protection);
        List<String> spotted = new ArrayList<>();
        t.setSpottedListener((x, z) -> spotted.add(x + "," + z));
        long key = ProtectedChunkStore.key(5, -7);
        List<byte[]> dark = List.of(filled(0));
        List<byte[]> normal = List.of(filled(0x55));

        t.ingest(5, -7, bits(1), new BitSet(), normal, 24, -4);
        check(!t.isFlagged(key) && t.snapshots().isEmpty(), "normal light: not flagged, nothing kept");
        t.ingest(5, -7, bits(1), new BitSet(), dark, 24, -4);
        check(t.isFlagged(key) && spotted.equals(List.of("5,-7")), "dark deep section: flagged and reported once");
        t.ingest(5, -7, bits(1), new BitSet(), dark, 24, -4);
        check(spotted.size() == 1, "same chunk is not reported again");
        t.ingest(5, -7, bits(1), new BitSet(), normal, 24, -4);
        check(t.isFlagged(key) && t.snapshots().containsKey(key), "once reported the chunk stays flagged even when the light is normal again");
        t.clear();
        check(t.snapshots().isEmpty() && !t.isFlagged(key), "clear forgets all");

        protection.blocked.add(key);
        t.ingest(5, -7, bits(1), new BitSet(), dark, 24, -4);
        check(!t.isFlagged(key) && t.snapshots().isEmpty() && spotted.size() == 1, "protected chunk is not flagged");
        protection.blocked.clear();

        t.setSpottedListener(null);
        t.ingest(5, -7, bits(1), new BitSet(), dark, 24, -4);
        check(t.isFlagged(key) && spotted.size() == 1, "without a listener the chunk is flagged silently");
        t.ingest(5, -7, bits(1), new BitSet(), normal, 24, -4);
        check(!t.isFlagged(key) && t.snapshots().isEmpty(), "without an alert the flag ends with the signal");
        t.setSpottedListener((x, z) -> spotted.add(x + "," + z));

        t.ingest(5, -7, bits(1), new BitSet(), dark, 24, -4);
        t.clear();
        t.ingest(5, -7, bits(1), new BitSet(), dark, 24, -4);
        ChunkLightSnapshot before = t.snapshots().get(key);
        t.ingest(5, -7, bits(1), new BitSet(), dark, 8, 0);
        check(t.snapshots().get(key) != before && t.snapshots().get(key).sectionCount == 8, "other height = new snapshot");

        t.clear();
        long k2 = ProtectedChunkStore.key(1, 1);
        t.hold(1, 1, bits(1), new BitSet(), dark, 24, -4);
        check(!t.isFlagged(k2) && t.isFlaggedOrHeld(k2), "held chunk counts for the guard but is not flagged");
        t.hold(1, 1, bits(1), new BitSet(), normal, 24, -4);
        check(!t.isFlaggedOrHeld(k2), "a normal packet releases the held chunk");
        t.hold(1, 1, bits(1), new BitSet(), dark, 24, -4);
        protection.blocked.add(k2);
        t.hold(1, 1, bits(1), new BitSet(), dark, 24, -4);
        check(!t.isFlaggedOrHeld(k2), "a protected chunk is not held either");
        protection.blocked.clear();
        t.hold(1, 1, bits(1), new BitSet(), dark, 24, -4);

        t.ingest(1, 1, bits(1), new BitSet(), normal, 24, -4);
        check(!t.isFlaggedOrHeld(k2), "ingest of a normal packet also releases the held state");
    }

    static void entryGuard() {
        ChunkEntryGuard guard = new ChunkEntryGuard();
        LongOpenHashSet loaded = new LongOpenHashSet();
        LongOpenHashSet flagged = new LongOpenHashSet();
        LongOpenHashSet protectedNow = new LongOpenHashSet();
        long home = ProtectedChunkStore.key(10, 10);

        guard.update(home, loaded::contains, flagged::contains, protectedNow::add);
        check(protectedNow.isEmpty(), "waits until the own chunk has arrived");

        loaded.add(home);
        ProtectedChunkStore.forEachInArea(10, 10, k -> {
            long x = ProtectedChunkStore.chunkX(k);
            if (x <= 11) {
                loaded.add(k);
            }
        });
        guard.update(home, loaded::contains, flagged::contains, protectedNow::add);
        check(protectedNow.size() == 4 * 5, "protects the loaded part of the 5x5 area: " + protectedNow.size());

        ProtectedChunkStore.forEachInArea(10, 10, loaded::add);
        guard.update(home, loaded::contains, flagged::contains, protectedNow::add);
        check(protectedNow.size() == 25, "protects the rest as soon as it arrives: " + protectedNow.size());
        check(protectedNow.contains(ProtectedChunkStore.key(8, 8)) && protectedNow.contains(ProtectedChunkStore.key(12, 12)), "corners of the area");
        check(!protectedNow.contains(ProtectedChunkStore.key(13, 10)), "nothing outside the radius");

        protectedNow.clear();
        long next = ProtectedChunkStore.key(30, 30);
        ProtectedChunkStore.forEachInArea(30, 30, loaded::add);
        flagged.add(ProtectedChunkStore.key(31, 29));
        guard.update(next, loaded::contains, flagged::contains, protectedNow::add);
        check(protectedNow.size() == 24 && !protectedNow.contains(ProtectedChunkStore.key(31, 29)), "flagged chunk is not protected");

        protectedNow.clear();
        long fl = ProtectedChunkStore.key(50, 50);
        ProtectedChunkStore.forEachInArea(50, 50, loaded::add);
        flagged.add(fl);
        guard.update(fl, loaded::contains, flagged::contains, protectedNow::add);
        guard.update(fl, loaded::contains, flagged::contains, protectedNow::add);
        check(protectedNow.isEmpty(), "standing in a flagged chunk protects nothing");

        flagged.clear();
        guard.reset();
        guard.update(fl, loaded::contains, flagged::contains, protectedNow::add);
        check(protectedNow.size() == 25, "after reset the chunk is entered again");

        ChunkEntryGuard g2 = new ChunkEntryGuard();
        LongOpenHashSet p2 = new LongOpenHashSet();
        LongOpenHashSet l2 = new LongOpenHashSet();
        long a = ProtectedChunkStore.key(0, 0);
        l2.add(a);
        g2.update(a, l2::contains, k -> false, p2::add);
        check(p2.size() == 1, "only the loaded chunk at first");
        long b = ProtectedChunkStore.key(100, 100);
        l2.add(b);
        g2.update(b, l2::contains, k -> false, p2::add);
        l2.add(ProtectedChunkStore.key(1, 1));
        g2.update(b, l2::contains, k -> false, p2::add);
        check(!p2.contains(ProtectedChunkStore.key(1, 1)), "a chunk of the old area is not protected after leaving");
    }

    static void store() throws IOException {

        check(ProtectedChunkStore.chunkX(ProtectedChunkStore.key(-5, 9)) == -5 && ProtectedChunkStore.chunkZ(ProtectedChunkStore.key(-5, 9)) == 9, "key roundtrip");
        check(ProtectedChunkStore.chunkX(ProtectedChunkStore.key(Integer.MIN_VALUE, Integer.MAX_VALUE)) == Integer.MIN_VALUE
                && ProtectedChunkStore.chunkZ(ProtectedChunkStore.key(Integer.MIN_VALUE, Integer.MAX_VALUE)) == Integer.MAX_VALUE, "key extremes");
        LongArrayList area = new LongArrayList();
        ProtectedChunkStore.forEachInArea(0, 0, area::add);
        check(area.size() == 25 && new LongOpenHashSet(area).size() == 25, "area of 25 distinct chunks");
        check(ProtectedChunkStore.serverKey(" Example.COM:25565 ").equals("example.com"), "server key drops the default port and case");
        check(ProtectedChunkStore.serverKey("example.com:25566").equals("example.com:25566"), "other port stays");
        check(ProtectedChunkStore.serverKey(null).equals("unknown") && ProtectedChunkStore.serverKey("  ").equals("unknown"), "no address = unknown");
        check(ProtectedChunkStore.singleplayerKey("My World").equals("singleplayer/My World"), "singleplayer key");
        String name = ProtectedChunkStore.fileName("Ex/ample.com", "minecraft:overworld");
        check(name.endsWith(".json") && !name.contains("/") && !name.contains(":"), "file name is safe: " + name);
        check(name.equals(ProtectedChunkStore.fileName("Ex/ample.com", "minecraft:overworld")), "file name is stable");
        check(!name.equals(ProtectedChunkStore.fileName("Ex/ample.com", "minecraft:the_nether")), "dimension changes the file");
        check(!ProtectedChunkStore.fileName("a/b", "d").equals(ProtectedChunkStore.fileName("a_b", "d")), "names that read the same stay apart (hash)");

        Path dir = Files.createTempDirectory("chunks-test");
        Path legacy = Files.createTempDirectory("chunks-legacy");
        try {
            String server = "example.com";
            String dim = "minecraft:overworld";
            ProtectedChunkStore a = new ProtectedChunkStore(() -> dir, null);
            check(!a.protect(1), "nothing selected: nothing is stored");
            a.select(server, dim);
            long c1 = ProtectedChunkStore.key(3, -4);
            long c2 = ProtectedChunkStore.key(-100, 200);
            check(a.protect(c1) && a.protect(c2) && !a.protect(c1), "protect adds once");
            check(a.isProtected(c1) && !a.isProtected(ProtectedChunkStore.key(0, 0)), "isProtected");
            check(!a.flagAllowed(c1, false) && a.flagAllowed(c1, true) && a.flagAllowed(ProtectedChunkStore.key(0, 0), false), "flagAllowed");
            a.tickAutosave();
            check(!Files.exists(dir.resolve(ProtectedChunkStore.fileName(server, dim))), "autosave waits for its delay");
            a.flushAndWait();
            Path file = dir.resolve(ProtectedChunkStore.fileName(server, dim));
            check(Files.isRegularFile(file), "flush writes the file");
            check(!Files.exists(file.resolveSibling(file.getFileName() + ".tmp")), "no temp file left");

            ProtectedChunkStore b = new ProtectedChunkStore(() -> dir, null);
            b.select(server, dim);
            check(b.isProtected(c1) && b.isProtected(c2), "the chunks come back from the file");
            b.select(server, "minecraft:the_nether");
            check(!b.isProtected(c1), "other dimension has its own set");
            b.select(server, dim);
            check(b.isProtected(c1), "and the first one is still there");
            b.select(null, null);
            check(!b.isProtected(c1) && !b.protect(c1), "no world selected = nothing protected");

            Path legacyFile = legacy.resolve(ProtectedChunkStore.fileName("old.server", dim));
            Files.copy(file, legacyFile);
            Files.writeString(legacyFile, Files.readString(file).replace("example.com", "old.server"));
            ProtectedChunkStore c = new ProtectedChunkStore(() -> dir, () -> legacy);
            c.select("old.server", dim);
            check(c.isProtected(c1), "old file is read when there is no new one");
            c.flushAndWait();
            check(Files.isRegularFile(dir.resolve(ProtectedChunkStore.fileName("old.server", dim))), "and written to the new folder");

            String badName = ProtectedChunkStore.fileName("bad.server", dim);
            Files.writeString(dir.resolve(badName), "{ this is not json");
            ProtectedChunkStore d = new ProtectedChunkStore(() -> dir, null);
            d.select("bad.server", dim);
            d.protect(c1);
            d.flushAndWait();
            check(Files.readString(dir.resolve(badName)).equals("{ this is not json"), "unreadable file is never overwritten");

            String newName = ProtectedChunkStore.fileName("new.server", dim);
            String future = "{\"version\":99,\"server\":\"x\",\"dimension\":\"y\",\"count\":1,\"chunks\":[1,2]}";
            Files.writeString(dir.resolve(newName), future);
            d.select("new.server", dim);
            check(!d.isProtected(ProtectedChunkStore.key(1, 2)), "newer file version is not used");
            d.protect(c1);
            d.flushAndWait();
            check(Files.readString(dir.resolve(newName)).equals(future), "newer file version is not overwritten");

            String oddName = ProtectedChunkStore.fileName("odd.server", dim);
            Files.writeString(dir.resolve(oddName),
                    "{\"version\":1,\"count\":99999999,\"unknown\":{\"a\":[1]},\"chunks\":[1,2,\"x\",4,1.5,3,7,-8,5]}");
            d.select("odd.server", dim);
            check(d.isProtected(ProtectedChunkStore.key(1, 2)) && d.isProtected(ProtectedChunkStore.key(7, -8)), "good pairs are read");
            check(!d.isProtected(ProtectedChunkStore.key(4, 4)) && !d.isProtected(ProtectedChunkStore.key(3, 3)), "pairs with a bad number are dropped");
        } finally {
            deleteAll(dir);
            deleteAll(legacy);
        }
    }

    static void deleteAll(Path dir) throws IOException {
        try (var files = Files.walk(dir)) {
            for (Path p : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    static void areas() {
        ChunkAreas areas = new ChunkAreas();
        LongOpenHashSet set = new LongOpenHashSet();
        areas.update(set);
        check(areas.count() == 0, "no chunks, no areas");
        set.add(ProtectedChunkStore.key(0, 0));
        areas.update(set);
        check(areas.count() == 1 && areas.x(0) == 8.0 && areas.z(0) == 8.0, "single chunk: its middle");
        set.add(ProtectedChunkStore.key(1, 1));
        areas.update(set);
        check(areas.count() == 1 && areas.x(0) == 16.0 && areas.z(0) == 16.0, "diagonal chunks are one area");
        set.add(ProtectedChunkStore.key(10, 0));
        areas.update(set);
        check(areas.count() == 2, "a far chunk is a second area");
        set.remove(ProtectedChunkStore.key(10, 0));
        areas.update(set);
        check(areas.count() == 1, "the cache follows the set");

        LongOpenHashSet u = new LongOpenHashSet();
        for (int x = 0; x < 5; x++) {
            u.add(ProtectedChunkStore.key(x, 0));
        }
        u.add(ProtectedChunkStore.key(0, 1));
        u.add(ProtectedChunkStore.key(4, 1));
        u.add(ProtectedChunkStore.key(0, 2));
        u.add(ProtectedChunkStore.key(4, 2));
        ChunkAreas ua = new ChunkAreas();
        ua.update(u);
        long point = ProtectedChunkStore.key((int) Math.floor(ua.x(0)) >> 4, (int) Math.floor(ua.z(0)) >> 4);
        check(ua.count() == 1 && u.contains(point), "the area point is always inside a flagged chunk");

        double x0 = ua.x(0);
        ua.update(u);
        check(ua.x(0) == x0 && ua.count() == 1, "same set, same result");

        LongOpenHashSet neg = new LongOpenHashSet();
        neg.add(ProtectedChunkStore.key(-1, -1));
        ChunkAreas na = new ChunkAreas();
        na.update(neg);
        check(na.x(0) == -8.0 && na.z(0) == -8.0, "negative chunk middle");
    }

    static void set(byte[] section, int x, int y, int z, int value) {
        int index = LightDiff.blockIndex(x, y, z);
        int b = section[index >> 1] & 0xFF;
        b = (index & 1) == 0 ? b & 0xF0 | value : b & 0x0F | value << 4;
        section[index >> 1] = (byte) b;
    }

    static void lightDiff() {
        byte[] sec = new byte[2048];
        set(sec, 3, 5, 7, 11);
        check(LightDiff.nibble(sec, LightDiff.blockIndex(3, 5, 7)) == 11, "nibble read back (odd index)");
        set(sec, 4, 5, 7, 6);
        check(LightDiff.nibble(sec, LightDiff.blockIndex(4, 5, 7)) == 6 && LightDiff.nibble(sec, LightDiff.blockIndex(3, 5, 7)) == 11, "neighbour nibbles stay apart");

        byte[] before = new byte[2048];
        byte[] after = new byte[2048];
        set(after, 3, 5, 7, 12);
        LightDiff.Summary s = new LightDiff.Summary();
        LightDiff.compareSection(before, after, -2, 3, -4, null, s);
        check(s.count == 1 && s.x == -2 * 16 + 3 && s.y == -4 * 16 + 5 && s.z == 3 * 16 + 7, "position of the change: " + s.x + "," + s.y + "," + s.z);
        check(s.oldValue == 0 && s.newValue == 12, "values of the change");

        after = new byte[2048];
        set(after, 0, 0, 0, 4);
        set(after, 1, 0, 0, 9);
        s = new LightDiff.Summary();
        LightDiff.compareSection(before, after, 0, 0, 0, null, s);
        check(s.count == 2 && s.newValue == 9 && s.x == 1, "both nibbles of a byte, the biggest change is the one kept");

        s = new LightDiff.Summary();
        LightDiff.compareSection(after, after.clone(), 0, 0, 0, null, s);
        check(s.count == 0, "same light = no change");

        s = new LightDiff.Summary();
        byte[] lit = new byte[2048];
        set(lit, 15, 15, 15, 14);
        LightDiff.compareSection(lit, before, 1, 1, 1, null, s);
        check(s.count == 1 && s.oldValue == 14 && s.newValue == 0 && s.x == 31 && s.y == 31 && s.z == 31, "a light that goes out is a change too");

        s = new LightDiff.Summary();
        byte[] two = new byte[2048];
        set(two, 1, 1, 1, 8);
        set(two, 9, 9, 9, 8);
        LightDiff.compareSection(before, two, 0, 0, 0, (x, y, z, o, n) -> x == 1, s);
        check(s.count == 1 && s.x == 9, "explained changes are left out");

        Map<Integer, byte[]> sections = LightDiff.zeroedChunk(new HashMap<>(), -4, 19);
        check(sections.size() == 24 && sections.get(-4) == LightDiff.ZERO_SECTION && sections.get(19) == LightDiff.ZERO_SECTION, "zeroed chunk");
        byte[] lampLight = new byte[2048];
        set(lampLight, 2, 2, 2, 10);
        LightDiff.Summary base = new LightDiff.Summary();

        LightDiff.apply(sections, 7, 7, bits(0, 1, 25), List.of(lampLight, lampLight, lampLight), new BitSet(), -5, -4, 19, false, null, base);
        check(base.count == 0, "baseline does not report");
        check(sections.get(-4) != LightDiff.ZERO_SECTION && sections.get(-4)[LightDiff.blockIndex(2, 2, 2) >> 1] != 0, "data goes to light section + offset");
        check(!sections.containsKey(-5) && !sections.containsKey(20), "outer light sections are not stored");
        check(sections.get(-4) != lampLight, "the packet array is copied");

        byte[] changed = new byte[2048];
        set(changed, 5, 5, 5, 7);
        LightDiff.Summary sum = new LightDiff.Summary();
        LightDiff.apply(sections, 7, 7, bits(1), List.of(changed), new BitSet(), -5, -4, 19, true, null, sum);
        check(sum.count == 2, "two blocks changed: " + sum.count);
        check(sum.x == 7 * 16 + 2 && sum.y == -4 * 16 + 2 && sum.z == 7 * 16 + 2 && sum.oldValue == 10 && sum.newValue == 0, "the biggest change (10 -> 0) is reported");

        LightDiff.Summary sum2 = new LightDiff.Summary();
        LightDiff.apply(sections, 7, 7, new BitSet(), List.of(), bits(1), -5, -4, 19, true, null, sum2);
        check(sum2.count == 1 && sum2.oldValue == 7 && sum2.newValue == 0 && sections.get(-4) == LightDiff.ZERO_SECTION, "empty-mask section goes dark and is compared");

        LightDiff.Summary sum3 = new LightDiff.Summary();
        LightDiff.apply(sections, 0, 0, bits(2, 3), List.of(new byte[100]), new BitSet(), -5, -4, 19, true, null, sum3);
        check(sum3.count == 0 && sections.get(-3) == LightDiff.ZERO_SECTION, "an array of the wrong size is ignored");
        LightDiff.apply(sections, 0, 0, bits(2), List.of(changed), new BitSet(), -5, -4, 19, true, null, null);
        check(sections.get(-3) != LightDiff.ZERO_SECTION, "without a summary the light is stored anyway");

        Map<Integer, byte[]> partial = new HashMap<>();
        LightDiff.Summary sum4 = new LightDiff.Summary();
        LightDiff.apply(partial, 0, 0, bits(1), List.of(changed), new BitSet(), -5, -4, 19, true, null, sum4);
        check(sum4.count == 0 && partial.containsKey(-4), "unknown previous light is no change");

        check(LightDiff.lampExplains(0, 0, 0, 14, 3, 0, 0, 11), "lamp: distance 3 explains light 11");
        check(!LightDiff.lampExplains(0, 0, 0, 14, 4, 0, 0, 11), "lamp: distance 4 does not explain light 11");
        check(LightDiff.lampExplains(0, 0, 0, 14, 0, 0, 0, 14), "lamp: its own block");
        check(LightDiff.lampExplains(0, 0, 0, 14, 1, 1, 1, 11), "lamp: manhattan, not euclid");
        check(!LightDiff.lampExplains(0, 0, 0, 12, 0, 0, 0, 13), "a weaker light source does not explain a brighter value");
        check(LightDiff.oreExplains(0, 0, 0, 2, 0, 0, 7) && !LightDiff.oreExplains(0, 0, 0, 3, 0, 0, 7), "redstone ore reach = 9 - value");
        check(!LightDiff.oreExplains(0, 0, 0, 0, 0, 0, 10), "redstone ore never explains more than 9");
        check(LightDiff.amethystExplains(0, 0, 0, 5, 0, 0, 0, 5), "amethyst: reach 5, light 5");
        check(!LightDiff.amethystExplains(0, 0, 0, 3, 3, 0, 0, 5), "amethyst: 6 away is too far");
        check(!LightDiff.amethystExplains(0, 0, 0, 1, 0, 0, 2, 6), "amethyst: light above 5 is something else");
        check(LightDiff.amethystExplains(0, 0, 0, 1, 0, 0, 5, 0), "amethyst: fading out counts as well");
        check(LightDiff.manhattan(-1, -2, -3, 1, 2, 3) == 12, "manhattan distance");

        LightDiff.Summary m = new LightDiff.Summary();
        m.add(1, 1, 1, 0, 3);
        m.add(2, 2, 2, 10, 0);
        m.add(3, 3, 3, 5, 7);
        check(m.count == 3 && m.x == 2 && m.oldValue == 10, "summary keeps the biggest change and counts all");
    }
}
