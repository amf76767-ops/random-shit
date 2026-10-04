package dev.dihclient.port.chunks;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ported from an open-source client (GPL-3.0).
 * The chunks that must never be flagged (the ones around where you have been), per server and dimension, kept in
 * {@code config/dihclient/playerbypass-protected}. Files from the old folder are picked up once when no new file exists.
 * Game thread only; the files are written on a background thread from a copy.
 */
public final class ProtectedChunkStore implements ChunkFlagTracker.Protection {
    public static final int RADIUS = 2;
    static final int CONFIG_VERSION = 1;
    static final long SAVE_DELAY_NANOS = 10_000_000_000L;
    private static final long STOP_WAIT_SECONDS = 10L;
    private static final String DEFAULT_PORT_SUFFIX = ":25565";
    private static final int NAME_PART_LENGTH = 40;
    private static final long NOT_AN_INT = Long.MIN_VALUE;
    // not DIHClient.LOG: the class stays loadable without the game (unit tests)
    private static final Logger LOGGER = LoggerFactory.getLogger("DIHClient");
    private static final ProtectedChunkStore INSTANCE = new ProtectedChunkStore(
            () -> FabricLoader.getInstance().getConfigDir().resolve("dihclient").resolve("playerbypass-protected"),
            null);

    private final Supplier<Path> directory;
    private final Supplier<Path> legacyDirectory;
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final Executor writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "DIHClient Player Bypass saves");
        thread.setDaemon(true);
        return thread;
    });
    private final ConcurrentHashMap<Path, long[]> queued = new ConcurrentHashMap<>();
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);
    private volatile boolean writeFailed;
    private Bucket active;
    private boolean dirty;
    private long dirtySince;

    /** @param legacyDirectory folder of the old files, or null */
    public ProtectedChunkStore(Supplier<Path> directory, Supplier<Path> legacyDirectory) {
        this.directory = directory;
        this.legacyDirectory = legacyDirectory;
    }

    public static ProtectedChunkStore get() {
        return INSTANCE;
    }

    public static long key(int chunkX, int chunkZ) {
        return chunkX & 0xFFFFFFFFL | (chunkZ & 0xFFFFFFFFL) << 32;
    }

    public static int chunkX(long key) {
        return (int) key;
    }

    public static int chunkZ(long key) {
        return (int) (key >>> 32);
    }

    public static String serverKey(String address) {
        String key = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        if (key.endsWith(DEFAULT_PORT_SUFFIX)) {
            key = key.substring(0, key.length() - DEFAULT_PORT_SUFFIX.length());
        }
        return key.isEmpty() ? "unknown" : key;
    }

    public static String singleplayerKey(String worldFolder) {
        return "singleplayer/" + worldFolder;
    }

    public static void forEachInArea(int centerX, int centerZ, LongConsumer action) {
        for (int x = centerX - RADIUS; x <= centerX + RADIUS; x++) {
            for (int z = centerZ - RADIUS; z <= centerZ + RADIUS; z++) {
                action.accept(key(x, z));
            }
        }
    }

    public static String fileName(String server, String dimension) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((server + "\n" + dimension).getBytes(StandardCharsets.UTF_8));
            String hash = HexFormat.of().formatHex(digest, 0, 8);
            return readable(server) + "_" + readable(dimension) + "-" + hash + ".json";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    public void select(String server, String dimension) {
        if (server == null || dimension == null) {
            this.active = null;
            return;
        }
        String name = fileName(server, dimension);
        Bucket bucket = this.buckets.get(name);
        if (bucket == null) {
            bucket = new Bucket(this.directory.get().resolve(name), server, dimension);
            Path source = bucket.file;
            if (!Files.isRegularFile(source) && this.legacyDirectory != null) {
                Path old = this.legacyDirectory.get().resolve(name);
                if (Files.isRegularFile(old)) {
                    source = old;
                }
            }
            load(bucket, source);
            if (source != bucket.file && !bucket.readOnly && !bucket.chunks.isEmpty()) {
                this.changed(bucket); // copy the old file to the new place
            }
            this.buckets.put(name, bucket);
        }
        this.active = bucket;
    }

    public boolean isProtected(long chunk) {
        return this.active != null && this.active.chunks.contains(chunk);
    }

    public boolean protect(long chunk) {
        if (this.active != null && this.active.chunks.add(chunk)) {
            this.changed(this.active);
            return true;
        }
        return false;
    }

    @Override
    public boolean flagAllowed(long chunk, boolean alreadyFlagged) {
        return alreadyFlagged || !this.isProtected(chunk);
    }

    public void tickAutosave() {
        this.retryFailedWrites();
        if (this.dirty && System.nanoTime() - this.dirtySince >= SAVE_DELAY_NANOS) {
            this.saveChanged();
        }
    }

    public void flush() {
        this.retryFailedWrites();
        if (this.dirty) {
            this.saveChanged();
        }
    }

    public void flushAndWait() {
        this.flush();
        try {
            this.lastWrite.get(STOP_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException | ExecutionException e) {
            LOGGER.warn("[DIHClient] Player Bypass protected chunks may not all be saved", e);
        }
    }

    private void changed(Bucket bucket) {
        if (!bucket.readOnly) {
            bucket.dirty = true;
            if (!this.dirty) {
                this.dirty = true;
                this.dirtySince = System.nanoTime();
            }
        }
    }

    private void retryFailedWrites() {
        if (this.writeFailed) {
            this.writeFailed = false;
            for (Bucket bucket : this.buckets.values()) {
                if (bucket.writeFailed) {
                    bucket.writeFailed = false;
                    this.changed(bucket);
                }
            }
        }
    }

    private void saveChanged() {
        this.dirty = false;
        for (Bucket bucket : this.buckets.values()) {
            if (bucket.dirty) {
                bucket.dirty = false;
                this.save(bucket);
            }
        }
    }

    private void save(Bucket bucket) {
        // a newer snapshot replaces one that is still waiting; only the first call starts a write
        if (this.queued.put(bucket.file, bucket.chunks.toLongArray()) == null) {
            this.lastWrite = CompletableFuture.runAsync(() -> this.writeQueued(bucket), this.writer);
        }
    }

    private void writeQueued(Bucket bucket) {
        long[] chunks = this.queued.remove(bucket.file);
        if (chunks != null) {
            try {
                write(bucket, chunks);
            } catch (RuntimeException | IOException e) {
                LOGGER.warn("[DIHClient] Failed to save Player Bypass protected chunks to {}", bucket.file, e);
                bucket.writeFailed = true;
                this.writeFailed = true;
            }
        }
    }

    private static void write(Bucket bucket, long[] chunks) throws IOException {
        Files.createDirectories(bucket.file.getParent());
        Path tmp = bucket.file.resolveSibling(bucket.file.getFileName() + ".tmp");
        try (JsonWriter json = new JsonWriter(Files.newBufferedWriter(tmp))) {
            json.beginObject();
            json.name("version").value((long) CONFIG_VERSION);
            json.name("server").value(bucket.server);
            json.name("dimension").value(bucket.dimension);
            json.name("count").value((long) chunks.length);
            json.name("chunks").beginArray();
            for (long chunk : chunks) {
                json.value((long) chunkX(chunk)).value((long) chunkZ(chunk));
            }
            json.endArray();
            json.endObject();
        }
        try {
            Files.move(tmp, bucket.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, bucket.file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** A file that cannot be read stays untouched on disk: the bucket turns read-only so it is never overwritten. */
    private static void load(Bucket bucket, Path source) {
        if (!Files.isRegularFile(source)) {
            return;
        }
        int version = 0;
        try (JsonReader json = new JsonReader(Files.newBufferedReader(source))) {
            long maxChunks = Files.size(source) / 4L;
            json.beginObject();
            while (json.hasNext()) {
                switch (json.nextName()) {
                    case "version" -> version = json.nextInt();
                    case "count" -> {
                        long count = wholeInt(json);
                        if (count > 0L) {
                            bucket.chunks.ensureCapacity((int) Math.min(count, maxChunks));
                        }
                    }
                    case "chunks" -> {
                        if (json.peek() == JsonToken.BEGIN_ARRAY) {
                            readChunks(json, bucket.chunks);
                        } else {
                            json.skipValue();
                        }
                    }
                    default -> json.skipValue();
                }
            }
            json.endObject();
        } catch (RuntimeException | IOException e) {
            LOGGER.warn("[DIHClient] Failed to load Player Bypass protected chunks from {}; preserving the file", source, e);
            bucket.chunks.clear();
            bucket.readOnly = true;
            return;
        }
        if (version > CONFIG_VERSION) {
            LOGGER.warn("[DIHClient] Player Bypass protected chunks file {} is version {}, newer than supported {}; skipping load", source, version,
                    CONFIG_VERSION);
            bucket.chunks.clear();
            bucket.readOnly = true;
        }
    }

    private static void readChunks(JsonReader json, LongOpenHashSet into) throws IOException {
        json.beginArray();
        int index = 0;
        long x = NOT_AN_INT;
        while (json.hasNext()) {
            long value = wholeInt(json);
            if ((index++ & 1) == 0) {
                x = value;
            } else if (x != NOT_AN_INT && value != NOT_AN_INT) {
                into.add(key((int) x, (int) value));
            }
        }
        json.endArray();
    }

    private static long wholeInt(JsonReader json) throws IOException {
        if (json.peek() != JsonToken.NUMBER) {
            json.skipValue();
            return NOT_AN_INT;
        }
        double value = json.nextDouble();
        return value == Math.rint(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? (long) value : NOT_AN_INT;
    }

    private static String readable(String key) {
        StringBuilder out = new StringBuilder(Math.min(key.length(), NAME_PART_LENGTH));
        for (int i = 0; i < key.length() && out.length() < NAME_PART_LENGTH; i++) {
            char c = Character.toLowerCase(key.charAt(i));
            out.append((c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '.' && c != '-' ? '_' : c);
        }
        return out.toString();
    }

    private static final class Bucket {
        final Path file;
        final String server;
        final String dimension;
        final LongOpenHashSet chunks = new LongOpenHashSet();
        boolean readOnly;
        boolean dirty;
        volatile boolean writeFailed;

        Bucket(Path file, String server, String dimension) {
            this.file = file;
            this.server = server;
            this.dimension = dimension;
        }
    }
}
