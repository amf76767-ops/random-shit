package dev.dihclient.port.chunks;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/** Ported from an open-source client (GPL-3.0). */
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
