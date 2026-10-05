package dev.dihclient.port.discord;

import dev.dihclient.DIHClient;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/** Ported from an open-source client (GPL-3.0). */
final class DiscordLink implements Runnable {
    private static final long RETRY_MIN_MS = 5000L;
    private static final long RETRY_MAX_MS = 30000L;
    private static final long IDLE_POLL_MS = 1000L;
    private static final long MIN_UPDATE_GAP_MS = 4000L;
    private static final long KEEP_ALIVE_MS = 60000L;

    private final Thread thread = new Thread(this, "DIHClient Discord Presence");
    private final BooleanSupplier wanted;
    private volatile boolean running = true;
    private volatile boolean finished;
    private volatile DiscordCard card;
    private Thread previous;
    private DiscordIpc ipc;
    private String connectedId;
    private DiscordCard sent;
    private long sentAt;
    private long retryMs = RETRY_MIN_MS;
    private String lastProblem;

    DiscordLink(DiscordLink previous, BooleanSupplier wanted) {
        this.previous = previous != null ? previous.thread : null;
        this.wanted = wanted;
        this.thread.setDaemon(true);
    }

    void start() {
        this.thread.start();
    }

    void publish(DiscordCard next) {
        this.card = next;
        LockSupport.unpark(this.thread);
    }

    void stop() {
        this.running = false;
        LockSupport.unpark(this.thread);
    }

    boolean alive() {
        return this.running && !this.finished;
    }

    DiscordCard current() {
        return this.card;
    }

    void join(long millis) {
        try {
            this.thread.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        try {
            this.awaitPrevious();
            while (this.running && this.wanted.getAsBoolean()) {
                this.step();
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Discord Presence stopped: {}", t.toString());
        } finally {
            try {
                if (this.ipc != null) {
                    this.clear();
                }
            } catch (Throwable ignored) {

            }
            this.finished = true;
        }
    }

    private void step() {
        DiscordCard want = this.card;
        if (want == null) {

            if (this.ipc != null) {
                this.clear();
            }
            this.pause(IDLE_POLL_MS);
            return;
        }
        if (this.ipc != null && !want.appId().equals(this.connectedId)) {
            this.clear();
        }
        if (this.ipc == null && !this.connect(want.appId())) {
            this.pause(this.retryMs);
            this.retryMs = Math.min(this.retryMs * 2L, RETRY_MAX_MS);
            return;
        }
        try {
            this.ipc.poll();
            long since = System.currentTimeMillis() - this.sentAt;
            if (this.sent == null || since >= (want.equals(this.sent) ? KEEP_ALIVE_MS : MIN_UPDATE_GAP_MS)) {
                this.send(want);
            }
        } catch (IOException e) {
            this.report("lost Discord", e);
            this.hangUp();
            this.pause(this.retryMs);
            this.retryMs = Math.min(this.retryMs * 2L, RETRY_MAX_MS);
            return;
        }
        this.pause(IDLE_POLL_MS);
    }

    private void awaitPrevious() {
        if (this.previous != null) {
            try {
                this.previous.join(3000L);
            } catch (InterruptedException e) {
                this.running = false;
            }
            this.previous = null;
        }
    }

    private boolean connect(String appId) {
        try {
            this.ipc = DiscordIpc.connect(appId);
        } catch (IOException | RuntimeException e) {
            this.report("could not connect", e);
            return false;
        }
        if (this.ipc == null) {

            return false;
        }
        DIHClient.LOG.info("[DIHClient] Discord Presence: connected to Discord");
        this.connectedId = appId;
        this.retryMs = RETRY_MIN_MS;
        this.lastProblem = null;
        this.sent = null;
        return true;
    }

    private void send(DiscordCard next) throws IOException {
        try {
            this.ipc.setActivity(next.activity());
        } catch (DiscordIpc.RefusedException e) {
            this.report("Discord refused the status", e);
        }
        this.sent = next;
        this.sentAt = System.currentTimeMillis();
    }

    private void clear() {
        try {
            if (this.sent != null) {
                this.ipc.setActivity(null);
            }
        } catch (DiscordIpc.RefusedException | IOException ignored) {

        }
        this.hangUp();
    }

    private void hangUp() {
        if (this.ipc != null) {
            this.ipc.close();
        }
        this.ipc = null;
        this.connectedId = null;
        this.sent = null;
    }

    private void pause(long millis) {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(millis));
    }

    private void report(String what, Exception problem) {
        String line = what + " (" + problem + ")";
        if (!line.equals(this.lastProblem)) {
            this.lastProblem = line;
            DIHClient.LOG.warn("[DIHClient] Discord Presence: {}", line);
        }
    }
}
