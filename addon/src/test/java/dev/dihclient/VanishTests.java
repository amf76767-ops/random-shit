package dev.dihclient;

import dev.dihclient.port.vanish.VanishContext;
import dev.dihclient.port.vanish.VanishEngine;
import dev.dihclient.port.vanish.VanishEngine.Body;
import dev.dihclient.port.vanish.VanishEngine.Observation;
import dev.dihclient.port.vanish.VanishEngine.ObservationType;
import dev.dihclient.port.vanish.VanishHeuristics;
import dev.dihclient.port.vanish.VanishText;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Anti Vanish decision logic with synthetic tab lists, entities and clocks (no Minecraft). */
public final class VanishTests {
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

    // ------------------------------------------------------------------ fakes

    static final class Fake implements VanishEngine.Env, VanishEngine.Sink {
        long now = 1_000_000L;
        final UUID self = UUID.nameUUIDFromBytes("self".getBytes());
        double[] pos = {0, 64, 0};
        boolean container;
        final Map<UUID, String> tab = new LinkedHashMap<>();
        final Set<UUID> listed = new HashSet<>();
        final List<Body> bodies = new ArrayList<>();
        final List<double[]> projectiles = new ArrayList<>();
        final List<double[]> villagers = new ArrayList<>();
        final Map<String, String> blocks = new HashMap<>();
        final Set<String> poweredBlocks = new HashSet<>();
        Predicate<String> target = n -> true;
        final List<String> sentProbes = new ArrayList<>();
        final List<String> announced = new ArrayList<>();
        final List<String> news = new ArrayList<>();
        final List<String> watching = new ArrayList<>();
        VanishEngine engine;

        Fake() {
            this.engine = new VanishEngine(this, this);
        }

        UUID join(String name) {
            UUID id = UUID.nameUUIDFromBytes(name.getBytes());
            this.tab.put(id, name);
            this.listed.add(id);
            return id;
        }

        Body body(UUID id, String name, double x, double y, double z, boolean invisible) {
            double dx = x - this.pos[0], dy = y - this.pos[1], dz = z - this.pos[2];
            Body b = new Body(id, Math.abs(id.hashCode() % 1000), name, x, y, z, Math.sqrt(dx * dx + dy * dy + dz * dz), invisible);
            this.bodies.add(b);
            return b;
        }

        void ticks(int n) {
            for (int i = 0; i < n; i++) {
                this.now += 50;
                this.engine.tick();
            }
        }

        void ticks(int n, long msPerTick) {
            for (int i = 0; i < n; i++) {
                this.now += msPerTick;
                this.engine.tick();
            }
        }

        boolean announcedHas(String s) {
            return this.announced.stream().anyMatch(a -> a.contains(s));
        }

        boolean newsHas(String s) {
            return this.news.stream().anyMatch(a -> a.contains(s));
        }

        // Env
        public long now() { return this.now; }
        public UUID selfId() { return this.self; }
        public String selfName() { return "Me"; }
        public double[] selfPos() { return this.pos; }
        public boolean containerOpen() { return this.container; }
        public String tabName(UUID id) { return this.tab.get(id); }
        public boolean inTab(UUID id) { return this.tab.containsKey(id); }
        public Collection<VanishEngine.TabEntry> listedEntries() {
            List<VanishEngine.TabEntry> out = new ArrayList<>();
            for (UUID id : this.listed) {
                if (this.tab.containsKey(id)) {
                    out.add(new VanishEngine.TabEntry(id, this.tab.get(id)));
                }
            }
            return out;
        }
        public Set<UUID> connectedIds() { return new HashSet<>(this.tab.keySet()); }
        public Set<String> tabNamesLower() {
            Set<String> out = new HashSet<>();
            for (String n : this.tab.values()) {
                out.add(n.toLowerCase(Locale.ROOT));
            }
            return out;
        }
        public List<Body> players() { return new ArrayList<>(this.bodies); }
        public Body playerByUuid(UUID id) {
            for (Body b : this.bodies) {
                if (b.id.equals(id)) {
                    return b;
                }
            }
            return null;
        }
        public Body playerByEntityId(int entityId) {
            for (Body b : this.bodies) {
                if (b.entityId == entityId) {
                    return b;
                }
            }
            return null;
        }
        public boolean projectileNear(double x, double y, double z, double rSq) { return near(this.projectiles, x, y, z, rSq); }
        public boolean villagerNear(double x, double y, double z, double rSq) { return near(this.villagers, x, y, z, rSq); }
        static boolean near(List<double[]> list, double x, double y, double z, double rSq) {
            for (double[] p : list) {
                double dx = p[0] - x, dy = p[1] - y, dz = p[2] - z;
                if (dx * dx + dy * dy + dz * dz <= rSq) {
                    return true;
                }
            }
            return false;
        }
        public String blockPath(int x, int y, int z) { return this.blocks.getOrDefault(x + "," + y + "," + z, "air"); }
        public boolean powered(int x, int y, int z) { return this.poweredBlocks.contains(x + "," + y + "," + z); }
        public boolean isTarget(String name) { return this.target.test(name); }
        public void sendCompletionRequest(int id, String command) { this.sentProbes.add(id + ":" + command); }

