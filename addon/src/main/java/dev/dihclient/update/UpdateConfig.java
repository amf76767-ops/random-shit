package dev.dihclient.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Settings of the update check, stored in {@code config/dihclient/update.json}. */
public final class UpdateConfig {
    public static final String DEFAULT_REPO = "amf76767-ops/random-shit";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** GitHub repository as {@code owner/name} whose releases are checked. */
    public String repo = DEFAULT_REPO;
    public boolean checkForUpdates = true;
    public boolean includePrereleases = false;
    /** A version the player chose "Skip this version" for. */
    public String skippedVersion = "";
    /** Hours between two checks while the game keeps running. */
    public int checkEveryHours = 6;

    public static UpdateConfig load(Path file) {
        try {
            if (Files.exists(file)) {
                UpdateConfig c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), UpdateConfig.class);
                if (c != null) {
                    c.sanitize();
                    return c;
                }
            }
        } catch (Exception ignored) {
            // a broken file falls back to the defaults and is rewritten below
        }
        UpdateConfig c = new UpdateConfig();
        c.save(file);
        return c;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // not being able to save must never break the game
        }
    }

    private void sanitize() {
        if (repo == null || !repo.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+")) {
            repo = DEFAULT_REPO;
        }
        if (skippedVersion == null) {
            skippedVersion = "";
        }
        checkEveryHours = Math.max(1, Math.min(168, checkEveryHours));
    }
}
