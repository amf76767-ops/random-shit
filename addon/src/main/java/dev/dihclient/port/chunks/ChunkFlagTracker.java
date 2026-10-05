package dev.dihclient.port.chunks;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.BitSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ported from an open-source client (GPL-3.0). */
public final class ChunkFlagTracker {

    public interface Protection {
        boolean flagAllowed(long chunk, boolean alreadyFlagged);
    }

    @FunctionalInterface
    public interface SpottedListener {
        void onChunkSpotted(int chunkX, int chunkZ);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("DIHClient");

    private final Long2ObjectOpenHashMap<ChunkLightSnapshot> snapshots = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet alerted = new LongOpenHashSet();
    private final Long2ObjectOpenHashMap<ChunkLightSnapshot> held = new Long2ObjectOpenHashMap<>();
    private final Protection protection;
    private volatile SpottedListener spottedListener;

    public ChunkFlagTracker(Protection protection) {
        this.protection = protection;
    }

    public void clear() {
        this.snapshots.clear();
        this.alerted.clear();
        this.held.clear();
    }

    public void setSpottedListener(SpottedListener listener) {
        this.spottedListener = listener;
    }

    public Long2ObjectMap<ChunkLightSnapshot> snapshots() {
        return this.snapshots;
    }

    public boolean isFlagged(long chunkKey, ChunkLightSnapshot snapshot) {
        return this.alerted.contains(chunkKey) || snapshot.hasBaseSignal();
    }

    public boolean isFlagged(long chunkKey) {
        ChunkLightSnapshot snapshot = this.snapshots.get(chunkKey);
        return snapshot != null && this.isFlagged(chunkKey, snapshot);
    }

    public boolean isFlaggedOrHeld(long chunkKey) {
        return this.isFlagged(chunkKey) || this.held.containsKey(chunkKey);
    }

    public void ingest(int chunkX, int chunkZ, BitSet skyMask, BitSet skyEmpty, List<byte[]> skyNibbles, int sectionCount, int bottomSectionY) {
        long key = ProtectedChunkStore.key(chunkX, chunkZ);
        if (this.held.containsKey(key)) {
            this.hold(chunkX, chunkZ, skyMask, skyEmpty, skyNibbles, sectionCount, bottomSectionY);
        }
        ChunkLightSnapshot snapshot = this.snapshots.get(key);
        boolean wasFlagged = snapshot != null && this.isFlagged(key, snapshot);
        if (snapshot == null || snapshot.sectionCount != sectionCount || snapshot.bottomSectionY != bottomSectionY) {
            snapshot = new ChunkLightSnapshot(sectionCount, bottomSectionY);
            this.snapshots.put(key, snapshot);
        }
        try {
            LightDataClassifier.applyTrack(skyMask, skyEmpty, skyNibbles, snapshot.sky, snapshot.signalSections());
        } catch (Throwable t) {
            LOGGER.error("[DIHClient] Player Bypass light ingest failed for chunk {}, {}", chunkX, chunkZ, t);
            return;
        }
        boolean flagged = snapshot.hasBaseSignal();
        if (flagged && !this.protection.flagAllowed(key, wasFlagged)) {
            this.snapshots.remove(key);
        } else if (flagged) {
            SpottedListener listener = this.spottedListener;
            if (listener != null && this.alerted.add(key)) {
                listener.onChunkSpotted(chunkX, chunkZ);
            }
        } else if (!this.alerted.contains(key)) {
            this.snapshots.remove(key);
        }
    }

    public void hold(int chunkX, int chunkZ, BitSet skyMask, BitSet skyEmpty, List<byte[]> skyNibbles, int sectionCount, int bottomSectionY) {
        long key = ProtectedChunkStore.key(chunkX, chunkZ);
        ChunkLightSnapshot snapshot = this.held.get(key);
        if (snapshot == null || snapshot.sectionCount != sectionCount || snapshot.bottomSectionY != bottomSectionY) {
            snapshot = new ChunkLightSnapshot(sectionCount, bottomSectionY);
        }
        try {
            LightDataClassifier.applyTrack(skyMask, skyEmpty, skyNibbles, snapshot.sky, snapshot.signalSections());
        } catch (Throwable t) {
            LOGGER.error("[DIHClient] Player Bypass light hold failed for chunk {}, {}", chunkX, chunkZ, t);
            return;
        }
        if (snapshot.hasBaseSignal() && this.protection.flagAllowed(key, false)) {
            this.held.put(key, snapshot);
        } else {
            this.held.remove(key);
        }
    }
}