        // Sink
        public void announce(String reason, String subject, boolean named) {
            this.announced.add(reason + "|" + subject + "|" + named);
        }
        public void news(String name, String what, String detail) {
            this.news.add(name + " " + what + (detail.isEmpty() ? "" : " (" + detail + ")"));
        }
        public void watching(UUID id, String name) {
            this.watching.add(name);
        }
    }

    /** A fresh fake with Steve listed for long enough to be trusted. */
    static Fake withSteve() {
        Fake f = new Fake();
        f.join("Steve");
        f.engine.primeFromTab();
        f.now += 2000;
        return f;
    }

    // ------------------------------------------------------------------ tests

    static void text() {
        check(VanishText.normalize("ɴᴏᴛᴀ Test").equals("nota test"), "small caps fold to plain letters: " + VanishText.normalize("ɴᴏᴛᴀ Test"));
        check(VanishText.normalize("§aSteve§r!!").equals("steve"), "colour codes and punctuation vanish");
        check(VanishText.normalize(null).isEmpty() && VanishText.normalize("   ").isEmpty(), "blank -> empty");
        check(VanishText.containsPlayerName("Steve left the game", "Steve"), "name inside message");
        check(!VanishText.containsPlayerName("Steve2 left the game", "Steve"), "Steve is not Steve2");
        check(!VanishText.containsPlayerName("xSteve left", "Steve"), "no match inside a longer name");
        check(VanishText.containsPlayerName("§c[Admin] §fSTEVE", "steve"), "case and colour insensitive");
        check(VanishText.looksLikeLeaveMessage("Steve left the game", "Steve"), "vanilla leave message");
        check(VanishText.looksLikeLeaveMessage("§e- Steve has quit", "Steve"), "has quit");
        check(!VanishText.looksLikeLeaveMessage("Steve joined the game", "Steve"), "join is no leave");
        check(!VanishText.looksLikeLeaveMessage("Bob left the game", "Steve"), "another player's leave");
        check(!VanishText.looksLikeLeaveMessage("Steve said: Alex left the game", "Steve"), "only the name directly before the verb counts");
        check(VanishText.isUsername("Steve_01") && VanishText.isUsername(".BedrockGuy") && !VanishText.isUsername("a") && !VanishText.isUsername("§bNPC!") && !VanishText.isUsername(null), "isUsername");
        check(VanishText.isPlausiblePlayerName("Abc") && !VanishText.isPlausiblePlayerName("ab") && !VanishText.isPlausiblePlayerName("a b c") && !VanishText.isPlausiblePlayerName("12345678901234567"), "isPlausiblePlayerName");
    }

