package dev.dihclient.port.vanish;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Decision logic of the Anti Vanish module without any Minecraft type: it is fed with observations (tab list changes,
 * chat lines, sounds, particles ...) and with a view of the game ({@link Env}), and reports through a {@link Sink}.
 * <p>
 * Two kinds of evidence, as in the original:
 * <ul>
 *   <li>Vanish events (tab entry hidden/removed without a leave message, game mode switched to spectator, still
 *       targetable by command completion while off the tab list). They report straight away.</li>
 *   <li>Sensor signals (invisible player entity, suspicious sound or particle with no visible cause). Each one alone is
 *       only announced; two different kinds within 15 s, one of them naming a player who is provably in your region, and a
 *       combined weight of 35 raise the "is watching you" alert.</li>
 * </ul>
 * Everything except {@link #offer} runs on the game thread.
 */
public final class VanishEngine {
    public static final String SPECTATOR = "spectator";
    private static final String PROBE_COMMAND = "minecraft:msg";
    private static final int PROBE_ID_FIRST = 30000;
    private static final int PROBE_ID_LAST = 40000;
    private static final double SENSOR_RANGE = 64.0;
    private static final long SIGNAL_WINDOW_MS = 15000L;
    private static final long DETECTION_TTL_MS = 20000L;
    private static final long CRITICAL_COOLDOWN_MS = 12000L;
    private static final long CRITICAL_SHOWN_MS = 7000L;
    private static final long ANNOUNCE_COOLDOWN_MS = 1500L;
    private static final long ANNOUNCE_WINDOW_MS = 4000L;
    private static final int MAX_ANNOUNCE_PER_WINDOW = 6;
    private static final int MAX_OBSERVATIONS_PER_TICK = 512;
    private static final int CRITICAL_SCORE = 35;
    private static final long SELF_PLACE_TTL_MS = 6000L;
    private static final long CONTAINER_SELF_GRACE_MS = 2500L;
    private static final long RECENT_MESSAGE_TTL_MS = 8000L;
    private static final long TRUSTED_LISTED_MS = 1500L;
    private static final long REGION_MEMORY_MS = 600000L;
    private static final long CONFIRMED_DEPARTURE_MS = 5000L;
    private static final int PENDING_VANISH_TICKS = 20;

    /** One player entity around you (never yourself). */
    public static final class Body {
        public final UUID id;
        public final int entityId;
        public final String name;
        public final double x;
        public final double y;
        public final double z;
        public final double distance;
        public final boolean invisible;

        public Body(UUID id, int entityId, String name, double x, double y, double z, double distance, boolean invisible) {
            this.id = id;
            this.entityId = entityId;
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.distance = distance;
            this.invisible = invisible;
        }
    }

    /** One tab list entry. */
    public record TabEntry(UUID id, String name) {
    }

    /** What the game shows right now. All calls on the game thread; null / empty when unknown. */
    public interface Env {
        long now();

        UUID selfId();

        String selfName();

        double[] selfPos();

        /** True while a container screen of your own is open. */
        boolean containerOpen();

        /** Name of the tab list entry, or null when there is no entry for the id. */
        String tabName(UUID id);

        boolean inTab(UUID id);

        /** Tab entries that are listed (visible in the tab overlay). */
        Collection<TabEntry> listedEntries();

        /** Every connected player id, listed or not. */
        Set<UUID> connectedIds();

        /** All names in the tab list, lower case. */
        Set<String> tabNamesLower();

        /** Other player entities in the loaded world. */
        List<Body> players();

        Body playerByUuid(UUID id);

        Body playerByEntityId(int entityId);

        boolean projectileNear(double x, double y, double z, double radiusSq);

        boolean villagerNear(double x, double y, double z, double radiusSq);

        /** Registry path of the block at the position, e.g. "campfire". */
        String blockPath(int x, int y, int z);

        /** Block has a POWERED property that is set, or receives redstone power. */
        boolean powered(int x, int y, int z);

        /** Whether this name is one the user wants to be told about (staff list, watchlist, everyone ...). */
        boolean isTarget(String name);

        /** Asks the server for command completions; may throw, the engine ignores that. */
        void sendCompletionRequest(int id, String command);
    }

    /** How the engine talks to the user. */
    public interface Sink {
        /** A sensor fired. {@code subject} is null when unknown; {@code named} says it is a player name rather than a location. */
        void announce(String reason, String subject, boolean named);

        /** A player vanished / switched / is visible again: "{name} {what} ({detail})". */
        void news(String name, String what, String detail);

        /** Strong evidence that the player is near you and watching. */
        void watching(UUID id, String name);
    }

    public enum ObservationType {
        TAB_REMOVE, TAB_HIDE, PLAYER_LEFT, SYSTEM_CHAT, ENTITY_METADATA, POSITIONAL_SOUND, ENTITY_SOUND, PARTICLE, EXPLOSION, GAMEMODE
    }

    /** Something the packet thread saw. Immutable, handed over through a queue. */
    public record Observation(ObservationType type, UUID profileId, int entityId, double x, double y, double z, String detail) {
        public static Observation tabRemove(UUID id) {
            return new Observation(ObservationType.TAB_REMOVE, id, -1, 0.0, 0.0, 0.0, "");
        }

        public static Observation tabHide(UUID id) {
            return new Observation(ObservationType.TAB_HIDE, id, -1, 0.0, 0.0, 0.0, "");
        }

        public static Observation gamemode(UUID id, String mode) {
            return new Observation(ObservationType.GAMEMODE, id, -1, 0.0, 0.0, 0.0, mode);
        }

        public static Observation playerLeft(String name) {
            return new Observation(ObservationType.PLAYER_LEFT, null, -1, 0.0, 0.0, 0.0, name);
        }

        public static Observation systemChat(String text) {
            return new Observation(ObservationType.SYSTEM_CHAT, null, -1, 0.0, 0.0, 0.0, text);
        }

        public static Observation entityMetadata(int id) {
            return new Observation(ObservationType.ENTITY_METADATA, null, id, 0.0, 0.0, 0.0, "");
        }

        public static Observation explosion(double x, double y, double z, float radius) {
            return new Observation(ObservationType.EXPLOSION, null, -1, x, y, z, Float.toString(radius));
        }

        public static Observation entitySound(int id, String sound) {
            return new Observation(ObservationType.ENTITY_SOUND, null, id, 0.0, 0.0, 0.0, sound);
        }

        public static Observation position(ObservationType type, double x, double y, double z, String detail) {
            return new Observation(type, null, -1, x, y, z, detail);
        }
    }

    private enum SignalType {
        VANISH, INVISIBLE, PARTICLE, SOUND
    }

    private record RegionProof(UUID id, int distance) {
    }

    private record Signal(SignalType type, String subject, RegionProof proof, String reason, int weight, long timeMs) {
    }

    private record KnownPlayer(UUID uuid, String name) {
    }

    private record PendingVanish(UUID uuid, String name, int dueTick) {
    }

    private record RecentMessage(String text, long atMs) {
    }

    private record Explosion(double x, double y, double z, double radius, long timeMs) {
    }

    private static final class Detection {
        String name;
        String reason;
        int score;
        long expiresAt;

        Detection(String name, String reason, int score, long expiresAt) {
            this.name = name;
            this.reason = reason;
            this.score = score;
            this.expiresAt = expiresAt;
        }
    }

    private static final class HiddenSpell {
        final UUID uuid;
        final String name;
        final boolean spectator;
        final String detail;
        final RegionProof proof;
        final int sinceTick;
        final long sinceMs;
        boolean told;
        boolean watched;

        HiddenSpell(UUID uuid, String name, boolean spectator, String detail, RegionProof proof, int sinceTick, long sinceMs) {
            this.uuid = uuid;
            this.name = name;
            this.spectator = spectator;
            this.detail = detail;
            this.proof = proof;
            this.sinceTick = sinceTick;
            this.sinceMs = sinceMs;
        }

        String how() {
            return this.spectator ? switchedTo(SPECTATOR) : "vanished";
        }
    }

    /** When a player was last seen in your region (replaces the Donut staff list's "sighting"). */
    private static final class Sighting {
        long provenAt;
        boolean nearby;
        int bodyTick;

        boolean marked(long now) {
            return this.nearby || this.provenAt != 0L && now - this.provenAt < REGION_MEMORY_MS;
        }
    }

    /** A row of the summary list. */
    public record HudEntry(String name, String reason) {
        public String tag() {
            return VanishHeuristics.tag(this.name, this.reason);
        }

        public String value() {
            if ("CRITICAL".equalsIgnoreCase(this.name)) {
                return shortName(this.reason);
            }
            String n = this.name == null ? "" : this.name.trim();
            return !n.isBlank() && !"Unknown".equalsIgnoreCase(n) && !"You".equalsIgnoreCase(n) ? shortName(n) : "Staff";
        }
    }

    private final Env env;
    private final Sink sink;
    private final ConcurrentLinkedQueue<Observation> observations = new ConcurrentLinkedQueue<>();
    private final Map<UUID, KnownPlayer> knownPlayers = new HashMap<>();
    private final Map<String, Detection> detections = new LinkedHashMap<>();
    private final Map<String, Long> signalCooldowns = new HashMap<>();
    private final Map<String, Long> announceCooldowns = new HashMap<>();
    private final Deque<Long> announceTimes = new ArrayDeque<>();
    private final Map<Long, Long> selfPlacedBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> confirmedDepartures = new HashMap<>();
    private final Map<Long, Long> automatedMechanisms = new HashMap<>();
    private final Map<String, Deque<Long>> weakParticleBursts = new HashMap<>();
    private final Deque<Signal> signals = new ArrayDeque<>();
    private final Deque<Explosion> recentExplosions = new ArrayDeque<>();
    private final Deque<PendingVanish> pendingVanishes = new ArrayDeque<>();
    private final Map<String, HiddenSpell> hiddenSpells = new LinkedHashMap<>();
    private final Deque<RecentMessage> recentMessages = new ArrayDeque<>();
    private final Map<UUID, Long> listedSinceMs = new HashMap<>();
    private final Map<UUID, Sighting> sightings = new HashMap<>();
    private final Set<Integer> completionRequestIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingChimes = new HashSet<>();
    private volatile List<String> pendingCompletionNames;
    private volatile long lastLocalActionMs;
    private boolean serverSendsLeaveMessages;
    private int nextCompletionId = PROBE_ID_FIRST;
    private int tickCounter;
    private long lastContainerActivityMs;
    private long lastCriticalMs;
    private long criticalUntilMs;
    private String criticalWatcher = "";
    private UUID ghostId;
    private String ghostName;
    /** Set by the module from its setting: whether the command completion probe may be sent at all. */
    public volatile boolean probeEnabled = true;

    public VanishEngine(Env env, Sink sink) {
        this.env = env;
        this.sink = sink;
    }

    // ---------------------------------------------------------------- input from the packet thread

    /** Thread safe. Old entries are dropped when the game thread falls behind. */
    public void offer(Observation observation) {
        if (observation != null) {
            this.observations.offer(observation);
        }
    }

    /** A "players removed from tab" packet; big batches (server restart, lobby switch) are no vanish. */
    public void offerTabRemove(Collection<UUID> ids) {
        if (ids != null && ids.size() < 4) {
            for (UUID id : ids) {
                offer(Observation.tabRemove(id));
            }
        }
    }

    /** A "listed" update; only a few players turning unlisted at once counts. */
    public void offerTabListed(Map<UUID, Boolean> listedById) {
        if (listedById == null) {
            return;
        }
        int unlisted = 0;
        for (Boolean listed : listedById.values()) {
            if (listed != null && !listed) {
                unlisted++;
            }
        }
        if (unlisted > 0 && unlisted < 4) {
            for (Map.Entry<UUID, Boolean> e : listedById.entrySet()) {
                if (e.getValue() != null && !e.getValue()) {
                    offer(Observation.tabHide(e.getKey()));
                }
            }
        }
    }

    public void offerGamemodes(Map<UUID, String> modeById) {
        if (modeById != null) {
            for (Map.Entry<UUID, String> e : modeById.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    offer(Observation.gamemode(e.getKey(), e.getValue()));
                }
            }
        }
    }

    /** A system chat line; {@code departedName} is the argument of the vanilla "left the game" message or "". */
    public void offerChat(String text, String departedName) {
        if (text != null && !text.isBlank()) {
            offer(Observation.systemChat(text));
        }
        if (departedName != null && !departedName.isBlank()) {
            offer(Observation.playerLeft(departedName));
        }
    }

    /**
     * Command completion answer. Thread safe.
     * @return true when this was the answer to our own probe (the caller should swallow the packet)
     */
    public boolean offerCompletion(int id, List<String> suggestions) {
        if (!this.completionRequestIds.remove(id)) {
            return false;
        }
        List<String> names = new ArrayList<>();
        if (suggestions != null) {
            for (String s : suggestions) {
                if (s != null && !s.isBlank()) {
                    names.add(s.trim());
                }
            }
        }
        this.pendingCompletionNames = names;
        return true;
    }

    /** Own actions: sounds and particles right after them are yours. Thread safe. */
    public void localAction() {
        this.lastLocalActionMs = this.env.now();
    }

    /** Own block use: remembers the clicked block and the one next to it (and door/bed halves) as self-made. */
    public void selfInteract(int hitX, int hitY, int hitZ, int nextX, int nextY, int nextZ, String itemPath) {
        long now = this.env.now();
        this.lastLocalActionMs = now;
        long until = now + SELF_PLACE_TTL_MS;
        VanishHeuristics.markSelfFootprint(hitX, hitY, hitZ, itemPath, this.selfPlacedBlocks, until);
        VanishHeuristics.markSelfFootprint(nextX, nextY, nextZ, itemPath, this.selfPlacedBlocks, until);
    }

    // ---------------------------------------------------------------- tick

    /** Once per client tick while in a world. */
    public void tick() {
        playPendingChime();
        this.tickCounter++;
        long now = this.env.now();
        if (this.env.containerOpen()) {
            this.lastContainerActivityMs = now;
        }
        drainObservations();
        processPendingVanishes();
        processCompletionProbe();
        if (this.tickCounter % 100 == 0 && this.probeEnabled) {
            sendCompletionProbe();
        }
        if (this.tickCounter % 10 == 0) {
            trackListedPlayers();
            reviewSpells();
        }
        if (this.tickCounter % 5 == 0) {
            scanPlayers();
        }
        pruneState();
    }

    /** Takes the listed players on enable (before the first tick). */
    public void primeFromTab() {
        trackListedPlayers();
    }

    public void reset() {
        this.observations.clear();
        this.knownPlayers.clear();
        this.detections.clear();
        this.signalCooldowns.clear();
        this.announceCooldowns.clear();
        this.announceTimes.clear();
        this.selfPlacedBlocks.clear();
        this.confirmedDepartures.clear();
        this.automatedMechanisms.clear();
        this.weakParticleBursts.clear();
        this.signals.clear();
        this.recentExplosions.clear();
        this.pendingVanishes.clear();
        this.hiddenSpells.clear();
        this.sightings.clear();
        this.tickCounter = 0;
        this.lastLocalActionMs = 0L;
        this.lastContainerActivityMs = 0L;
        this.lastCriticalMs = 0L;
        this.criticalUntilMs = 0L;
        this.criticalWatcher = "";
        this.pendingChimes.clear();
        this.ghostId = null;
        this.ghostName = null;
        this.recentMessages.clear();
        this.listedSinceMs.clear();
        this.serverSendsLeaveMessages = false;
        this.completionRequestIds.clear();
        this.pendingCompletionNames = null;
        this.nextCompletionId = PROBE_ID_FIRST;
    }

    private void drainObservations() {
        for (int i = 0; i < MAX_OBSERVATIONS_PER_TICK; i++) {
            Observation observation = this.observations.poll();
            if (observation == null) {
                break;
            }
            processObservation(observation);
        }
        while (this.observations.size() > 4096) {
            this.observations.poll();
        }
    }

    private void processObservation(Observation o) {
        switch (o.type()) {
            case TAB_REMOVE -> handleTabRemoval(o.profileId());
            case TAB_HIDE -> handleTabHidden(o.profileId());
            case PLAYER_LEFT -> {
                this.serverSendsLeaveMessages = true;
                confirmDeparture(o.detail());
            }
            case SYSTEM_CHAT -> cacheRecentMessage(o.detail());
            case ENTITY_METADATA -> inspectInvisibleEntity(o.entityId());
            case POSITIONAL_SOUND -> inspectPositionalSound(o);
            case ENTITY_SOUND -> inspectEntitySound(o);
            case PARTICLE -> inspectParticle(o);
            case EXPLOSION -> rememberExplosion(o);
            case GAMEMODE -> announceGamemode(o.profileId(), o.detail());
        }
    }

    // ---------------------------------------------------------------- tab list

    private void trackListedPlayers() {
        long now = this.env.now();
        UUID self = this.env.selfId();
        for (TabEntry info : this.env.listedEntries()) {
            if (info == null || info.id() == null) {
                continue;
            }
            UUID uuid = info.id();
            if (self == null || !self.equals(uuid)) {
                this.listedSinceMs.putIfAbsent(uuid, now);
                String name = info.name();
                if (name != null && !this.knownPlayers.containsKey(uuid)) {
                    this.knownPlayers.put(uuid, new KnownPlayer(uuid, name));
                }
                if (name != null && !this.hiddenSpells.isEmpty()) {
                    endSpell(name, false, 1000L);
                }
            }
        }
        if (this.listedSinceMs.size() > 1024 || this.knownPlayers.size() > 1024) {
            Set<UUID> connected = this.env.connectedIds();
            this.listedSinceMs.keySet().retainAll(connected);
            this.knownPlayers.keySet().retainAll(connected);
        }
    }

    /** Listed for at least 1.5 s: filters NPCs and join flicker. */
    boolean trustedListed(UUID uuid) {
        Long since = uuid == null ? null : this.listedSinceMs.get(uuid);
        return since != null && this.env.now() - since >= TRUSTED_LISTED_MS;
    }

    boolean credibleSubject(UUID uuid, String name) {
        return uuid != null && VanishText.isUsername(name) && this.env.isTarget(name) && trustedListed(uuid);
    }

    private boolean isSelf(UUID uuid) {
        return uuid.equals(this.env.selfId());
    }

    private void handleTabRemoval(UUID uuid) {
        if (uuid != null && !isSelf(uuid)) {
            String name = knownName(uuid);
            if (credibleSubject(uuid, name) && VanishText.isPlausiblePlayerName(name)) {
                this.pendingVanishes.removeIf(pending -> pending.uuid().equals(uuid));
                this.pendingVanishes.addLast(new PendingVanish(uuid, name, this.tickCounter + PENDING_VANISH_TICKS));
            }
        }
    }

    private void handleTabHidden(UUID uuid) {
        if (uuid != null && !isSelf(uuid)) {
            KnownPlayer known = this.knownPlayers.get(uuid);
            String name = known != null && known.name() != null ? known.name() : knownName(uuid);
            if (credibleSubject(uuid, name) && VanishText.isPlausiblePlayerName(name)) {
                if (!recentMessageNames(name)) {
                    reportHidden(uuid, name, false, "Vanish Event: hidden from TAB", "hidden from TAB", 100);
                }
            }
        }
    }

    private String knownName(UUID uuid) {
        KnownPlayer known = this.knownPlayers.get(uuid);
        if (known != null && known.name() != null && !known.name().isBlank()) {
            return known.name();
        }
        return this.env.tabName(uuid);
    }

    private UUID knownUuid(String name) {
        for (KnownPlayer known : this.knownPlayers.values()) {
            if (known.name().equalsIgnoreCase(name)) {
                return known.uuid();
            }
        }
        return null;
    }

    private void confirmDeparture(String displayedName) {
        long now = this.env.now();
        for (KnownPlayer known : new ArrayList<>(this.knownPlayers.values())) {
            if (VanishText.containsPlayerName(displayedName, known.name())) {
                this.confirmedDepartures.put(known.uuid(), now + CONFIRMED_DEPARTURE_MS);
                this.pendingVanishes.removeIf(pending -> pending.uuid().equals(known.uuid()));
                this.hiddenSpells.remove(spellKey(known.name()));
                Detection detection = this.detections.get(known.uuid().toString());
                if (detection != null && VanishHeuristics.tabDepartureReason(detection.reason)) {
                    this.detections.remove(known.uuid().toString());
                }
                this.signals.removeIf(signal -> signal.type() == SignalType.VANISH
                        && signal.subject().equalsIgnoreCase(known.name())
                        && VanishHeuristics.tabDepartureReason(signal.reason()));
            }
        }
    }

    private void processPendingVanishes() {
        while (!this.pendingVanishes.isEmpty() && this.pendingVanishes.peekFirst().dueTick() <= this.tickCounter) {
            PendingVanish pending = this.pendingVanishes.removeFirst();
            if (this.env.inTab(pending.uuid())) {
                continue; // came back (relog, server reshuffling the list)
            }
            long now = this.env.now();
            if (this.confirmedDepartures.getOrDefault(pending.uuid(), 0L) > now) {
                continue; // a leave message named it: really gone
            }
            if (recentMessageNames(pending.name())) {
                this.serverSendsLeaveMessages = true;
                continue;
            }
            Body remaining = this.env.playerByUuid(pending.uuid());
            if (remaining != null) {
                reportHidden(pending.uuid(), pending.name(), false, "Vanish Event: entity remained", "off TAB, still nearby", 100);
                continue;
            }
            String reason = "Vanish Event: silent TAB disappearance";
            int score = VanishHeuristics.silentTabRemovalScore(this.serverSendsLeaveMessages);
            if (this.serverSendsLeaveMessages) {
                reportHidden(pending.uuid(), pending.name(), false, reason, "left TAB silently", score);
            } else {
                RegionProof proof = regionProof(pending.uuid());
                if (proof != null) {
                    this.signals.addLast(new Signal(SignalType.VANISH, pending.name(), proof, reason, score, now));
                    evaluateCritical();
                }
            }
        }
    }

    private void cacheRecentMessage(String text) {
        if (text != null && !text.isBlank()) {
            long now = this.env.now();
            this.recentMessages.addLast(new RecentMessage(text, now));
            while (!this.recentMessages.isEmpty()
                    && (now - this.recentMessages.peekFirst().atMs() > RECENT_MESSAGE_TTL_MS || this.recentMessages.size() > 64)) {
                this.recentMessages.removeFirst();
            }
        }
    }

    /** A chat line of the last 8 s says this player left. */
    boolean recentMessageNames(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        long now = this.env.now();
        for (RecentMessage message : this.recentMessages) {
            if (now - message.atMs() <= RECENT_MESSAGE_TTL_MS && VanishText.looksLikeLeaveMessage(message.text(), name)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- command completion probe

    private void sendCompletionProbe() {
        int id = this.nextCompletionId++;
        if (this.nextCompletionId > PROBE_ID_LAST) {
            this.nextCompletionId = PROBE_ID_FIRST;
        }
        this.completionRequestIds.add(id);
        while (this.completionRequestIds.size() > 8) {
            this.completionRequestIds.remove(this.completionRequestIds.iterator().next());
        }
        try {
            this.env.sendCompletionRequest(id, PROBE_COMMAND + " ");
        } catch (Throwable ignored) {
            // no connection right now; the id expires with the next ones
        }
    }

    /** Names the server completes for /msg that are not in the tab list are hidden but targetable. */
    private void processCompletionProbe() {
        List<String> current = this.pendingCompletionNames;
        if (current == null) {
            return;
        }
        this.pendingCompletionNames = null;
        Set<String> tabNames = this.env.tabNamesLower();
        String self = this.env.selfName();
        List<String> hidden = new ArrayList<>();
        for (String name : current) {
            if (this.env.isTarget(name)
                    && VanishText.isPlausiblePlayerName(name)
                    && !name.equalsIgnoreCase(self)
                    && !tabNames.contains(name.toLowerCase(Locale.ROOT))
                    && !recentMessageNames(name)) {
                hidden.add(name);
            }
        }
        if (!hidden.isEmpty() && hidden.size() <= 3) {
            for (String name : hidden) {
                reportHidden(knownUuid(name), name, false, "Vanish Event: hidden but targetable", "off TAB, still targetable", 90);
            }
        }
    }

    // ---------------------------------------------------------------- hidden spells (what the user is told)

    private void reportHidden(UUID uuid, String name, boolean spectator, String reason, String detail, int score) {
        upsertDetection(detectionKey(uuid, name), name, reason, score, this.env.now() + DETECTION_TTL_MS);
        HiddenSpell spell = this.hiddenSpells.get(spellKey(name));
        if (spell == null || spell.spectator != spectator) {
            this.hiddenSpells.put(spellKey(name),
                    new HiddenSpell(uuid, name, spectator, detail, regionProof(uuid), this.tickCounter, this.env.now()));
        }
    }

    /** Runs every 10 ticks: a spell is told one tick-batch after it began, as a "watching you" alert if the player is in your region. */
    private void reviewSpells() {
        Iterator<HiddenSpell> it = this.hiddenSpells.values().iterator();
        while (it.hasNext()) {
            HiddenSpell spell = it.next();
            if (spell.spectator && !this.env.inTab(spell.uuid)) {
                it.remove();
            } else if (!spell.watched && spell.sinceTick < this.tickCounter) {
                RegionProof proof = regionProof(spell.uuid);
                if (proof == null && !spell.told) {
                    proof = spell.proof;
                }
                if (proof != null) {
                    spell.told = true;
                    spell.watched = true;
                    watchingYou(spell.name, proof);
                } else if (!spell.told) {
                    spell.told = true;
                    this.sink.news(spell.name, spell.how(), spell.detail);
                }
            }
        }
    }

    private boolean endSpell(String name, boolean spectator, long settleMs) {
        HiddenSpell spell = this.hiddenSpells.get(spellKey(name));
        if (spell != null && spell.spectator == spectator && this.env.now() - spell.sinceMs >= settleMs) {
            this.hiddenSpells.remove(spellKey(name));
            this.detections.remove(detectionKey(spell.uuid, spell.name));
            if (spell.told) {
                this.sink.news(spell.name, "is visible again", "");
            }
            return true;
        }
        return false;
    }

    private void announceGamemode(UUID uuid, String gameMode) {
        if (uuid == null || isSelf(uuid)) {
            return;
        }
        KnownPlayer known = this.knownPlayers.get(uuid);
        if (known == null || !this.env.isTarget(known.name())) {
            return;
        }
        // a game mode update about this player means it is in the same place as you
        touchSighting(uuid, false);
        if (SPECTATOR.equals(gameMode)) {
            if (credibleSubject(uuid, known.name())) {
                reportHidden(uuid, known.name(), true, "Vanish Event: switched to spectator", "", 100);
                return;
            }
        } else if (endSpell(known.name(), true, 0L)) {
            return;
        }
        this.sink.news(known.name(), switchedTo(gameMode), "");
    }

    /** "switched to spectator"; unknown mode -> "switched game mode". */
    public static String switchedTo(String mode) {
        return mode == null ? "switched game mode" : "switched to " + mode;
    }

    private static String spellKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String detectionKey(UUID uuid, String name) {
        return uuid != null ? uuid.toString() : name;
    }

    // ---------------------------------------------------------------- region (who is provably around you)

    private RegionProof regionProof(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Body body = this.env.playerByUuid(uuid);
        if (body != null) {
            return new RegionProof(uuid, (int) Math.round(body.distance));
        }
        Sighting seen = this.sightings.get(uuid);
        return seen != null && seen.marked(this.env.now()) ? new RegionProof(uuid, -1) : null;
    }

    private void touchSighting(UUID uuid, boolean body) {
        Sighting seen = this.sightings.computeIfAbsent(uuid, k -> new Sighting());
        seen.provenAt = this.env.now();
        if (body) {
            seen.nearby = true;
            seen.bodyTick = this.tickCounter;
        }
    }

    // ---------------------------------------------------------------- sensors

    /** Every 5 ticks: refresh the region memory and look for invisible player entities. */
    private void scanPlayers() {
        double rangeSq = SENSOR_RANGE * SENSOR_RANGE;
        for (Body player : this.env.players()) {
            if (player.id == null) {
                continue;
            }
            if (this.env.isTarget(player.name)) {
                touchSighting(player.id, true);
            }
            if (player.invisible && player.distance * player.distance <= rangeSq && realPlayer(player)) {
                triggerSensor(SignalType.INVISIBLE, player, "Invisible Entity: metadata flag", 35, 5000L);
            }
        }
        for (Sighting seen : this.sightings.values()) {
            if (seen.nearby && seen.bodyTick != this.tickCounter) {
                seen.nearby = false;
            }
        }
    }

    private void inspectInvisibleEntity(int entityId) {
        Body player = this.env.playerByEntityId(entityId);
        if (player != null && player.invisible && !(player.distance * player.distance > sensorRangeSq()) && realPlayer(player)) {
            triggerSensor(SignalType.INVISIBLE, player, "Invisible Entity: metadata flag", 35, 5000L);
        }
    }

    private boolean realPlayer(Body player) {
        return credibleSubject(player.id, player.name);
    }

    private void inspectPositionalSound(Observation o) {
        if (!VanishHeuristics.suspiciousSound(o.detail())) {
            return;
        }
        long now = this.env.now();
        if (nearPlayer(o.x(), o.y(), o.z())
                && !hasVisibleCause(o.x(), o.y(), o.z())
                && !isExplosionRelated(o.x(), o.y(), o.z())
                && !isPoweredMechanism(o.x(), o.y(), o.z(), o.detail())
                && now - this.lastLocalActionMs >= 1000L
                && !recentlySelfPlaced(o.x(), o.y(), o.z())
                && !villagerToggledDoor(o.detail(), o.x(), o.y(), o.z())
                && (!selfContainerActive() || !VanishHeuristics.isContainerSignal(o.detail()))) {
            triggerSensor(SignalType.SOUND, locatedSubject(o.x(), o.y(), o.z()), "Suspicious Sound: " + shortId(o.detail()), 14, 3000L);
        }
    }

    private void inspectEntitySound(Observation o) {
        if (!VanishHeuristics.suspiciousSound(o.detail())) {
            return;
        }
        Body player = this.env.playerByEntityId(o.entityId());
        if (player != null
                && player.invisible
                && player.distance * player.distance <= sensorRangeSq()
                && realPlayer(player)) {
            triggerSensor(SignalType.SOUND, player, "Suspicious Sound: invisible source", 16, 3000L);
        }
    }

    private void inspectParticle(Observation o) {
        if (!VanishHeuristics.suspiciousParticle(o.detail())) {
            return;
        }
        long now = this.env.now();
        if (nearPlayer(o.x(), o.y(), o.z())
                && !hasVisibleCause(o.x(), o.y(), o.z())
                && !isExplosionRelated(o.x(), o.y(), o.z())
                && now - this.lastLocalActionMs >= 900L
                && !hasAmbientParticleSource(o.x(), o.y(), o.z(), o.detail())) {
            String particle = shortId(o.detail());
            boolean weak = VanishHeuristics.weakParticle(particle);
            if (!weak || !nearSelf(o.x(), o.y(), o.z(), 6.25)) {
                if (!weak || particleBurstReady(particle, now)) {
                    triggerSensor(SignalType.PARTICLE, locatedSubject(o.x(), o.y(), o.z()), "Ghost Particle: " + shortId(o.detail()), 16, 3000L);
                }
            }
        }
    }

    private boolean villagerToggledDoor(String blockId, double x, double y, double z) {
        return VanishHeuristics.doorLike(blockId) && this.env.villagerNear(x, y, z, 9.0);
    }

    private boolean recentlySelfPlaced(double x, double y, double z) {
        Long until = this.selfPlacedBlocks.get(VanishHeuristics.pack(floor(x), floor(y), floor(z)));
        return until != null && until > this.env.now();
    }

    private void rememberExplosion(Observation o) {
        long now = this.env.now();
        this.recentExplosions.addLast(new Explosion(o.x(), o.y(), o.z(), Math.max(2.0, parseDouble(o.detail(), 4.0) + 4.0), now));
        while (this.recentExplosions.size() > 8) {
            this.recentExplosions.removeFirst();
        }
    }

    /** Smoke and block particles are common: only the second one within 2 s counts. */
    private boolean particleBurstReady(String particle, long now) {
        Deque<Long> burst = this.weakParticleBursts.computeIfAbsent(particle, ignored -> new ArrayDeque<>());
        burst.addLast(now);
        while (!burst.isEmpty() && now - burst.peekFirst() > 2000L) {
            burst.removeFirst();
        }
        if (burst.size() < 2) {
            return false;
        }
        burst.clear();
        return true;
    }

    private void triggerSensor(SignalType type, String where, String reason, int weight, long cooldownMs) {
        addSignal(type, where, null, reason, weight, cooldownMs);
    }

    private void triggerSensor(SignalType type, Body player, String reason, int weight, long cooldownMs) {
        addSignal(type, player.name, regionProof(player.id), reason, weight, cooldownMs);
    }

    private void addSignal(SignalType type, String subject, RegionProof proof, String reason, int weight, long cooldownMs) {
        long now = this.env.now();
        String cooldownKey = type.name() + "|" + subject.toLowerCase(Locale.ROOT);
        long last = this.signalCooldowns.getOrDefault(cooldownKey, 0L);
        if (now - last >= cooldownMs) {
            this.signalCooldowns.put(cooldownKey, now);
            this.signals.addLast(new Signal(type, subject, proof, reason, weight, now));
            upsertDetection("signal:" + cooldownKey, subject, reason, weight, now + DETECTION_TTL_MS);
            announceTrigger(cooldownKey, subject, proof != null, reason);
            evaluateCritical();
        }
    }

    /** Two different kinds of signal in the window, one naming a provably nearby player, and 35 points: that player watches you. */
    private void evaluateCritical() {
        long now = this.env.now();
        pruneSignals(now);
        EnumMap<SignalType, Integer> strongest = new EnumMap<>(SignalType.class);
        Signal watcher = null;
        for (Signal signal : this.signals) {
            strongest.merge(signal.type(), signal.weight(), Math::max);
            if (signal.proof() != null) {
                watcher = signal;
            }
        }
        int score = strongest.values().stream().mapToInt(Integer::intValue).sum();
        if (watcher != null && strongest.size() >= 2 && score >= CRITICAL_SCORE) {
            if (now - this.lastCriticalMs >= CRITICAL_COOLDOWN_MS) {
                watchingYou(watcher.subject(), watcher.proof());
            }
        }
    }

    private void watchingYou(String name, RegionProof proof) {
        long now = this.env.now();
        this.lastCriticalMs = now;
        this.criticalUntilMs = now + CRITICAL_SHOWN_MS;
        this.criticalWatcher = name;
        this.pendingChimes.add(proof.id());
        this.ghostId = proof.id();
        this.ghostName = name;
    }

    /** The alert is raised one tick later than the decision, like in the original. */
    private void playPendingChime() {
        if (!this.pendingChimes.isEmpty()) {
            this.pendingChimes.clear();
            this.sink.watching(this.ghostId, this.ghostName);
        }
    }

    private void announceTrigger(String eventKey, String subject, boolean named, String reason) {
        long now = this.env.now();
        Long last = this.announceCooldowns.get(eventKey);
        if (last == null || now - last >= ANNOUNCE_COOLDOWN_MS) {
            while (!this.announceTimes.isEmpty() && now - this.announceTimes.peekFirst() > ANNOUNCE_WINDOW_MS) {
                this.announceTimes.removeFirst();
            }
            if (this.announceTimes.size() < MAX_ANNOUNCE_PER_WINDOW) {
                this.announceCooldowns.put(eventKey, now);
                this.announceTimes.addLast(now);
                boolean located = subject != null && !subject.isBlank() && !"Unknown".equalsIgnoreCase(subject) && !"CRITICAL".equalsIgnoreCase(subject);
                this.sink.announce(reason, located ? subject : null, named);
            }
        }
    }

    private void upsertDetection(String key, String name, String reason, int score, long expiresAt) {
        Detection existing = this.detections.get(key);
        if (existing == null) {
            this.detections.put(key, new Detection(name, reason, score, expiresAt));
        } else {
            existing.name = name;
            existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
            if (score >= existing.score) {
                existing.reason = reason;
                existing.score = score;
            }
        }
    }

    // ---------------------------------------------------------------- summary

    private static boolean detectionWorthShowing(Detection detection) {
        if (detection == null) {
            return false;
        }
        String name = detection.name == null ? "" : detection.name.trim();
        return !name.isBlank() && !"Unknown".equalsIgnoreCase(name) && !"You".equalsIgnoreCase(name) && !"CRITICAL".equalsIgnoreCase(name);
    }

    static String shortName(String name) {
        String shown = name == null ? "" : name.trim();
        return shown.length() <= 16 ? shown : shown.substring(0, 16);
    }

    public boolean hasHudContent() {
        long now = this.env.now();
        if (now < this.criticalUntilMs) {
            return true;
        }
        for (Detection detection : this.detections.values()) {
            if (detection.expiresAt > now && detectionWorthShowing(detection)) {
                return true;
            }
        }
        return false;
    }

    /** Alert first, then up to four strongest detections, one per tag and name. */
    public List<HudEntry> hudEntries() {
        long now = this.env.now();
        List<HudEntry> out = new ArrayList<>();
        if (now < this.criticalUntilMs) {
            out.add(new HudEntry("CRITICAL", this.criticalWatcher));
        }
        Set<String> seenRows = new HashSet<>();
        this.detections.values().stream()
                .filter(detection -> detection.expiresAt > now)
                .filter(VanishEngine::detectionWorthShowing)
                .sorted(Comparator.<Detection>comparingInt(detection -> detection.score).reversed()
                        .thenComparing(detection -> detection.name, String.CASE_INSENSITIVE_ORDER))
                .map(detection -> new HudEntry(detection.name, detection.reason))
                .filter(entry -> seenRows.add(entry.tag() + "\u0000" + entry.value()))
                .limit(4L)
                .forEach(out::add);
        return List.copyOf(out);
    }

    // ---------------------------------------------------------------- upkeep

    private void pruneState() {
        long now = this.env.now();
        this.detections.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        this.signalCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 60000L);
        this.announceCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 60000L);
        this.selfPlacedBlocks.entrySet().removeIf(entry -> entry.getValue() <= now);
        this.confirmedDepartures.entrySet().removeIf(entry -> entry.getValue() <= now);
        this.automatedMechanisms.entrySet().removeIf(entry -> entry.getValue() <= now);
        this.weakParticleBursts.values().removeIf(burst -> {
            while (!burst.isEmpty() && now - burst.peekFirst() > 2000L) {
                burst.removeFirst();
            }
            return burst.isEmpty();
        });
        while (!this.recentExplosions.isEmpty() && now - this.recentExplosions.peekFirst().timeMs() > 3000L) {
            this.recentExplosions.removeFirst();
        }
        this.sightings.values().removeIf(seen -> !seen.nearby && now - seen.provenAt > REGION_MEMORY_MS);
        pruneSignals(now);
    }

    private void pruneSignals(long now) {
        while (!this.signals.isEmpty() && now - this.signals.peekFirst().timeMs() > SIGNAL_WINDOW_MS) {
            this.signals.removeFirst();
        }
    }

    // ---------------------------------------------------------------- geometry helpers

    private static double sensorRangeSq() {
        return SENSOR_RANGE * SENSOR_RANGE;
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    private double sqToSelf(double x, double y, double z) {
        double[] me = this.env.selfPos();
        double dx = x - me[0];
        double dy = y - me[1];
        double dz = z - me[2];
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean nearPlayer(double x, double y, double z) {
        return sqToSelf(x, y, z) <= sensorRangeSq();
    }

    private boolean nearSelf(double x, double y, double z, double maxDistSq) {
        return sqToSelf(x, y, z) < maxDistSq;
    }

    private boolean selfContainerActive() {
        return this.env.now() - this.lastContainerActivityMs < CONTAINER_SELF_GRACE_MS;
    }

    private String locatedSubject(double x, double y, double z) {
        double[] me = this.env.selfPos();
        return VanishHeuristics.located(x - me[0], y - me[1], z - me[2]);
    }

    /** A visible player or a projectile within 4 blocks explains the sound or particle. */
    private boolean hasVisibleCause(double x, double y, double z) {
        for (Body player : this.env.players()) {
            if (!player.invisible) {
                double dx = player.x - x;
                double dy = player.y - y;
                double dz = player.z - z;
                if (dx * dx + dy * dy + dz * dz <= 16.0) {
                    return true;
                }
            }
        }
        return this.env.projectileNear(x, y, z, 16.0);
    }

    private boolean isExplosionRelated(double x, double y, double z) {
        long now = this.env.now();
        while (!this.recentExplosions.isEmpty() && now - this.recentExplosions.peekFirst().timeMs() > 3000L) {
            this.recentExplosions.removeFirst();
        }
        for (Explosion explosion : this.recentExplosions) {
            double dx = explosion.x() - x;
            double dy = explosion.y() - y;
            double dz = explosion.z() - z;
            if (dx * dx + dy * dy + dz * dz <= explosion.radius() * explosion.radius()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAmbientParticleSource(double x, double y, double z, String particleId) {
        if (!shortId(particleId).contains("smoke")) {
            return false;
        }
        int cx = floor(x);
        int cy = floor(y);
        int cz = floor(z);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (VanishHeuristics.ambientSmokeBlock(this.env.blockPath(cx + dx, cy + dy, cz + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Doors and trapdoors next to a redstone signal (or one that was powered in the last 5 s) are machines, not players. */
    private boolean isPoweredMechanism(double x, double y, double z, String soundId) {
        String path = shortId(soundId);
        if (!path.contains("door") && !path.contains("trapdoor")) {
            return false;
        }
        int cx = floor(x);
        int cy = floor(y);
        int cz = floor(z);
        long now = this.env.now();
        for (int dy = -1; dy <= 1; dy++) {
            long key = VanishHeuristics.pack(cx, cy + dy, cz);
            if (this.env.powered(cx, cy + dy, cz)) {
                this.automatedMechanisms.put(key, now + 5000L);
                return true;
            }
            if (this.automatedMechanisms.getOrDefault(key, 0L) > now) {
                return true;
            }
        }
        return false;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String shortId(String id) {
        return VanishHeuristics.path(id);
    }

    // ---------------------------------------------------------------- state for tests and the module's info line

    public boolean serverSendsLeaveMessages() {
        return this.serverSendsLeaveMessages;
    }

    public int pendingVanishCount() {
        return this.pendingVanishes.size();
    }

    public int signalCount() {
        return this.signals.size();
    }

    public int spellCount() {
        return this.hiddenSpells.size();
    }

    public boolean criticalActive() {
        return this.env.now() < this.criticalUntilMs;
    }

    public String criticalWatcher() {
        return this.criticalWatcher;
    }

    public int tickCounter() {
        return this.tickCounter;
    }
}
