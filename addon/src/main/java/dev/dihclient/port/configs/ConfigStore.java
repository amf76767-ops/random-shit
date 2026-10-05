package dev.dihclient.port.configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConfigStore {

    public interface Backend {
        boolean save(int id);

        boolean load(int id);

        boolean exists(int id);

        void delete(int id);
    }

    public static final class Entry {
        public int id;
        public String name = "";
        public final List<String> servers = new ArrayList<>();
        public long saved;
    }

    public static final int FIRST_ID = 1000;
    public static final int LEGACY_SLOTS = 3;
    public static final int MAX_NAME = 24;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Backend backend;
    private final List<Entry> entries = new ArrayList<>();

    public ConfigStore(Path file, Backend backend) {
        this.file = file;
        this.backend = backend;
        this.load();
    }

    public List<Entry> list() {
        return this.entries;
    }

    public Entry byId(int id) {
        for (Entry e : this.entries) {
            if (e.id == id) {
                return e;
            }
        }
        return null;
    }

    public Entry add(String rawName, long now) {
        String name = cleanName(rawName);
        if (name.isEmpty()) {
            return null;
        }
        Entry entry = new Entry();
        entry.id = this.nextId();
        entry.name = this.uniqueName(name, null);
        entry.saved = now;
        if (!this.backend.save(entry.id)) {
            return null;
        }
        this.entries.add(entry);
        this.save();
        return entry;
    }

    public boolean overwrite(Entry entry, long now) {
        if (!this.backend.save(entry.id)) {
            return false;
        }
        entry.saved = now;
        this.save();
        return true;
    }

    public boolean apply(Entry entry) {
        return this.backend.load(entry.id);
    }

    public boolean rename(Entry entry, String rawName) {
        String name = cleanName(rawName);
        if (name.isEmpty()) {
            return false;
        }
        entry.name = this.uniqueName(name, entry);
        this.save();
        return true;
    }

    public void delete(Entry entry) {
        this.backend.delete(entry.id);
        this.entries.remove(entry);
        this.save();
    }

    public boolean addServer(Entry entry, String raw) {
        String host = normalize(raw);
        if (host.isEmpty()) {
            return false;
        }

        for (Entry other : this.entries) {
            other.servers.remove(host);
        }
        entry.servers.add(host);
        this.save();
        return true;
    }

    public void removeServer(Entry entry, String host) {
        entry.servers.remove(host);
        this.save();
    }

    public Entry forServer(String address) {
        String host = normalize(address);
        if (host.isEmpty()) {
            return null;
        }
        Entry best = null;
        int bestLength = -1;
        for (Entry entry : this.entries) {
            for (String saved : entry.servers) {
                if ((host.equals(saved) || host.endsWith("." + saved)) && saved.length() > bestLength) {
                    best = entry;
                    bestLength = saved.length();
                }
            }
        }
        return best;
    }

    public static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.replaceAll("[\\p{Cntrl}]", "").trim();
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME).trim() : name;
    }

    private String uniqueName(String name, Entry self) {
        String candidate = name;
        for (int n = 2; this.nameTaken(candidate, self); n++) {
            String suffix = " " + n;
            candidate = (name.length() + suffix.length() > MAX_NAME ? name.substring(0, MAX_NAME - suffix.length()) : name) + suffix;
        }
        return candidate;
    }

    private boolean nameTaken(String name, Entry self) {
        for (Entry e : this.entries) {
            if (e != self && e.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            s = s.substring(scheme + 3);
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        if (s.startsWith("[")) {
            int end = s.indexOf(']');
            return end > 0 ? s.substring(0, end + 1) : "";
        }
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(0, colon);
        }
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.matches("[a-z0-9._-]+") ? s : "";
    }

    private int nextId() {
        int id = FIRST_ID;
        for (Entry e : this.entries) {
            id = Math.max(id, e.id + 1);
        }
        return id;
    }

    public void syncLegacy() {
        boolean changed = false;
        for (int slot = 1; slot <= LEGACY_SLOTS; slot++) {
            if (this.byId(slot) == null && this.backend.exists(slot)) {
                Entry entry = new Entry();
                entry.id = slot;
                entry.name = this.uniqueName("Slot " + slot, null);
                this.entries.add(entry);
                changed = true;
            }
        }

        changed |= this.entries.removeIf(e -> !this.backend.exists(e.id));
        if (changed) {
            this.entries.sort((a, b) -> Integer.compare(a.id, b.id));
            this.save();
        }
    }

    public void load() {
        this.entries.clear();
        try {
            if (Files.exists(this.file)) {
                JsonElement root = JsonParser.parseString(Files.readString(this.file));
                JsonArray array = root.getAsJsonObject().getAsJsonArray("configs");
                for (JsonElement item : array) {
                    try {
                        JsonObject o = item.getAsJsonObject();
                        Entry e = new Entry();
                        e.id = o.get("id").getAsInt();
                        e.name = cleanName(o.get("name").getAsString());
                        e.saved = o.has("saved") ? o.get("saved").getAsLong() : 0L;
                        if (o.has("servers")) {
                            for (JsonElement s : o.getAsJsonArray("servers")) {
                                String host = normalize(s.getAsString());
                                if (!host.isEmpty() && !e.servers.contains(host)) {
                                    e.servers.add(host);
                                }
                            }
                        }
                        if (!e.name.isEmpty() && this.byId(e.id) == null) {
                            this.entries.add(e);
                        }
                    } catch (RuntimeException ignored) {

                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {

        }
    }

    public void save() {
        try {
            JsonArray array = new JsonArray();
            for (Entry e : this.entries) {
                JsonObject o = new JsonObject();
                o.addProperty("id", e.id);
                o.addProperty("name", e.name);
                o.addProperty("saved", e.saved);
                JsonArray servers = new JsonArray();
                e.servers.forEach(servers::add);
                o.add("servers", servers);
                array.add(o);
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.add("configs", array);
            Files.createDirectories(this.file.toAbsolutePath().getParent());
            Files.writeString(this.file, GSON.toJson(root));
        } catch (IOException ignored) {

        }
    }
}