    static void heuristics() {
        check(VanishHeuristics.suspiciousSound("minecraft:block.chest.open") && VanishHeuristics.suspiciousSound("minecraft:block.wooden_door.close")
                && VanishHeuristics.suspiciousSound("minecraft:block.lever.click") && !VanishHeuristics.suspiciousSound("minecraft:entity.pig.ambient"), "sound list");
        check(VanishHeuristics.suspiciousParticle("minecraft:crit") && VanishHeuristics.suspiciousParticle("minecraft:block") && !VanishHeuristics.suspiciousParticle("minecraft:block_marker")
                && !VanishHeuristics.suspiciousParticle("minecraft:heart"), "particle list (block only exact)");
        check(VanishHeuristics.path("minecraft:Block.Chest").equals("block.chest") && VanishHeuristics.path(null).equals("unknown") && VanishHeuristics.path("plain").equals("plain"), "path");
        check(VanishHeuristics.silentTabRemovalScore(true) == 100 && VanishHeuristics.silentTabRemovalScore(false) == 30, "silent removal score");
        check(VanishHeuristics.tabDepartureReason("Vanish Event: hidden from TAB") && VanishHeuristics.tabDepartureReason("Vanish Event: entity remained")
                && !VanishHeuristics.tabDepartureReason("Vanish Event: switched to spectator") && !VanishHeuristics.tabDepartureReason("Invisible Entity: tab"), "tab departure reasons");
        // BlockPos.asLong: x<<38 | z<<12 | y, with masks
        check(VanishHeuristics.pack(1, 2, 3) == (1L << 38 | 3L << 12 | 2L), "pack matches BlockPos layout");
        check(VanishHeuristics.pack(-1, -1, -1) == (0x3FFFFFFL << 38 | 0x3FFFFFFL << 12 | 0xFFFL), "pack negative");
        Map<Long, Long> m = new HashMap<>();
        VanishHeuristics.markSelfFootprint(10, 64, 10, "oak_door", m, 5L);
        check(m.size() == 3 && m.containsKey(VanishHeuristics.pack(10, 65, 10)) && m.containsKey(VanishHeuristics.pack(10, 63, 10)), "door marks both halves");
        m.clear();
        VanishHeuristics.markSelfFootprint(10, 64, 10, "oak_trapdoor", m, 5L);
        check(m.size() == 1, "trapdoor is a single block");
        m.clear();
        VanishHeuristics.markSelfFootprint(10, 64, 10, "red_bed", m, 5L);
        check(m.size() == 5 && m.containsKey(VanishHeuristics.pack(11, 64, 10)) && m.containsKey(VanishHeuristics.pack(10, 64, 9)), "bed marks the four neighbours");
        check(VanishHeuristics.compass(0, 0).isEmpty() && VanishHeuristics.compass(5, -5).equals("NE") && VanishHeuristics.compass(-5, 5).equals("SW"), "compass");
        check(VanishHeuristics.located(3, 0, 4).equals("5m") || VanishHeuristics.located(3, 0, 4).startsWith("5m"), "located distance");
        check(VanishHeuristics.located(0, 10, -6).equals("12m N"), "located: " + VanishHeuristics.located(0, 10, -6));
        check(VanishHeuristics.tag("CRITICAL", "x").equals("ALERT") && VanishHeuristics.tag("A", "Vanish Event: x").equals("VANISH")
                && VanishHeuristics.tag("A", "Ghost Particle: crit").equals("PARTICLE") && VanishHeuristics.tag("A", "?").equals("WATCH"), "tags");
        check(VanishHeuristics.doorLike("minecraft:block.wooden_door.open") && !VanishHeuristics.doorLike("minecraft:block.wooden_trapdoor.open")
                && VanishHeuristics.doorLike("minecraft:block.fence_gate.open"), "doorLike");
    }

    static void context() {
        VanishContext c = new VanishContext();
        Object w1 = new Object(), w2 = new Object(), p1 = new Object(), p2 = new Object();
        check(!c.moved(w1, p1, o -> false, "vanilla"), "first look is no move");
        check(!c.moved(w1, p1, o -> false, "vanilla"), "same place");
        check(!c.moved(w1, p1, o -> false, null), "brand unknown meanwhile is no move");
        check(c.moved(w2, p1, o -> false, "vanilla"), "new world is a move");
        check(c.moved(w2, p2, o -> false, "vanilla"), "new player object (alive old one) is a move");
        check(!c.moved(w2, p1, o -> true, "vanilla"), "new player object after death is a respawn, no move");
        check(c.moved(w2, p1, o -> false, "velocity"), "brand change is a move");
        check(!c.moved(null, null, o -> false, null) && !c.moved(w1, p1, o -> false, "x"), "leaving the world forgets the old one");
    }

    static void tabHide() {
        Fake f = withSteve();
        UUID steve = f.tab.keySet().iterator().next();
        f.listed.remove(steve);
        f.engine.offer(Observation.tabHide(steve));
        f.ticks(25);
        check(f.newsHas("Steve vanished (hidden from TAB)"), "hidden from tab is reported: " + f.news);
        check(f.engine.hudEntries().stream().anyMatch(e -> e.name().equals("Steve") && e.tag().equals("VANISH")), "summary row exists");
        // visible again: listed again, spell older than 1 s
        f.listed.add(steve);
        f.ticks(15);
        check(f.newsHas("Steve is visible again"), "visible again: " + f.news);
        check(f.engine.hudEntries().isEmpty(), "row removed once visible");
    }

