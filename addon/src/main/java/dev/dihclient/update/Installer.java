package dev.dihclient.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Downloads a release, checks it and swaps it in for the running jar once the game has closed.
 * The running jar is locked on Windows, so the swap is done by a small script that waits for it to be free.
 */
public final class Installer {
    public static final String PENDING_SUFFIX = ".update";
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._+-]{1,120}\\.jar");

    private Installer() {
    }

    /** Downloads into {@code modsDir/<asset>.update} and returns that file. */
    public static Path download(GithubReleases.Release r, Path modsDir, DoubleConsumer progress)
            throws IOException, InterruptedException {
        if (!SAFE_NAME.matcher(r.assetName()).matches()) {
            throw new IOException("Unsafe file name: " + r.assetName());
        }
        if (!GithubReleases.trustedUrl(r.assetUrl())) {
            throw new IOException("Untrusted download address");
        }
        Files.createDirectories(modsDir);
        Path part = modsDir.resolve(r.assetName() + ".part");
        Path done = modsDir.resolve(r.assetName() + PENDING_SUFFIX);
        HttpRequest req = HttpRequest.newBuilder(URI.create(r.assetUrl()))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "DIHClient-Updater")
                .header("Accept", "application/octet-stream")
                .GET().build();
        HttpResponse<InputStream> res = GithubReleases.client().send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = res.body()) {
            if (res.statusCode() != 200) {
                throw new IOException("Download failed (" + res.statusCode() + ")");
            }
            if (!GithubReleases.trustedHost(res.uri().getHost())) {
                throw new IOException("Download was redirected to " + res.uri().getHost());
            }
            long expected = r.assetSize() > 0 ? r.assetSize() : res.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (expected > MAX_BYTES) {
                throw new IOException("File is too large");
            }
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (OutputStream out = Files.newOutputStream(part)) {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) {
                        throw new IOException("File is too large");
                    }
                    sha.update(buf, 0, n);
                    out.write(buf, 0, n);
                    if (expected > 0 && progress != null) {
                        progress.accept(Math.min(1.0, (double) total / expected));
                    }
                }
            }
            if (r.assetSize() > 0 && total != r.assetSize()) {
                throw new IOException("Download is incomplete (" + total + " of " + r.assetSize() + " bytes)");
            }
            String got = HexFormat.of().formatHex(sha.digest());
            if (!r.sha256().isEmpty() && !r.sha256().equalsIgnoreCase(got)) {
                throw new IOException("Checksum does not match");
            }
            verifyJar(part, r.version());
            Files.move(part, done, StandardCopyOption.REPLACE_EXISTING);
            return done;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(part);
            throw e;
        } catch (java.security.NoSuchAlgorithmException e) {
            Files.deleteIfExists(part);
            throw new IOException(e);
        }
    }

    /** The file must be a DIHClient jar of the version we expect, otherwise it is not installed. */
    public static void verifyJar(Path jar, Version expected) throws IOException {
        try (ZipFile z = new ZipFile(jar.toFile())) {
            ZipEntry e = z.getEntry("fabric.mod.json");
            if (e == null) {
                throw new IOException("Not a Fabric mod");
            }
            JsonObject o;
            try (InputStream in = z.getInputStream(e)) {
                o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
            if (!"dihclient".equals(o.has("id") ? o.get("id").getAsString() : "")) {
                throw new IOException("Not DIHClient");
            }
            Version v = Version.parse(o.has("version") ? o.get("version").getAsString() : "");
            if (v == null || (expected != null && v.compareTo(expected) != 0)) {
                throw new IOException("Jar contains version " + v + ", expected " + expected);
            }
        } catch (IllegalStateException | ClassCastException | java.util.zip.ZipException e) {
            throw new IOException("Not a valid jar", e);
        }
    }

    /**
     * Arranges for {@code pending} to replace {@code oldJar} when the JVM exits. The result is named {@code finalName}
     * and lives next to the old jar.
     */
    public static void swapOnExit(Path oldJar, Path pending, String finalName) {
        Path finalPath = oldJar.resolveSibling(finalName);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                launchHelper(oldJar, pending, finalPath, System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));
            } catch (Throwable ignored) {
                // nothing sensible left to do while the game is closing; the next start retries
            }
        }, "DIHClient-update-swap"));
    }

    static void launchHelper(Path oldJar, Path pending, Path finalPath, boolean windows) throws IOException {
        Path script = Files.createTempFile("dihclient-update", windows ? ".cmd" : ".sh");
        Files.writeString(script, windows ? windowsScript(oldJar, pending, finalPath) : unixScript(oldJar, pending, finalPath),
                StandardCharsets.UTF_8);
        script.toFile().setExecutable(true);
        ProcessBuilder pb = windows
                ? new ProcessBuilder("cmd", "/c", "start", "DIHClient update", "/min", script.toString())
                : new ProcessBuilder("sh", "-c", "nohup sh '" + script + "' >/dev/null 2>&1 &");
        pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.start();
    }

    static String windowsScript(Path oldJar, Path pending, Path finalPath) {
        return "@echo off\r\n"
                + "for /l %%i in (1,1,60) do (\r\n"
                + "  del /f /q \"" + win(oldJar) + "\" >nul 2>&1\r\n"
                + "  if not exist \"" + win(oldJar) + "\" goto swap\r\n"
                + "  ping -n 2 127.0.0.1 >nul\r\n"
                + ")\r\n"
                + "exit /b 1\r\n"
                + ":swap\r\n"
                + "move /y \"" + win(pending) + "\" \"" + win(finalPath) + "\" >nul\r\n"
                + "(goto) 2>nul & del \"%~f0\"\r\n";
    }

    static String unixScript(Path oldJar, Path pending, Path finalPath) {
        return "#!/bin/sh\n"
                + "i=0\n"
                + "while [ $i -lt 60 ]; do\n"
                + "  rm -f " + sh(oldJar) + "\n"
                + "  [ ! -e " + sh(oldJar) + " ] && break\n"
                + "  i=$((i+1)); sleep 1\n"
                + "done\n"
                + "[ -e " + sh(oldJar) + " ] && exit 1\n"
                + "mv -f " + sh(pending) + " " + sh(finalPath) + "\n"
                + "rm -f \"$0\"\n";
    }

    private static String win(Path p) {
        return p.toAbsolutePath().toString().replace("%", "%%");
    }

    private static String sh(Path p) {
        return "'" + p.toAbsolutePath().toString().replace("'", "'\\''") + "'";
    }
}
