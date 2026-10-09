package dev.dihclient.port.discordbot;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class BotRest {
    static final String API = "https://discord.com/api/v10";

    static final class HttpError extends IOException {
        final int status;

        HttpError(int status, String body) {
            super("HTTP " + status + (body.isBlank() ? "" : ": " + (body.length() > 200 ? body.substring(0, 200) : body)));
            this.status = status;
        }
    }

    private final HttpClient http;
    private final String token;

    BotRest(HttpClient http, String token) {
        this.http = http;
        this.token = token;
    }

    JsonElement get(String path) throws IOException, InterruptedException {
        return this.send("GET", path, null);
    }

    JsonElement post(String path, JsonElement body) throws IOException, InterruptedException {
        return this.send("POST", path, body);
    }

    JsonElement put(String path, JsonElement body) throws IOException, InterruptedException {
        return this.send("PUT", path, body);
    }

    JsonElement send(String method, String path, JsonElement body) throws IOException, InterruptedException {
        for (int attempt = 0; ; attempt++) {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bot " + this.token)
                    .header("User-Agent", "DiscordBot (https://github.com/amf76767-ops/random-shit, 1.0) DIHClient");
            if (body != null) {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> r = this.http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = r.statusCode();
            String text = r.body() == null ? "" : r.body();
            if (code == 429 && attempt < 2) {
                double wait = 1.0;
                try {
                    JsonObject o = JsonParser.parseString(text).getAsJsonObject();
                    if (o.has("retry_after")) {
                        wait = o.get("retry_after").getAsDouble();
                    }
                } catch (RuntimeException ignored) {

                }
                Thread.sleep((long) (Math.min(wait, 10.0) * 1000.0) + 100L);
                continue;
            }
            if (code >= 300) {
                throw new HttpError(code, text);
            }
            if (text.isBlank()) {
                return JsonNull.INSTANCE;
            }
            return JsonParser.parseString(text);
        }
    }
}