    static void tabHideNeedsTrustAndNoLeaveMessage() {
        Fake f = new Fake();
        UUID id = f.join("Alex");
        f.engine.primeFromTab();
        f.now += 500; // listed for only 0.5 s: NPC / join flicker
        f.listed.remove(id);
        f.engine.offer(Observation.tabHide(id));
        f.ticks(25);
        check(f.news.isEmpty(), "not listed long enough -> ignored: " + f.news);

        Fake g = withSteve();
        UUID steve = g.tab.keySet().iterator().next();
        g.engine.offerChat("Steve left the game", "Steve");
        g.listed.remove(steve);
        g.engine.offer(Observation.tabHide(steve));
        g.ticks(25);
        check(g.news.isEmpty(), "a leave message explains it: " + g.news);

        Fake h = withSteve();
        UUID s3 = h.tab.keySet().iterator().next();
        h.target = n -> false;
        h.listed.remove(s3);
        h.engine.offer(Observation.tabHide(s3));
        h.ticks(25);
        check(h.news.isEmpty(), "players that are not targets are ignored");

        Fake k = withSteve();
        UUID s4 = k.tab.keySet().iterator().next();
        k.engine.offerTabListed(Map.of(s4, false));
        k.ticks(25);
        check(k.newsHas("Steve vanished"), "offerTabListed with one unlisted player works");
        Fake big = new Fake();
        Map<UUID, Boolean> many = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            UUID x = big.join("P" + i + "xx");
            many.put(x, false);
        }
        big.engine.primeFromTab();
        big.now += 2000;
        big.engine.offerTabListed(many);
        big.ticks(25);
        check(big.news.isEmpty(), "four at once is a list reshuffle, not a vanish");
    }

    static void tabRemoval() {
        // 1: body still around
        Fake f = withSteve();
        UUID steve = f.tab.keySet().iterator().next();
        f.body(steve, "Steve", 5, 64, 5, false);
        f.tab.remove(steve);
        f.listed.remove(steve);
        f.engine.offerTabRemove(List.of(steve));
        f.ticks(40);
        // the entity is provably around, so the report is the strong "watching you" alert, not the plain news line
        check(f.watching.contains("Steve") && f.engine.hudEntries().stream().anyMatch(e -> e.reason().equals("Vanish Event: entity remained")),
                "entity remained: " + f.news + " watching=" + f.watching);

        // 2: server is known to announce leaves -> silent removal is certain
        Fake g = withSteve();
        UUID steve2 = g.tab.keySet().iterator().next();
        g.engine.offerChat("Other left the game", "Other");
        g.ticks(1);
        check(g.engine.serverSendsLeaveMessages(), "leave message teaches the server style");
        g.tab.remove(steve2);
        g.listed.remove(steve2);
        g.engine.offerTabRemove(List.of(steve2));
        g.ticks(40);
        check(g.newsHas("Steve vanished (left TAB silently)"), "silent tab removal with leave messages: " + g.news);

        // 3: leave message arrives with the removal -> nothing
        Fake h = withSteve();
        UUID steve3 = h.tab.keySet().iterator().next();
        h.tab.remove(steve3);
        h.listed.remove(steve3);
        h.engine.offerTabRemove(List.of(steve3));
        h.engine.offerChat("Steve left the game", "Steve");
        h.ticks(40);
        check(h.news.isEmpty() && h.watching.isEmpty() && h.engine.hudEntries().isEmpty(), "normal quit: " + h.news);

        // 3b: leave message from before (cached) counts too and teaches the style
        Fake h2 = withSteve();
        UUID s5 = h2.tab.keySet().iterator().next();
        h2.engine.offerChat("Steve left the game", null);
        h2.tab.remove(s5);
        h2.listed.remove(s5);
        h2.engine.offerTabRemove(List.of(s5));
        h2.ticks(40);
        check(h2.news.isEmpty() && h2.engine.serverSendsLeaveMessages(), "cached leave line explains the removal");

        // 4: unknown server style, no region proof -> stays quiet
        Fake i = withSteve();
        UUID steve4 = i.tab.keySet().iterator().next();
        i.tab.remove(steve4);
        i.listed.remove(steve4);
        i.engine.offerTabRemove(List.of(steve4));
        i.ticks(40);
        check(i.news.isEmpty() && i.watching.isEmpty() && i.engine.signalCount() == 0, "weak evidence without proof is dropped");

        // 5: same, but Steve was seen around you a minute ago -> a weak VANISH signal exists
        Fake j = withSteve();
        UUID steve5 = j.tab.keySet().iterator().next();
        Body b = j.body(steve5, "Steve", 6, 64, 0, false);
        j.ticks(10); // region memory is refreshed every 5 ticks
        j.bodies.remove(b);
        j.tab.remove(steve5);
        j.listed.remove(steve5);
        j.engine.offerTabRemove(List.of(steve5));
        j.ticks(30);
        check(j.engine.signalCount() == 1, "silent removal of a recently seen player becomes a signal: " + j.engine.signalCount());
        // a second kind of evidence (a chest opening 12 blocks away, nobody visible) now makes him a watcher
        j.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 12, 64, 0, "minecraft:block.chest.open"));
        j.ticks(2);
        check(j.announcedHas("Suspicious Sound: block.chest.open"), "sound was announced: " + j.announced);
        check(j.watching.contains("Steve"), "two kinds of signal, one with proof: watching you: " + j.watching);
        check(j.engine.criticalActive() && j.engine.hudEntries().get(0).tag().equals("ALERT"), "alert row first");
        // a later leave message retracts the VANISH signal
        j.engine.offerChat("Steve left the game", "Steve");
        j.ticks(1);
        check(j.engine.signalCount() == 1, "leave message removes the tab-based signal, the sound stays");

        // 6: region memory is limited to ten minutes
        Fake k = withSteve();
        UUID steve6 = k.tab.keySet().iterator().next();
        Body b6 = k.body(steve6, "Steve", 6, 64, 0, false);
        k.ticks(10);
        k.bodies.remove(b6);
        k.ticks(10, 70_000); // 11+ minutes later, the listing must still be fresh
        k.tab.remove(steve6);
        k.listed.remove(steve6);
        k.engine.offerTabRemove(List.of(steve6));
        k.ticks(30);
        check(k.engine.signalCount() == 0, "region proof expires after 10 minutes");

        // 7: batches of 4+ are server events
        Fake l = withSteve();
        List<UUID> four = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        l.engine.offerTabRemove(four);
        l.ticks(2);
        check(l.engine.pendingVanishCount() == 0, "no pending vanish from a mass removal");
    }

    static void spectator() {
        Fake f = withSteve();
        UUID steve = f.tab.keySet().iterator().next();
        f.ticks(1);
        f.engine.offerGamemodes(Map.of(steve, "spectator"));
        f.ticks(25);
        check(f.watching.contains("Steve") || f.newsHas("Steve switched to spectator"), "spectator switch is reported: " + f.news + f.watching);
        // by default the game mode update proves he is around: that makes it the strong alert
        check(f.watching.contains("Steve") && f.engine.criticalActive(), "spectator + same-place proof -> watching: " + f.watching);

        // when the player really is far away (target = false) nothing is said
        Fake g = withSteve();
        UUID s = g.tab.keySet().iterator().next();
        g.target = n -> false;
        g.engine.offerGamemodes(Map.of(s, "spectator"));
        g.ticks(25);
        check(g.news.isEmpty() && g.watching.isEmpty(), "non-targets are ignored");

        // survival again ends the spell
        Fake h = withSteve();
        UUID s2 = h.tab.keySet().iterator().next();
        h.engine.offerGamemodes(Map.of(s2, "spectator"));
        h.ticks(25);
        h.engine.offerGamemodes(Map.of(s2, "survival"));
        h.ticks(2);
        check(h.engine.spellCount() == 0, "spell ended by survival");
        // creative for somebody not hidden is plain news
        h.engine.offerGamemodes(Map.of(s2, "creative"));
        h.ticks(2);
        check(h.newsHas("Steve switched to creative"), "other mode changes are told: " + h.news);
    }

    static void probe() {
        Fake f = withSteve();
        f.join("Alex");
        f.engine.primeFromTab();
        f.ticks(99);
        check(f.sentProbes.isEmpty(), "probe not before tick 100");
        f.ticks(1);
        check(f.sentProbes.size() == 1 && f.sentProbes.get(0).equals("30000:minecraft:msg "), "probe sent at tick 100: " + f.sentProbes);
        check(!f.engine.offerCompletion(555, List.of("Ghost")), "foreign suggestion ids are not ours");
        check(f.engine.offerCompletion(30000, List.of("Steve", "Alex", "Ghost", "Me", " Ghost2 ", "", "ab")), "our answer is swallowed");
        check(!f.engine.offerCompletion(30000, List.of("Ghost")), "an id works once");
        f.ticks(1);
        check(f.newsHas("Ghost vanished") || f.newsHas("Ghost2 vanished") || f.watching.size() > 0 || f.engine.spellCount() == 2,
                "targetable names missing from the tab are collected: spells=" + f.engine.spellCount());
        check(f.engine.spellCount() == 2, "exactly Ghost and Ghost2 (not Steve/Alex in the tab, not me, not 'ab'): " + f.engine.spellCount());
        // more than three missing names looks like a different list (lobby plugin): ignored
        Fake g = withSteve();
        g.ticks(100);
        g.engine.offerCompletion(30000, List.of("Aaa", "Bbb", "Ccc", "Ddd"));
        g.ticks(1);
        check(g.engine.spellCount() == 0, "four missing names are ignored");
        // switched off: no probe
        Fake h = withSteve();
        h.engine.probeEnabled = false;
        h.ticks(250);
        check(h.sentProbes.isEmpty(), "probe can be disabled");
        // ids wrap
        Fake w = withSteve();
        w.ticks(100 * 3);
        check(w.sentProbes.size() == 3 && w.sentProbes.get(2).startsWith("30002:"), "ids count up: " + w.sentProbes);
    }

    static void invisible() {
        Fake f = withSteve();
        UUID steve = f.tab.keySet().iterator().next();
        f.body(steve, "Steve", 10, 64, 0, true);
        f.ticks(5);
        check(f.announcedHas("Invisible Entity: metadata flag|Steve|true"), "invisible player entity: " + f.announced);
        int n = f.announced.size();
        f.ticks(20);
        check(f.announced.size() == n, "5 s cooldown per subject and kind");
        f.ticks(100);
        check(f.announced.size() > n, "announced again after the cooldown");
        // too far away
        Fake g = withSteve();
        UUID s = g.tab.keySet().iterator().next();
        g.body(s, "Steve", 100, 64, 0, true);
        g.ticks(10);
        check(g.announced.isEmpty(), "outside 64 blocks");
        // not credible (NPC name)
        Fake h = new Fake();
        UUID npc = h.join("§bShop!");
        h.engine.primeFromTab();
        h.now += 2000;
        h.body(npc, "§bShop!", 5, 64, 0, true);
        h.ticks(10);
        check(h.announced.isEmpty(), "not a username");
        // metadata packet path
        Fake m = withSteve();
        UUID s2 = m.tab.keySet().iterator().next();
        Body bb = m.body(s2, "Steve", 4, 64, 0, true);
        m.engine.offer(Observation.entityMetadata(bb.entityId));
        m.ticks(1);
        check(m.announcedHas("Invisible Entity"), "metadata packet inspects the entity");
        // visible players do not count
        Fake v = withSteve();
        UUID s3 = v.tab.keySet().iterator().next();
        v.body(s3, "Steve", 4, 64, 0, false);
        v.ticks(10);
        check(v.announced.isEmpty(), "visible player is no signal");
    }

    static Fake quiet() {
        Fake f = new Fake();
        f.engine.primeFromTab();
        f.now += 2000;
        return f;
    }

    static void sounds() {
        Fake f = quiet();
        f.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 10, 64, 0, "minecraft:block.chest.open"));
        f.ticks(1);
        check(f.announced.size() == 1 && f.announced.get(0).equals("Suspicious Sound: block.chest.open|10m E|false"), "chest sound with no cause: " + f.announced);
        // not suspicious / out of range
        Fake g = quiet();
        g.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 10, 64, 0, "minecraft:entity.pig.ambient"));
        g.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 80, 64, 0, "minecraft:block.chest.open"));
        g.ticks(1);
        check(g.announced.isEmpty(), "boring sound and far sound");
        // visible player next to the source
        Fake h = quiet();
        h.body(UUID.randomUUID(), "Bob", 11, 64, 0, false);
        h.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 10, 64, 0, "minecraft:block.chest.open"));
        h.ticks(1);
        check(h.announced.isEmpty(), "visible player within 4 blocks explains it");
        Fake h2 = quiet();
        h2.body(UUID.randomUUID(), "Bob", 11, 64, 0, true);
        h2.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 10, 64, 0, "minecraft:block.chest.open"));
        h2.ticks(1);
        check(h2.announced.size() == 1, "an invisible player does not explain it");
        // projectile
        Fake p = quiet();
        p.projectiles.add(new double[] {10, 64, 1});
        p.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 10, 64, 0, "minecraft:block.lever.click"));
        p.ticks(1);
        check(p.announced.isEmpty(), "projectile explains a button/lever");
        // own action within one second
        Fake a = quiet();
        a.engine.localAction();
        a.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 10, 64, 0, "minecraft:block.chest.open"));
        a.ticks(1);
        check(a.announced.isEmpty(), "own action");
        Fake a2 = quiet();
        a2.engine.localAction();
        a2.now += 1100;
        a2.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 10, 64, 0, "minecraft:block.chest.open"));
        a2.ticks(1);
        check(a2.announced.size() == 1, "own action more than a second ago no longer counts");
        // block you placed
        Fake s = quiet();
        s.engine.selfInteract(10, 64, 0, 10, 65, 0, "oak_door");
        s.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 10.5, 65.2, 0.5, "minecraft:block.wooden_door.open"));
        s.now += 1500;
        s.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 10.5, 65.2, 0.5, "minecraft:block.wooden_door.open"));
        s.ticks(1);
        check(s.announced.isEmpty(), "door you just placed (upper half too): " + s.announced);
        // your own container screen
        Fake c = quiet();
        c.container = true;
        c.ticks(1);
        c.container = false;
        c.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 3, 64, 0, "minecraft:block.chest.close"));
        c.ticks(1);
        check(c.announced.isEmpty(), "chest close right after your own container screen");
        c.now += 3000;
        c.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 3, 64, 0, "minecraft:block.chest.close"));
        c.ticks(1);
        check(c.announced.size() == 1, "later chest sound counts again");
        // door powered by redstone
        Fake d = quiet();
        d.poweredBlocks.add("10,64,0");
        d.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 10.5, 64.5, 0.5, "minecraft:block.iron_door.open"));
        d.ticks(1);
        check(d.announced.isEmpty(), "powered door");
        d.poweredBlocks.clear();
        d.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 10.5, 64.5, 0.5, "minecraft:block.iron_door.close"));
        d.ticks(1);
        check(d.announced.isEmpty(), "door that was powered a moment ago is still a machine");
        // villager door
        Fake v = quiet();
        v.villagers.add(new double[] {11, 64, 0});
        v.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 10.5, 64.5, 0.5, "minecraft:block.wooden_door.open"));
        v.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 10.5, 64.5, 0.5, "minecraft:block.chest.open"));
        v.ticks(1);
        check(v.announced.size() == 1 && v.announced.get(0).contains("chest"), "villager explains doors only: " + v.announced);
        // explosion
        Fake e = quiet();
        e.engine.offer(Observation.explosion(10, 64, 0, 4f));
        e.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 15, 64, 0, "minecraft:block.chest.open"));
        e.ticks(1);
        check(e.announced.isEmpty(), "near an explosion (radius + 4)");
        e.now += 3500;
        e.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 15, 64, 0, "minecraft:block.chest.open"));
        e.ticks(1);
        check(e.announced.size() == 1, "explosions are forgotten after 3 s");
        // entity sound from an invisible player
        Fake i = withSteve();
        UUID steve = i.tab.keySet().iterator().next();
        Body bb = i.body(steve, "Steve", 6, 64, 0, true);
        i.engine.offer(Observation.entitySound(bb.entityId, "minecraft:block.chest.open"));
        i.ticks(1);
        check(i.announcedHas("Suspicious Sound: invisible source|Steve|true"), "entity sound of an invisible player: " + i.announced);
    }

    static void particles() {
        Fake f = quiet();
        f.engine.offer(Observation.position(ObservationType.PARTICLE, 10, 64, 0, "minecraft:crit"));
        f.ticks(1);
        check(f.announced.size() == 1 && f.announced.get(0).startsWith("Ghost Particle: crit|10m E"), "crit particle: " + f.announced);
        // smoke needs two within 2 s
        Fake g = quiet();
        g.engine.offer(Observation.position(ObservationType.PARTICLE, 10, 64, 0, "minecraft:large_smoke"));
        g.ticks(1);
        check(g.announced.isEmpty(), "first smoke is ignored");
        g.engine.offer(Observation.position(ObservationType.PARTICLE, 10, 64, 0, "minecraft:large_smoke"));
        g.ticks(1);
        check(g.announced.size() == 1, "second smoke within 2 s counts");
        Fake g2 = quiet();
        g2.engine.offer(Observation.position(ObservationType.PARTICLE, 10, 64, 0, "minecraft:block"));
        g2.ticks(1);
        g2.now += 2500;
        g2.engine.offer(Observation.position(ObservationType.PARTICLE, 10, 64, 0, "minecraft:block"));
        g2.ticks(1);
        check(g2.announced.isEmpty(), "two block particles 2.5 s apart are two first ones");
        // smoke next to a campfire
        Fake c = quiet();
        c.blocks.put("11,63,0", "campfire");
        c.engine.offer(Observation.position(ObservationType.PARTICLE, 10, 64, 0, "minecraft:large_smoke"));
        c.engine.offer(Observation.position(ObservationType.PARTICLE, 10, 64, 0, "minecraft:large_smoke"));
        c.ticks(1);
        check(c.announced.isEmpty(), "smoke near a campfire");
        // smoke within 2.5 blocks of you (your own fire, cooking ...)
        Fake n = quiet();
        n.engine.offer(Observation.position(ObservationType.PARTICLE, 2, 64, 0, "minecraft:block"));
        n.engine.offer(Observation.position(ObservationType.PARTICLE, 2, 64, 0, "minecraft:block"));
        n.ticks(1);
        check(n.announced.isEmpty(), "weak particle right next to you");
        // crit right next to you still counts (not weak)
        Fake n2 = quiet();
        n2.engine.offer(Observation.position(ObservationType.PARTICLE, 2, 64, 0, "minecraft:crit"));
        n2.ticks(1);
        check(n2.announced.size() == 1, "crit next to you counts");
        // your own swing
        Fake s = quiet();
        s.engine.localAction();
        s.engine.offer(Observation.position(ObservationType.PARTICLE, 10, 64, 0, "minecraft:crit"));
        s.ticks(1);
        check(s.announced.isEmpty(), "particle right after your own action");
        s.now += 950;
        s.engine.offer(Observation.position(ObservationType.PARTICLE, 10, 64, 0, "minecraft:crit"));
        s.ticks(1);
        check(s.announced.size() == 1, "particle 0.9 s after your action counts");
    }

    static void announceRateLimit() {
        Fake f = quiet();
        for (int i = 0; i < 10; i++) {
            f.engine.offer(Observation.position(ObservationType.PARTICLE, 10 + i * 3, 64, 0, "minecraft:crit"));
            // distinct subjects: different "Nm E" labels
        }
        f.ticks(1);
        check(f.announced.size() == 6, "at most six lines per four seconds: " + f.announced.size());
        f.now += 4500;
        f.engine.offer(Observation.position(ObservationType.PARTICLE, 40, 64, 0, "minecraft:crit"));
        f.ticks(1);
        check(f.announced.size() == 7, "window moves on");
    }

    static void criticalNeedsProofAndTwoKinds() {
        // two kinds, but nobody provably nearby: no alert
        Fake f = quiet();
        f.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 10, 64, 0, "minecraft:block.chest.open"));
        f.engine.offer(Observation.position(ObservationType.PARTICLE, 10, 64, 0, "minecraft:crit"));
        f.ticks(1);
        check(f.watching.isEmpty() && !f.engine.criticalActive(), "locations alone never name a watcher");
        // invisible player (35, proof by body) alone is one kind -> no alert, a sound from him adds the second
        Fake g = withSteve();
        UUID steve = g.tab.keySet().iterator().next();
        Body bb = g.body(steve, "Steve", 8, 64, 0, true);
        g.ticks(5);
        check(g.watching.isEmpty(), "one kind only");
        g.engine.offer(Observation.entitySound(bb.entityId, "minecraft:block.chest.open"));
        g.ticks(2); // the alert is raised on the tick after the decision
        check(g.watching.size() == 1 && g.watching.get(0).equals("Steve"), "invisible + sound from him: " + g.watching);
        // cooldown of 12 s
        g.engine.offer(Observation.entitySound(bb.entityId, "minecraft:block.chest.close"));
        g.ticks(100); // sound cooldown 3 s passes, critical cooldown not
        check(g.watching.size() == 1, "no duplicate alert inside 12 s: " + g.watching.size());
        // signals expire after 15 s
        Fake h = withSteve();
        UUID s2 = h.tab.keySet().iterator().next();
        Body b2 = h.body(s2, "Steve", 8, 64, 0, true);
        h.ticks(5);
        h.bodies.clear();
        h.ticks(400, 50); // 20 s
        h.body(s2, "Steve", 8, 64, 0, false);
        h.engine.offer(Observation.position(ObservationType.POSITIONAL_SOUND, 30, 64, 0, "minecraft:block.chest.open"));
        h.ticks(1);
        check(h.watching.isEmpty(), "the old invisible signal is out of the 15 s window: " + h.watching);
    }

    static void hudRows() {
        Fake f = new Fake();
        UUID a = f.join("Alice");
        UUID b = f.join("Bobby");
        f.engine.primeFromTab();
        f.now += 2000;
        f.listed.remove(a);
        f.listed.remove(b);
        f.engine.offer(Observation.tabHide(a));
        f.engine.offer(Observation.tabHide(b));
        f.ticks(2);
        List<VanishEngine.HudEntry> rows = f.engine.hudEntries();
        check(rows.size() == 2 && rows.get(0).name().equals("Alice") && rows.get(1).name().equals("Bobby"), "equal scores sort by name: " + rows);
        check(rows.get(0).tag().equals("VANISH") && rows.get(0).value().equals("Alice"), "row tag and value");
        f.ticks(500, 50); // 25 s
        check(f.engine.hudEntries().isEmpty() && !f.engine.hasHudContent(), "rows expire after 20 s");
        check(new VanishEngine.HudEntry("Someone", "Ghost Particle: x").value().equals("Someone") && new VanishEngine.HudEntry("Unknown", "x").value().equals("Staff"), "value fallback");
        check(new VanishEngine.HudEntry("CRITICAL", "A_very_long_name_indeed_1").value().length() == 16, "CRITICAL value is the shortened watcher");
    }

    static void resetClears() {
        Fake f = withSteve();
        UUID steve = f.tab.keySet().iterator().next();
        f.listed.remove(steve);
        f.engine.offer(Observation.tabHide(steve));
        f.ticks(3);
        check(f.engine.spellCount() == 1, "spell exists");
        f.engine.reset();
        check(f.engine.spellCount() == 0 && f.engine.tickCounter() == 0 && f.engine.hudEntries().isEmpty() && !f.engine.serverSendsLeaveMessages(), "reset clears the state");
        f.ticks(25);
        check(f.news.isEmpty(), "a tab hide of the forgotten player stays quiet until it is listed long enough again: " + f.news);
    }

    public static void main(String[] args) {
        text();
        heuristics();
        context();
        tabHide();
        tabHideNeedsTrustAndNoLeaveMessage();
        tabRemoval();
        spectator();
        probe();
        invisible();
        sounds();
        particles();
        announceRateLimit();
        criticalNeedsProofAndTwoKinds();
        hudRows();
        resetClears();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
