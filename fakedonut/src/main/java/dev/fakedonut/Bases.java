package dev.fakedonut;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Bases {
    public static final class Base {
        public int x;
        public int y;
        public int z;
        public int kelpX;
        public int kelpZ;
        public int kelpTop;
        public int kelpFloor;
        public boolean spawner;
        public String owner;
    }

    public static final int HALF = 5;
    public static final int HEIGHT = 7;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Base> ALL = new ArrayList<>();
    private static final Map<Long, List<Base>> BY_CHUNK = new HashMap<>();

    private Bases() {
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    public static synchronized void load() {
        ALL.clear();
        BY_CHUNK.clear();
        try {
            java.nio.file.Path f = Config.dir().resolve("bases.json");
            if (Files.isRegularFile(f)) {
                List<Base> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<List<Base>>() { }.getType());
                if (read != null) {
                    for (Base b : read) {
                        index(b);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            FakeDonut.LOG.warn("bases.json could not be read", e);
        }
    }

    private static void index(Base b) {
        ALL.add(b);
        for (int cx = (b.x - HALF) >> 4; cx <= (b.x + HALF) >> 4; cx++) {
            for (int cz = (b.z - HALF) >> 4; cz <= (b.z + HALF) >> 4; cz++) {
                BY_CHUNK.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(b);
            }
        }
    }

    public static synchronized void add(Base b) {
        index(b);
        save();
    }

    public static synchronized void save() {
        try {
            Files.writeString(Config.dir().resolve("bases.json"), GSON.toJson(ALL), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FakeDonut.LOG.warn("bases.json could not be written", e);
        }
    }

    public static synchronized List<Base> all() {
        return new ArrayList<>(ALL);
    }

    public static synchronized List<Base> inChunk(int cx, int cz) {
        List<Base> l = BY_CHUNK.get(key(cx, cz));
        return l == null ? List.of() : new ArrayList<>(l);
    }

    public static boolean inside(Base b, int x, int y, int z) {
        return Math.abs(x - b.x) <= HALF && Math.abs(z - b.z) <= HALF && y >= b.y - 1 && y <= b.y - 1 + HEIGHT - 1;
    }

    public static synchronized boolean insideAny(int x, int y, int z) {
        List<Base> l = BY_CHUNK.get(key(x >> 4, z >> 4));
        if (l == null) {
            return false;
        }
        for (Base b : l) {
            if (inside(b, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean chunkHas(int cx, int cz) {
        return BY_CHUNK.containsKey(key(cx, cz));
    }
}
