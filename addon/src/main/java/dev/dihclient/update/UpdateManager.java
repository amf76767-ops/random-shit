package dev.dihclient.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Looks for a newer DIHClient on GitHub in the background and, when the player agrees, downloads it.
 * Everything here is free of Minecraft classes; the screen that asks the player lives in the glue package.
 */
public final class UpdateManager {
    public enum State { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, FAILED }

    private static final long FIRST_CHECK_MS = 8_000;
    private static final long RETRY_AFTER_FAILURE_MS = 30 * 60_000L;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DIHClient-updater");
        t.setDaemon(true);
        return t;
    });

    private static Path configFile;
    private static Path ownJar;
    private static UpdateConfig config = new UpdateConfig();
    private static Version current;
    private static String minecraft;
    private static long nextCheckAt = Long.MAX_VALUE;

    private static volatile State state = State.IDLE;
    private static volatile GithubReleases.Release release;
    private static volatile String error = "";
    private static volatile double progress;
    private static volatile boolean prompted;

    private UpdateManager() {
    }

    /** @param configDir {@code config/dihclient} */
    public static synchronized void init(Path configDir) {
        configFile = configDir.resolve("update.json");
        config = UpdateConfig.load(configFile);
        ownJar = locateOwnJar();
        current = readOwnVersion();
        minecraft = current == null ? null : current.minecraft();
        if (ownJar != null) {
            cleanLeftovers(ownJar.getParent());
        }
        if (config.checkForUpdates && current != null) {
            nextCheckAt = System.currentTimeMillis() + FIRST_CHECK_MS;
        }
    }

    /** Called every client tick. Cheap: only compares a timestamp. */
    public static void tick() {
        if (state != State.CHECKING && state != State.DOWNLOADING && System.currentTimeMillis() >= nextCheckAt) {
            nextCheckAt = Long.MAX_VALUE;
            checkNow();
        }
    }

    public static void checkNow() {
        if (current == null || state == State.CHECKING || state == State.DOWNLOADING) {
            return;
        }
        State before = state;
        state = State.CHECKING;
        POOL.execute(() -> {
            try {
                String json = GithubReleases.fetch(config.repo);
                GithubReleases.Release r = GithubReleases.pickNewer(json, current, minecraft, config.includePrereleases);
                if (r != null && !r.version().display().equals(config.skippedVersion)) {
                    release = r;
                    prompted = false;
                    state = State.AVAILABLE;
                } else {
                    state = before == State.READY ? State.READY : State.UP_TO_DATE;
                }
                schedule(config.checkEveryHours * 3_600_000L);
            } catch (Exception e) {
                error = String.valueOf(e.getMessage());
                state = before == State.READY ? State.READY : State.IDLE;
                schedule(RETRY_AFTER_FAILURE_MS);
            }
        });
    }

    /** Starts the download of the offered release. */
    public static void startDownload() {
        GithubReleases.Release r = release;
        if (r == null || (state != State.AVAILABLE && state != State.FAILED) || !canInstall()) {
            return;
        }
        state = State.DOWNLOADING;
        progress = 0;
        error = "";
        POOL.execute(() -> {
            try {
                Path pending = Installer.download(r, ownJar.getParent(), p -> progress = p);
                Installer.swapOnExit(ownJar, pending, r.assetName());
                state = State.READY;
            } catch (Exception e) {
                error = String.valueOf(e.getMessage());
                state = State.FAILED;
            }
        });
    }

    /** "Skip this version": the same release is not offered again, a newer one is. */
    public static synchronized void skipRelease() {
        GithubReleases.Release r = release;
        if (r != null) {
            config.skippedVersion = r.version().display();
            config.save(configFile);
        }
        state = State.UP_TO_DATE;
    }

    public static void dismiss() {
        prompted = true;
    }

    /** True exactly once per offered release, when the player should be asked. */
    public static boolean shouldPrompt() {
        if (state == State.AVAILABLE && !prompted) {
            prompted = true;
            return true;
        }
        return false;
    }

    /** False when the mod is not running from a jar (development) or the folder is read-only. */
    public static boolean canInstall() {
        return ownJar != null && Files.isRegularFile(ownJar) && Files.isWritable(ownJar.getParent());
    }

    public static State state() {
        return state;
    }

    public static GithubReleases.Release release() {
        return release;
    }

    public static String error() {
        return error;
    }

    public static double progress() {
        return progress;
    }

    public static Version currentVersion() {
        return current;
    }

    public static UpdateConfig config() {
        return config;
    }

    private static void schedule(long delayMs) {
        nextCheckAt = config.checkForUpdates ? System.currentTimeMillis() + delayMs : Long.MAX_VALUE;
    }

    private static Path locateOwnJar() {
        try {
            Path p = Path.of(UpdateManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar") ? p : null;
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    private static Version readOwnVersion() {
        try (InputStream in = UpdateManager.class.getResourceAsStream("/fabric.mod.json")) {
            if (in == null) {
                return null;
            }
            JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            return Version.parse(o.get("version").getAsString());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * A download that was finished but not swapped in (game crashed, killed) is retried if it is newer than the
     * running jar and removed otherwise. Half-finished downloads are always removed.
     */
    private static void cleanLeftovers(Path modsDir) {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(modsDir, "dihclient*")) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                if (n.endsWith(".part")) {
                    Files.deleteIfExists(p);
                } else if (n.endsWith(Installer.PENDING_SUFFIX)) {
                    String jarName = n.substring(0, n.length() - Installer.PENDING_SUFFIX.length());
                    Version v = Version.parse(jarName);
                    try {
                        Installer.verifyJar(p, v);
                        if (v != null && current != null && v.compareTo(current) > 0
                                && (minecraft == null || v.minecraft() == null || minecraft.equals(v.minecraft()))) {
                            Installer.swapOnExit(ownJar, p, jarName);
                            state = State.READY;
                            continue;
                        }
                    } catch (IOException ignored) {
                        // fall through and delete
                    }
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException ignored) {
            // an unreadable mods folder is not our problem to solve
        }
    }
}
