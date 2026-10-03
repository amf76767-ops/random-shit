package dev.dihclient.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/** Reads the releases of a GitHub repository and picks the newest DIHClient build for this Minecraft version. */
public final class GithubReleases {
    /** One downloadable release. */
    public record Release(Version version, String tag, String title, String notes, String pageUrl,
                          String assetName, String assetUrl, long assetSize, String sha256) {
    }

    private static final Pattern ASSET_NAME = Pattern.compile("(?i)dihclient[\\w.+-]*\\.jar");
    private static final Pattern BAD_ASSET = Pattern.compile("(?i)(sources|javadoc|dev|api)\\.jar$");

    /** Set by the tests only, so a local server can stand in for GitHub. */
    public static boolean allowLoopbackForTests;

    private GithubReleases() {
    }

    /** Only these hosts may serve an update, whatever the release page says. */
    public static boolean trustedHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (allowLoopbackForTests && (h.equals("127.0.0.1") || h.equals("localhost"))) {
            return true;
        }
        return h.equals("github.com") || h.equals("api.github.com") || h.endsWith(".githubusercontent.com");
    }

    public static boolean trustedUrl(String url) {
        try {
            URI u = URI.create(url);
            boolean loopback = allowLoopbackForTests && "http".equalsIgnoreCase(u.getScheme());
            return ("https".equalsIgnoreCase(u.getScheme()) || loopback) && trustedHost(u.getHost());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * @param json              the body of {@code GET /repos/{repo}/releases}
     * @param current           the running version
     * @param minecraft         Minecraft version of the running client, or null to accept any
     * @param allowPrereleases  whether pre-releases may be offered
     * @return the newest release that is strictly newer than {@code current}, or null
     */
    public static Release pickNewer(String json, Version current, String minecraft, boolean allowPrereleases) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) {
            return null;
        }
        Release best = null;
        for (JsonElement e : (JsonArray) root) {
            if (!e.isJsonObject()) {
                continue;
            }
            Release r = read(e.getAsJsonObject(), minecraft, allowPrereleases);
            if (r == null || r.version().compareTo(current) <= 0) {
                continue;
            }
            if (best == null || r.version().compareTo(best.version()) > 0) {
                best = r;
            }
        }
        return best;
    }

    private static Release read(JsonObject o, String minecraft, boolean allowPrereleases) {
        if (bool(o, "draft") || (bool(o, "prerelease") && !allowPrereleases)) {
            return null;
        }
        String tag = str(o, "tag_name");
        Version v = Version.parse(tag);
        if (v == null) {
            v = Version.parse(str(o, "name"));
        }
        if (v == null || (v.isPreRelease() && !allowPrereleases)) {
            return null;
        }
        JsonObject asset = null;
        if (o.has("assets") && o.get("assets").isJsonArray()) {
            for (JsonElement ae : o.getAsJsonArray("assets")) {
                if (!ae.isJsonObject()) {
                    continue;
                }
                JsonObject a = ae.getAsJsonObject();
                String name = str(a, "name");
                if (!ASSET_NAME.matcher(name).matches() || BAD_ASSET.matcher(name).find()) {
                    continue;
                }
                String state = str(a, "state");
                if (!state.isEmpty() && !state.equals("uploaded")) {
                    continue;
                }
                if (!trustedUrl(str(a, "browser_download_url"))) {
                    continue;
                }
                // prefer the file that names our Minecraft version
                boolean fits = minecraft != null && name.contains(minecraft);
                if (asset == null || fits) {
                    asset = a;
                }
            }
        }
        if (asset == null) {
            return null;
        }
        String assetName = str(asset, "name");
        String mc = v.minecraft();
        if (mc == null) {
            Version fromName = Version.parse(assetName);
            mc = fromName == null ? null : fromName.minecraft();
        }
        if (minecraft != null && mc != null && !mc.equals(minecraft)) {
            return null; // a build for another Minecraft version would crash the game
        }
        String digest = str(asset, "digest");
        String sha = digest.toLowerCase(Locale.ROOT).startsWith("sha256:") ? digest.substring(7).toLowerCase(Locale.ROOT) : "";
        long size = asset.has("size") && asset.get("size").isJsonPrimitive() ? asset.get("size").getAsLong() : -1L;
        String title = str(o, "name");
        return new Release(v, tag, title.isEmpty() ? tag : title, str(o, "body"), str(o, "html_url"),
                assetName, str(asset, "browser_download_url"), size, sha);
    }

    public static String fetch(String repo) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo + "/releases?per_page=20"))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DIHClient-Updater")
                .GET().build();
        HttpResponse<String> res = client().send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("GitHub answered " + res.statusCode());
        }
        return res.body();
    }

    static HttpClient client() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean();
    }
}
