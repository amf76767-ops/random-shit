package dev.dihclient.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The decision part of the automation supervisor. It knows nothing about Minecraft: the host feeds it one
 * {@link World} and a list of {@link Sample}s per tick and carries out the {@link Action}s it returns.
 *
 * <ul>
 *   <li><b>Danger:</b> low health or a stranger nearby pauses the automation modules; they are started again by
 *       themselves once it has been safe for a while. Modules the player turned on during the danger are left alone.</li>
 *   <li><b>Stuck:</b> a watched module that shows no progress (nothing changes in its status and the player does not
 *       move) is restarted, and switched off with a warning when restarting does not help.</li>
 * </ul>
 */
public final class SupervisorEngine {

    /** Tunables, all in ticks (20 per second) or plain units. */
    public static final class Config {
        public boolean pauseOnLowHealth = true;
        /** Health points (2 per heart) under which everything is paused. */
        public float pauseBelowHealth = 8f;
        /** Health points the player must be back at before things resume. */
        public float resumeAtHealth = 16f;
        public boolean pauseForPlayers = true;
        /** Ticks it has to be safe before the paused modules start again. */
        public int resumeDelayTicks = 200;
        public boolean detectStuck = true;
        public int stuckTicks = 900;
        public int maxRestarts = 2;
        public int restartDelayTicks = 20;
        /** Blocks the player has to move to count as progress. */
        public double moveEps = 1.5;
        /** Ticks of steady progress after a restart before the restart counter is forgiven. */
        public int forgiveTicks = 1200;
    }

    /** What the host sees of the player and its surroundings. */
    public record World(double x, double y, double z, float health, int strangersNearby, String nearestStranger, boolean dead) {
    }

    /**
     * One module.
     *
     * @param progress an opaque text that changes whenever the module gets something done
     * @param benign   the module says it is idle, paused or waiting on purpose, which counts as "not stuck"
     */
    public record Sample(String id, boolean enabled, String progress, boolean benign) {
    }

    public enum Type { DISABLE, ENABLE, WARN }

    public record Action(Type type, String id, String text) {
    }

    private static final class Track {
        String sig;
        double x, y, z;
        long progressTick;
        long lastRestartTick = Long.MIN_VALUE / 2;
        long enableAt = -1;
        int attempts;
    }

    private final Config cfg;
    private final Map<String, Track> tracks = new HashMap<>();
    private final Set<String> paused = new LinkedHashSet<>();
    private boolean inDanger;
    private boolean lowLatch;
    private long safeSince = -1;

    public SupervisorEngine(Config cfg) {
        this.cfg = cfg;
    }

    public void reset() {
        tracks.clear();
        paused.clear();
        inDanger = false;
        lowLatch = false;
        safeSince = -1;
    }

    public boolean inDanger() {
        return inDanger;
    }

    /** Ids of the modules this engine switched off because of danger and still wants to switch back on. */
    public Set<String> pausedIds() {
        return paused;
    }

    /**
     * @param watched modules that are checked for danger and for being stuck
     * @param guarded modules that are only paused in danger (waiting is normal for them, for example fishing)
     */
    public List<Action> step(long tick, World w, List<Sample> watched, List<Sample> guarded) {
        List<Action> out = new ArrayList<>();
        if (w.dead()) {
            reset();
            return out;
        }

        String reason = dangerReason(w);
        boolean danger = reason != null;
        if (danger) {
            safeSince = -1;
        } else if (safeSince < 0) {
            safeSince = tick;
        }

        if (danger && !inDanger) {
            inDanger = true;
            int n = 0;
            for (List<Sample> list : List.of(watched, guarded)) {
                for (Sample s : list) {
                    if (s.enabled() && paused.add(s.id())) {
                        tracks.remove(s.id());
                        out.add(new Action(Type.DISABLE, s.id(), reason));
                        n++;
                    }
                }
            }
            if (n > 0) {
                out.add(new Action(Type.WARN, null, "Paused " + n + (n == 1 ? " module" : " modules") + ": " + reason));
            }
        } else if (!danger && inDanger && tick - safeSince >= cfg.resumeDelayTicks) {
            inDanger = false;
            int n = 0;
            for (String id : paused) {
                Sample s = find(id, watched, guarded);
                if (s != null && !s.enabled()) {
                    out.add(new Action(Type.ENABLE, id, "safe again"));
                    n++;
                }
            }
            paused.clear();
            if (n > 0) {
                out.add(new Action(Type.WARN, null, "Safe again, resumed " + n + (n == 1 ? " module" : " modules")));
            }
        }
        if (inDanger || !cfg.detectStuck) {
            return out;
        }

        for (Sample s : watched) {
            watch(tick, w, s, out);
        }
        return out;
    }

