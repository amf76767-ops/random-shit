package dev.dihclient;

import com.sun.net.httpserver.HttpServer;
import dev.dihclient.update.GithubReleases;
import dev.dihclient.update.Installer;
import dev.dihclient.update.UpdateConfig;
import dev.dihclient.update.Version;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Plain main()-style tests so no test framework has to be downloaded. */
public final class UpdateTests {
    static int failed;
    static int passed;

    static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + what);
        }
    }

    static byte[] jar(String id, String version) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            z.putNextEntry(new ZipEntry("fabric.mod.json"));
            z.write(("{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return bo.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        versions();
        selection();
        config();
        installer();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void versions() {
        Version a = Version.parse("5.6.0+mc1.21.11");
        check(a != null && a.minecraft().equals("1.21.11") && a.display().equals("5.6.0"), "parse with mc");
        check(Version.parse("v5.7.0").compareTo(a) > 0, "v5.7.0 > 5.6.0");
        check(Version.parse("5.6").compareTo(Version.parse("5.6.0")) == 0, "5.6 == 5.6.0");
        check(Version.parse("5.10.0").compareTo(Version.parse("5.9.9")) > 0, "numeric not lexical");
        check(Version.parse("5.7.0-beta.1").compareTo(Version.parse("5.7.0")) < 0, "pre-release is lower");
        check(Version.parse("5.7.0-beta.1").isPreRelease(), "pre-release flag");
        check(Version.parse("nonsense") == null, "garbage is null");
        check(Version.parse(null) == null, "null is null");
        Version f = Version.parse("dihclient-v5.7.0+mc1.21.11.jar");
        check(f != null && f.display().equals("5.7.0") && "1.21.11".equals(f.minecraft()), "parse from file name");
        check(Version.parse("dihclient-v5.6.jar").display().equals("5.6"), "file name without patch");
    }

    static String release(String tag, boolean pre, boolean draft, String asset, String url) {
        return "{\"tag_name\":\"" + tag + "\",\"name\":\"" + tag + "\",\"prerelease\":" + pre + ",\"draft\":" + draft
                + ",\"html_url\":\"https://github.com/o/r/releases/tag/" + tag + "\",\"body\":\"notes\",\"assets\":["
                + "{\"name\":\"" + asset + "\",\"state\":\"uploaded\",\"size\":123,\"browser_download_url\":\"" + url
                + "\",\"digest\":\"sha256:ABCDEF\"}]}";
    }

    static void selection() {
        Version cur = Version.parse("5.6.0+mc1.21.11");
        String base = "https://github.com/o/r/releases/download/";
        String json = "[" + release("v5.8.0+mc1.21.12", false, false, "dihclient-v5.8.0+mc1.21.12.jar", base + "x/a.jar") + ","
                + release("v5.7.0+mc1.21.11", false, false, "dihclient-v5.7.0+mc1.21.11.jar", base + "x/b.jar") + ","
                + release("v5.9.0+mc1.21.11", true, false, "dihclient-v5.9.0+mc1.21.11.jar", base + "x/c.jar") + ","
                + release("v6.0.0+mc1.21.11", false, true, "dihclient-v6.0.0+mc1.21.11.jar", base + "x/d.jar") + ","
                + release("v5.5.0+mc1.21.11", false, false, "dihclient-v5.5.0+mc1.21.11.jar", base + "x/e.jar") + "]";
        GithubReleases.Release r = GithubReleases.pickNewer(json, cur, "1.21.11", false);
        check(r != null && r.version().display().equals("5.7.0"), "picks 5.7.0 (skips other MC, prerelease, draft, older)");
        check(r != null && r.sha256().equals("abcdef") && r.assetSize() == 123, "digest and size read");
        GithubReleases.Release p = GithubReleases.pickNewer(json, cur, "1.21.11", true);
        check(p != null && p.version().display().equals("5.9.0"), "prereleases allowed");
        check(GithubReleases.pickNewer(json, Version.parse("5.7.0+mc1.21.11"), "1.21.11", false) == null, "already up to date");
        String evil = "[" + release("v5.7.0", false, false, "dihclient-v5.7.0.jar", "https://evil.example/x.jar") + "]";
        check(GithubReleases.pickNewer(evil, cur, "1.21.11", false) == null, "untrusted host is ignored");
        String http = "[" + release("v5.7.0", false, false, "dihclient-v5.7.0.jar", "http://github.com/x.jar") + "]";
        check(GithubReleases.pickNewer(http, cur, "1.21.11", false) == null, "plain http is ignored");
        String src = "[" + release("v5.7.0", false, false, "dihclient-v5.7.0-sources.jar", base + "x/s.jar") + "]";
        check(GithubReleases.pickNewer(src, cur, "1.21.11", false) == null, "sources jar is ignored");
        String other = "[" + release("v5.7.0", false, false, "somethingelse.jar", base + "x/s.jar") + "]";
        check(GithubReleases.pickNewer(other, cur, "1.21.11", false) == null, "foreign jar is ignored");
        String tagOnly = "[" + release("v5.7.0", false, false, "dihclient-v5.7.0+mc1.21.11.jar", base + "x/t.jar") + "]";
        check(GithubReleases.pickNewer(tagOnly, cur, "1.21.11", false) != null, "Minecraft version taken from the file name when the tag has none");
        check(GithubReleases.pickNewer(tagOnly, cur, "1.21.12", false) == null, "file for another Minecraft version is not offered");
        check(GithubReleases.pickNewer("{\"message\":\"Not Found\"}", cur, "1.21.11", false) == null, "error body is ignored");
        check(GithubReleases.pickNewer("[]", cur, "1.21.11", false) == null, "empty list");
        check(GithubReleases.trustedHost("objects.githubusercontent.com") && GithubReleases.trustedHost("github.com")
                && !GithubReleases.trustedHost("githubusercontent.com.evil.io") && !GithubReleases.trustedHost("evilgithub.com"), "host allowlist");
    }

    static void config() throws IOException {
        Path d = Files.createTempDirectory("dihcfg");
        Path f = d.resolve("sub").resolve("update.json");
        UpdateConfig c = UpdateConfig.load(f);
        check(c.repo.equals(UpdateConfig.DEFAULT_REPO) && Files.exists(f), "defaults are written");
        Files.writeString(f, "{\"repo\":\"../../etc\",\"checkEveryHours\":0}");
        c = UpdateConfig.load(f);
        check(c.repo.equals(UpdateConfig.DEFAULT_REPO) && c.checkEveryHours == 1, "bad values are repaired");
        Files.writeString(f, "{not json");
        check(UpdateConfig.load(f).repo.equals(UpdateConfig.DEFAULT_REPO), "broken file falls back");
        Files.writeString(f, "{\"repo\":\"me/mine\",\"skippedVersion\":\"5.7.0\"}");
        c = UpdateConfig.load(f);
        check(c.repo.equals("me/mine") && c.skippedVersion.equals("5.7.0"), "values are kept");
    }

    static void installer() throws Exception {
        GithubReleases.allowLoopbackForTests = true;
        byte[] good = jar("dihclient", "5.7.0+mc1.21.11");
        byte[] wrongId = jar("othermod", "5.7.0+mc1.21.11");
        byte[] wrongVer = jar("dihclient", "5.6.5+mc1.21.11");
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/good.jar", ex -> send(ex, good));
        srv.createContext("/wrongid.jar", ex -> send(ex, wrongId));
        srv.createContext("/wrongver.jar", ex -> send(ex, wrongVer));
        srv.createContext("/short.jar", ex -> send(ex, java.util.Arrays.copyOf(good, good.length - 5)));
        srv.createContext("/missing.jar", ex -> { ex.sendResponseHeaders(404, -1); ex.close(); });
        srv.start();
        String base = "http://127.0.0.1:" + srv.getAddress().getPort();
        Version v = Version.parse("5.7.0+mc1.21.11");
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(good));
        Path mods = Files.createTempDirectory("dihmods");
        double[] last = {0};

        Path done = Installer.download(rel(v, "dihclient-v5.7.0+mc1.21.11.jar", base + "/good.jar", good.length, sha), mods, p -> last[0] = p);
        check(Files.exists(done) && done.getFileName().toString().endsWith(".jar.update") && last[0] == 1.0, "good download");
        check(!Files.exists(mods.resolve("dihclient-v5.7.0+mc1.21.11.jar.part")), "no .part left");

        check(fails(() -> Installer.download(rel(v, "dihclient-a.jar", base + "/good.jar", good.length, "00" + sha.substring(2)), mods, null)), "bad checksum rejected");
        check(fails(() -> Installer.download(rel(v, "dihclient-b.jar", base + "/wrongid.jar", wrongId.length, ""), mods, null)), "foreign mod id rejected");
        check(fails(() -> Installer.download(rel(v, "dihclient-c.jar", base + "/wrongver.jar", wrongVer.length, ""), mods, null)), "wrong version rejected");
        check(fails(() -> Installer.download(rel(v, "dihclient-d.jar", base + "/short.jar", good.length, ""), mods, null)), "short file rejected");
        check(fails(() -> Installer.download(rel(v, "dihclient-e.jar", base + "/missing.jar", 10, ""), mods, null)), "404 rejected");
        check(fails(() -> Installer.download(rel(v, "../evil.jar", base + "/good.jar", good.length, ""), mods, null)), "path traversal in name rejected");
        check(fails(() -> Installer.download(rel(v, "dihclient-f.jar", "https://evil.example/x.jar", 10, ""), mods, null)), "untrusted host rejected");
        try (var ls = Files.list(mods)) {
            check(ls.noneMatch(p -> p.toString().endsWith(".part")), "failed downloads leave no .part");
        }
        srv.stop(0);

        // the swap script, run for real on this (unix) machine
        Path old = mods.resolve("dihclient-v5.6.0+mc1.21.11.jar");
        Files.write(old, jar("dihclient", "5.6.0+mc1.21.11"));
        Path fin = mods.resolve("dihclient-v5.7.0+mc1.21.11.jar");
        Method m = Installer.class.getDeclaredMethod("launchHelper", Path.class, Path.class, Path.class, boolean.class);
        m.setAccessible(true);
        m.invoke(null, old, done, fin, false);
        for (int i = 0; i < 50 && !Files.exists(fin); i++) {
            Thread.sleep(100);
        }
        check(Files.exists(fin) && !Files.exists(old) && !Files.exists(done), "old jar replaced by new one");
        Installer.verifyJar(fin, v);
        check(true, "swapped jar verifies");

        // paths with spaces and quotes must survive the script
        Path odd = Files.createTempDirectory("dih odd'dir");
        Path old2 = odd.resolve("dihclient old.jar");
        Path pend2 = odd.resolve("dihclient new.jar.update");
        Path fin2 = odd.resolve("dihclient new.jar");
        Files.writeString(old2, "x");
        Files.write(pend2, good);
        m.invoke(null, old2, pend2, fin2, false);
        for (int i = 0; i < 50 && !Files.exists(fin2); i++) {
            Thread.sleep(100);
        }
        check(Files.exists(fin2) && !Files.exists(old2), "swap works with spaces and quotes in the path");

        String win = (String) call("windowsScript", old, done, fin);
        check(win.contains("del /f /q") && win.contains("move /y") && win.contains(old.toString()), "windows script contains the steps");
        check(((String) call("windowsScript", Path.of("/a/100%/b.jar"), done, fin)).contains("100%%"), "percent is escaped for cmd");
    }

    static Object call(String name, Path a, Path b, Path c) throws Exception {
        Method m = Installer.class.getDeclaredMethod(name, Path.class, Path.class, Path.class);
        m.setAccessible(true);
        return m.invoke(null, a, b, c);
    }

    static GithubReleases.Release rel(Version v, String asset, String url, long size, String sha) {
        return new GithubReleases.Release(v, "v" + v.display(), "t", "n", "u", asset, url, size, sha);
    }

    interface Throwing {
        void run() throws Exception;
    }

    static boolean fails(Throwing t) {
        try {
            t.run();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    static void send(com.sun.net.httpserver.HttpExchange ex, byte[] body) throws IOException {
        ex.sendResponseHeaders(200, body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }
}
