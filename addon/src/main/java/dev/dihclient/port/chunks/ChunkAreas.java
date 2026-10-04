package dev.dihclient.port.chunks;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Ported from an open-source client (GPL-3.0).
 * Groups flagged chunks into connected areas (8 neighbours) and gives one point per area for the tracer: the middle of the
 * area, moved to the nearest chunk of the area when the middle is not inside it. The result is cached until the set changes.
 */
public final class ChunkAreas {
    private final DoubleArrayList areaX = new DoubleArrayList();
    private final DoubleArrayList areaZ = new DoubleArrayList();
    private int areasSize = -1;
    private int areasHash;
    private final LongOpenHashSet seen = new LongOpenHashSet();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final LongArrayList area = new LongArrayList();

    /** Recomputes the areas when the set is not the one from the last call. */
    public void update(LongSet chunks) {
        int hash = chunks.hashCode();
        if (chunks.size() == this.areasSize && hash == this.areasHash) {
            return;
        }
        this.areasSize = chunks.size();
        this.areasHash = hash;
        this.areaX.clear();
        this.areaZ.clear();
        this.seen.clear();
        LongIterator it = chunks.iterator();
        while (it.hasNext()) {
            long start = it.nextLong();
            if (!this.seen.add(start)) {
                continue;
            }
            this.area.clear();
            this.queue.enqueue(start);
            double sumX = 0.0;
            double sumZ = 0.0;
            while (!this.queue.isEmpty()) {
                long key = this.queue.dequeueLong();
                this.area.add(key);
                int cx = ProtectedChunkStore.chunkX(key);
                int cz = ProtectedChunkStore.chunkZ(key);
                sumX += (cx << 4) + 8.0;
                sumZ += (cz << 4) + 8.0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long next = ProtectedChunkStore.key(cx + dx, cz + dz);
                        if (chunks.contains(next) && this.seen.add(next)) {
                            this.queue.enqueue(next);
                        }
                    }
                }
            }
            double mx = sumX / this.area.size();
            double mz = sumZ / this.area.size();
            if (!chunks.contains(ProtectedChunkStore.key((int) Math.floor(mx) >> 4, (int) Math.floor(mz) >> 4))) {
                double best = Double.MAX_VALUE;
                double bx = mx;
                double bz = mz;
                for (int i = 0; i < this.area.size(); i++) {
                    long key = this.area.getLong(i);
                    double x = (ProtectedChunkStore.chunkX(key) << 4) + 8.0;
                    double z = (ProtectedChunkStore.chunkZ(key) << 4) + 8.0;
                    double d = (x - mx) * (x - mx) + (z - mz) * (z - mz);
                    if (d < best) {
                        best = d;
                        bx = x;
                        bz = z;
                    }
                }
                mx = bx;
                mz = bz;
            }
            this.areaX.add(mx);
            this.areaZ.add(mz);
        }
    }

    public int count() {
        return this.areaX.size();
    }

    public double x(int index) {
        return this.areaX.getDouble(index);
    }

    public double z(int index) {
        return this.areaZ.getDouble(index);
    }
}
