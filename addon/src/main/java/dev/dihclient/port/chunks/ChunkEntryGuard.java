package dev.dihclient.port.chunks;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * Ported from an open-source client (GPL-3.0).
 * When the player walks into a chunk that is not flagged, the chunks around it are "protected": they were fine when you stood
 * there, so a later light change there (your own base) must not raise an alarm.
 * The store is reached through {@code protect}, so the guard itself has no game or file code.
 */
public final class ChunkEntryGuard {
    private final LongOpenHashSet waiting = new LongOpenHashSet();
    private boolean inChunk;
    private long chunk;
    private boolean entryPending;

    public void reset() {
        this.inChunk = false;
        this.entryPending = false;
        this.waiting.clear();
    }

    /** @param arrived whether the chunk is loaded · @param flagged whether it is flagged (or held back) already */
    public void update(long playerChunk, LongPredicate arrived, LongPredicate flagged, LongConsumer protect) {
        if (!this.inChunk || playerChunk != this.chunk) {
            this.inChunk = true;
            this.chunk = playerChunk;
            this.entryPending = true;
            this.waiting.clear();
        }
        if (this.entryPending) {
            if (arrived.test(playerChunk)) {
                this.entryPending = false;
                if (!flagged.test(playerChunk)) {
                    ProtectedChunkStore.forEachInArea(ProtectedChunkStore.chunkX(playerChunk), ProtectedChunkStore.chunkZ(playerChunk), key -> {
                        if (!arrived.test(key)) {
                            this.waiting.add(key);
                        } else if (!flagged.test(key)) {
                            protect.accept(key);
                        }
                    });
                }
            }
        } else if (!this.waiting.isEmpty()) {
            LongIterator keys = this.waiting.iterator();
            while (keys.hasNext()) {
                long key = keys.nextLong();
                if (arrived.test(key)) {
                    keys.remove();
                    if (!flagged.test(key)) {
                        protect.accept(key);
                    }
                }
            }
        }
    }
}
