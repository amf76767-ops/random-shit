package dev.dihclient.merge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;

public final class ConfigMigration {
    private ConfigMigration() {
    }

    public record Rule(String guest, String host, Map<String, String> renamed, String enableSetting, String modeSetting, String modeValue,
                       boolean takeKey, boolean copyEnabled) {
        public static Rule rename(String oldId, String newId) {
            return new Rule(oldId, newId, Map.of(), null, null, null, true, true);
        }
    }

    public static void apply(JsonObject root, List<Rule> rules) {
        if (root == null || !root.has("modules") || !root.get("modules").isJsonObject()) {
            return;
        }
        JsonObject modules = root.getAsJsonObject("modules");
        for (Rule r : rules) {
            JsonObject guest = section(modules, r.guest());
            if (guest == null) {
                continue;
            }
            JsonObject host = section(modules, r.host());
            if (host == null) {
                host = new JsonObject();
                modules.add(r.host(), host);
            }
            JsonObject hostSettings = settings(host);
            JsonObject guestSettings = guest.has("settings") && guest.get("settings").isJsonObject() ? guest.getAsJsonObject("settings") : null;
            boolean wasOn = guest.has("enabled") && guest.get("enabled").isJsonPrimitive() && guest.get("enabled").getAsBoolean();
            if (guestSettings != null) {
                for (Map.Entry<String, JsonElement> e : guestSettings.entrySet()) {
                    String target = r.renamed().getOrDefault(e.getKey(), e.getKey());
                    if (!hostSettings.has(target)) {
                        hostSettings.add(target, e.getValue());
                    }
                }
            }
            if (wasOn && r.enableSetting() != null && !hostSettings.has(r.enableSetting())) {
                hostSettings.add(r.enableSetting(), new JsonPrimitive(true));
            }
            if (wasOn && r.modeSetting() != null) {
                if (!hostSettings.has(r.modeSetting())) {
                    hostSettings.add(r.modeSetting(), new JsonPrimitive(r.modeValue()));
                }
                if (!host.has("enabled")) {
                    host.add("enabled", new JsonPrimitive(true));
                }
            }
            if (r.copyEnabled() && guest.has("enabled") && !host.has("enabled")) {
                host.add("enabled", guest.get("enabled"));
            }
            if (r.takeKey() && guest.has("key") && guest.get("key").isJsonPrimitive() && guest.get("key").getAsInt() >= 0) {
                boolean hostHasKey = host.has("key") && host.get("key").isJsonPrimitive() && host.get("key").getAsInt() >= 0;
                if (!hostHasKey) {
                    host.add("key", guest.get("key"));
                }
            }
        }
    }

    private static JsonObject section(JsonObject modules, String id) {
        JsonElement e = modules.get(id);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonObject settings(JsonObject host) {
        if (host.has("settings") && host.get("settings").isJsonObject()) {
            return host.getAsJsonObject("settings");
        }
        JsonObject s = new JsonObject();
        host.add("settings", s);
        return s;
    }
}
