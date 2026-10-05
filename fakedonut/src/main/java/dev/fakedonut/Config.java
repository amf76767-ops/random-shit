package dev.fakedonut;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class Config {
    public double baseChance = 0.06;
    public int baseMinKelp = 4;
    public int baseY = -40;
    public int seaMinDepth = 6;
    public int kelpPerBase = 8;
    public double spawnerChance = 0.5;
    public boolean antiXray = true;
    public int hideBelowY = 8;
    public boolean hideClosedRooms = true;
    public int breakRevealRadius = 2;
    public int proximityRadius = 4;
    public long startBalance = 1_000_000L;
    public int minListings = 60;
    public int listingHours = 48;

    private static Config current = new Config();

    public static Config get() {
        return current;
    }

    public static Path dir() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("fakedonut");
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            FakeDonut.LOG.warn("cannot create {}", p, e);
        }
        return p;
    }

    public static void load() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Path file = dir().resolve("config.json");
        try {
            if (Files.isRegularFile(file)) {
                Config read = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), Config.class);
                if (read != null) {
                    current = read;
                }
            }
            Files.writeString(file, gson.toJson(current), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            FakeDonut.LOG.warn("config.json could not be read, defaults are used", e);
        }
    }
}