    private String dangerReason(World w) {
        if (cfg.pauseOnLowHealth) {
            if (w.health() < cfg.pauseBelowHealth) {
                lowLatch = true;
            } else if (w.health() >= cfg.resumeAtHealth) {
                lowLatch = false;
            }
        } else {
            lowLatch = false;
        }
        StringBuilder b = new StringBuilder();
        if (lowLatch) {
            b.append("health ").append(String.format("%.1f", w.health() / 2f)).append(" hearts");
        }
        if (cfg.pauseForPlayers && w.strangersNearby() > 0) {
            if (b.length() > 0) {
                b.append(", ");
            }
            b.append(w.nearestStranger() == null ? "player nearby" : w.nearestStranger() + " nearby");
        }
        return b.length() == 0 ? null : b.toString();
    }

    private void watch(long tick, World w, Sample s, List<Action> out) {
        Track t = tracks.get(s.id());
        if (!s.enabled()) {
            if (t != null && t.enableAt >= 0) {
                if (tick >= t.enableAt) {
                    out.add(new Action(Type.ENABLE, s.id(), "restart"));
                    t.enableAt = -1;
                    t.progressTick = tick;
                    t.sig = null;
                }
            } else {
                tracks.remove(s.id()); // switched off by the player or by the module itself
            }
            return;
        }
        if (t == null) {
            t = new Track();
            mark(t, tick, w, s);
            tracks.put(s.id(), t);
            return;
        }
        t.enableAt = -1;
        double dx = w.x() - t.x, dy = w.y() - t.y, dz = w.z() - t.z;
        boolean moved = dx * dx + dy * dy + dz * dz >= cfg.moveEps * cfg.moveEps;
        if (s.benign() || moved || !Objects.equals(t.sig, s.progress())) {
            if (tick - t.lastRestartTick > cfg.forgiveTicks) {
                t.attempts = 0;
            }
            mark(t, tick, w, s);
            return;
        }
        if (tick - t.progressTick < cfg.stuckTicks) {
            return;
        }
        int seconds = cfg.stuckTicks / 20;
        t.attempts++;
        if (t.attempts > cfg.maxRestarts) {
            out.add(new Action(Type.DISABLE, s.id(), "no progress"));
            out.add(new Action(Type.WARN, null, s.id() + " made no progress for " + seconds + "s even after "
                    + cfg.maxRestarts + (cfg.maxRestarts == 1 ? " restart" : " restarts") + " and was switched off"));
            tracks.remove(s.id());
        } else {
            out.add(new Action(Type.DISABLE, s.id(), "restart"));
            out.add(new Action(Type.WARN, null, s.id() + " made no progress for " + seconds + "s, restarting ("
                    + t.attempts + "/" + cfg.maxRestarts + ")"));
            t.enableAt = tick + cfg.restartDelayTicks;
            t.lastRestartTick = tick;
            t.progressTick = tick;
        }
    }

    private static void mark(Track t, long tick, World w, Sample s) {
        t.sig = s.progress();
        t.x = w.x();
        t.y = w.y();
        t.z = w.z();
        t.progressTick = tick;
    }

    private static Sample find(String id, List<Sample> a, List<Sample> b) {
        for (Sample s : a) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        for (Sample s : b) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }
}
