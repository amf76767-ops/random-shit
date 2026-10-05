package dev.fakedonut;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class Economy {
    private static final Map<UUID, Long> BALANCES = new HashMap<>();
    private static final Gson GSON = new Gson();

    private Economy() {
    }

    public static synchronized long balance(UUID id) {
        return BALANCES.computeIfAbsent(id, k -> Config.get().startBalance);
    }

    public static synchronized void add(UUID id, long amount) {
        BALANCES.put(id, balance(id) + amount);
    }

    public static synchronized boolean take(UUID id, long amount) {
        long have = balance(id);
        if (have < amount) {
            return false;
        }
        BALANCES.put(id, have - amount);
        return true;
    }

    public static String money(long amount) {
        return "$" + NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }

    private static Path file() {
        return Config.dir().resolve("balances.json");
    }

    public static synchronized void load() {
        BALANCES.clear();
        try {
            if (Files.isRegularFile(file())) {
                Map<String, Long> raw = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), new TypeToken<Map<String, Long>>() { }.getType());
                if (raw != null) {
                    raw.forEach((k, v) -> BALANCES.put(UUID.fromString(k), v));
                }
            }
        } catch (IOException | RuntimeException e) {
            FakeDonut.LOG.warn("balances.json could not be read", e);
        }
    }

    public static synchronized void save() {
        Map<String, Long> raw = new HashMap<>();
        BALANCES.forEach((k, v) -> raw.put(k.toString(), v));
        try {
            Files.writeString(file(), GSON.toJson(raw), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FakeDonut.LOG.warn("balances.json could not be written", e);
        }
    }
}
