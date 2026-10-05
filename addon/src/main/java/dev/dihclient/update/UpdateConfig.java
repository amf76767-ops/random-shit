package dev.dihclient.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class UpdateConfig {
    public static final String DEFAULT_REPO = "amf76767-ops/random-shit";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String repo = DEFAULT_REPO;
    public boolean checkForUpdates = true;
    public boolean includePrereleases = false;

    public String skippedVersion = "";

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
