package dev.dihclient.account;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * One saved login. Microsoft and Refresh keep a refresh token, Session keeps a Minecraft access token, Offline keeps
 * only the name (a "cracked" account: it works on offline-mode servers and in single player, not on online-mode servers).
 */
public final class Account {
    public enum Kind { MICROSOFT, REFRESH, SESSION, OFFLINE }

    public Kind kind;
    public String name = "?";
    public UUID uuid = UUID.randomUUID();
    public String secret = "";
    public String mcToken;
    public String xuid;

    public Account() {
    }

    public Account(Kind kind) {
        this.kind = kind;
    }

    public static Account fromResult(Kind kind, AuthApi.Result result) {
        Account account = new Account(kind);
        account.apply(result);
        return account;
    }

    /** The same UUID a vanilla offline-mode server gives this name. */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public static Account offline(String name) {
        Account account = new Account(Kind.OFFLINE);
        account.name = name;
        account.uuid = offlineUuid(name);
        return account;
    }

    public void apply(AuthApi.Result result) {
        this.name = result.name();
        this.uuid = result.uuid();
        this.mcToken = result.mcToken();
        this.xuid = result.xuid();
        if (this.kind == Kind.SESSION) {
            this.secret = result.mcToken();
        } else if (result.refreshToken() != null) {
            this.secret = result.refreshToken();
        }
    }

    public AuthApi.Result resolve() throws AuthApi.AuthException {
        return switch (this.kind) {
            case MICROSOFT, REFRESH -> AuthApi.loginWithRefreshToken(this.secret);
            case SESSION -> AuthApi.loginWithSessionToken(this.secret);
            case OFFLINE -> new AuthApi.Result(this.name, offlineUuid(this.name), "0", null, null);
        };
    }

    public String label() {
        return switch (this.kind) {
            case MICROSOFT -> "Microsoft";
            case REFRESH -> "Refresh token";
            case SESSION -> "Session token";
            case OFFLINE -> "Cracked";
        };
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("kind", this.kind.name());
        json.addProperty("name", this.name);
        json.addProperty("uuid", this.uuid.toString());
        json.addProperty("secret", this.secret);
        if (this.xuid != null) {
            json.addProperty("xuid", this.xuid);
        }
        return json;
    }

    public static Account fromJson(JsonObject json) {
        Account account = new Account(Kind.valueOf(json.get("kind").getAsString()));
        account.name = json.has("name") ? json.get("name").getAsString() : "?";
        try {
            account.uuid = UUID.fromString(json.get("uuid").getAsString());
        } catch (Exception ignored) {
            // keeps the random one
        }
        account.secret = json.has("secret") ? json.get("secret").getAsString() : "";
        account.xuid = json.has("xuid") ? json.get("xuid").getAsString() : null;
        return account;
    }
}
