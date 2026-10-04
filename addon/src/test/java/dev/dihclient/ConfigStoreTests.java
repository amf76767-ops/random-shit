package dev.dihclient;

import dev.dihclient.port.configs.ConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class ConfigStoreTests {
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

    /** Pretends to be DIH's profile files. */
    static final class Fake implements ConfigStore.Backend {
        final Set<Integer> files = new HashSet<>();
        int lastLoaded = -1;
        boolean failSave;

        public boolean save(int id) {
            if (this.failSave) {
                return false;
            }
            return this.files.add(id) || true;
        }

        public boolean load(int id) {
            this.lastLoaded = id;
            return this.files.contains(id);
        }

        public boolean exists(int id) {
            return this.files.contains(id);
        }

        public void delete(int id) {
            this.files.remove(id);
        }
    }

    public static void main(String[] args) throws Exception {
        check(ConfigStore.normalize("Play.Example.NET.:25565").equals("play.example.net"), "normalize host + port + dot");
        check(ConfigStore.normalize("  donutsmp.net ").equals("donutsmp.net"), "normalize spaces");
        check(ConfigStore.normalize("minecraft://x.y/z").equals("x.y"), "normalize scheme + path");
        check(ConfigStore.normalize("[::1]:25565").equals("[::1]"), "normalize ipv6");
        check(ConfigStore.normalize("bad host").isEmpty(), "reject spaces in host");
        check(ConfigStore.normalize("").isEmpty() && ConfigStore.normalize(null).isEmpty(), "reject empty");
        check(ConfigStore.cleanName("  PvP\n ").equals("PvP"), "clean name");
        check(ConfigStore.cleanName("x".repeat(40)).length() == ConfigStore.MAX_NAME, "name length cap");

        Path dir = Files.createTempDirectory("cfgstore");
        Path file = dir.resolve("configs.json");
        Fake fake = new Fake();
        ConfigStore store = new ConfigStore(file, fake);

        ConfigStore.Entry pvp = store.add("PvP", 1000L);
        ConfigStore.Entry second = store.add("pvp", 2000L);
        check(pvp != null && pvp.id == ConfigStore.FIRST_ID, "first id");
        check(second != null && second.id == ConfigStore.FIRST_ID + 1, "second id");
        check(second.name.equals("pvp 2"), "duplicate name gets a number, got " + second.name);
        check(store.add("   ", 0L) == null, "empty name refused");
        fake.failSave = true;
        check(store.add("Broken", 0L) == null && store.list().size() == 2, "failed save adds nothing");
        fake.failSave = false;

        check(store.rename(second, "Farm") && second.name.equals("Farm"), "rename");
        check(store.rename(second, "PVP") && second.name.equals("PVP 2"), "rename keeps names unique");

        check(store.addServer(pvp, "Play.Donutsmp.NET:25565"), "add server");
        check(pvp.servers.contains("play.donutsmp.net"), "server stored normalized");
        check(!store.addServer(pvp, "not a host"), "bad address refused");
        check(store.forServer("play.donutsmp.net") == pvp, "exact server match");
        check(store.forServer("PLAY.DONUTSMP.NET:1234") == pvp, "match ignores case and port");
        check(store.forServer("eu.play.donutsmp.net") == pvp, "subdomain matches");
        check(store.forServer("donutsmp.net") == null, "parent domain does not match a subdomain entry");
        check(store.forServer("xplay.donutsmp.net") == null, "no match on a partial label");
        check(store.forServer(null) == null, "null address");

        store.addServer(second, "donutsmp.net");
        check(store.forServer("play.donutsmp.net") == pvp, "longer entry wins");
        check(store.forServer("lobby.donutsmp.net") == second, "shorter entry catches the rest");
        store.addServer(second, "play.donutsmp.net");
        check(!pvp.servers.contains("play.donutsmp.net") && store.forServer("play.donutsmp.net") == second, "one server loads one config");

        check(store.apply(pvp) && fake.lastLoaded == pvp.id, "apply loads the right file");
        check(store.overwrite(pvp, 5000L) && pvp.saved == 5000L, "overwrite");

        ConfigStore again = new ConfigStore(file, fake);
        check(again.list().size() == 2, "survives a restart, got " + again.list().size());
        check(again.byId(second.id) != null && again.byId(second.id).servers.contains("play.donutsmp.net"), "servers survive a restart");
        check(again.forServer("play.donutsmp.net").id == second.id, "auto-load still matches after restart");

        store.delete(pvp);
        check(!fake.exists(pvp.id) && store.byId(pvp.id) == null, "delete removes the file and the entry");

        // legacy slots and files that disappeared
        Fake old = new Fake();
        old.files.add(1);
        old.files.add(3);
        ConfigStore legacy = new ConfigStore(dir.resolve("legacy.json"), old);
        legacy.syncLegacy();
        check(legacy.list().size() == 2 && legacy.byId(1).name.equals("Slot 1") && legacy.byId(3).name.equals("Slot 3"), "old slots show up");
        old.files.remove(1);
        legacy.syncLegacy();
        check(legacy.byId(1) == null && legacy.byId(3) != null, "a missing file removes its entry");

        // a broken index file is not fatal
        Files.writeString(dir.resolve("bad.json"), "{ nope");
        check(new ConfigStore(dir.resolve("bad.json"), new Fake()).list().isEmpty(), "broken file gives an empty list");

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
