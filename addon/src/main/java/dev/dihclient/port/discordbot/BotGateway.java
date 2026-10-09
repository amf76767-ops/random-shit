package dev.dihclient.port.discordbot;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

final class BotGateway {
    interface Handler {
        void dispatch(String type, JsonObject data);

        void state(String text, boolean fatal);
    }

    private static final URI GATEWAY = URI.create("wss://gateway.discord.gg/?v=10&encoding=json");
    private static final int INTENTS = 1 << 12;

    private final HttpClient http;
    private final String token;
    private final Handler handler;
    private final ScheduledExecutorService timer;
    private volatile boolean stopped;
    private volatile Session current;
    private volatile JsonObject presence;
    private boolean connecting;
    private int failures;

    BotGateway(HttpClient http, String token, Handler handler, ScheduledExecutorService timer) {
        this.http = http;
        this.token = token;
        this.handler = handler;
        this.timer = timer;
    }

    void start() {
        this.connect();
    }

    boolean online() {
        Session s = this.current;
        return s != null && s.ready;
    }

    void presence(JsonObject p) {
        this.presence = p;
        Session s = this.current;
        if (s != null && s.ready) {
            JsonObject msg = new JsonObject();
            msg.addProperty("op", 3);
            msg.add("d", p);
            s.send(msg.toString());
        }
    }

    void stop(long waitMillis) {
        this.stopped = true;
        Session s = this.current;
        this.current = null;
        if (s != null) {
            s.cancel();
            WebSocket ws = s.ws;
            if (ws != null) {
                try {
                    CompletableFuture<WebSocket> f = s.send(null);
                    if (waitMillis > 0) {
                        f.get(waitMillis, TimeUnit.MILLISECONDS);
                    }
                } catch (Exception ignored) {

                }
                ws.abort();
            }
        }
    }

    private synchronized void connect() {
        if (this.stopped || this.connecting) {
            return;
        }
        this.connecting = true;
        Session session = new Session();
        this.current = session;
        this.http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15)).buildAsync(GATEWAY, session).whenComplete((ws, err) -> {
            synchronized (this) {
                this.connecting = false;
            }
            if (err != null) {
                session.closed(-1, "connect failed (" + simple(err) + ")");
            } else if (this.stopped) {
                ws.abort();
            }
        });
    }

    private synchronized void retry() {
        if (this.stopped) {
            return;
        }
        this.failures++;
        long delay = Math.min(60L, 1L << Math.min(this.failures, 6));
        this.handler.state("Offline, trying again in " + delay + "s", false);
        this.timer.schedule(this::connect, delay, TimeUnit.SECONDS);
    }

    private static String simple(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }

    private static String closeText(int code) {
        return switch (code) {
            case 4004 -> "Token is wrong (Discord says: authentication failed)";
            case 4010, 4011, 4012 -> "Discord refused the connection (code " + code + ")";
            case 4013, 4014 -> "Discord refused the intents (code " + code + ")";
            default -> null;
        };
    }

    private final class Session implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();
        private volatile WebSocket ws;
        private volatile boolean ready;
        private volatile boolean acked = true;
        private volatile JsonElement seq = JsonNull.INSTANCE;
        private ScheduledFuture<?> beat;
        private CompletableFuture<WebSocket> sending = CompletableFuture.completedFuture(null);
        private boolean done;

        @Override
        public void onOpen(WebSocket webSocket) {
            this.ws = webSocket;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            this.buffer.append(data);
            if (last) {
                String text = this.buffer.toString();
                this.buffer.setLength(0);
                try {
                    this.handle(JsonParser.parseString(text).getAsJsonObject());
                } catch (RuntimeException e) {
                    BotGateway.this.handler.state("Bad gateway message (" + simple(e) + ")", false);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            this.closed(statusCode, reason == null || reason.isBlank() ? "closed" : reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            this.closed(-1, simple(error));
        }

        synchronized CompletableFuture<WebSocket> send(String text) {
            WebSocket w = this.ws;
            if (w == null) {
                return CompletableFuture.completedFuture(null);
            }
            this.sending = this.sending.handle((a, b) -> null).thenCompose(x -> text == null ? w.sendClose(WebSocket.NORMAL_CLOSURE, "bye") : w.sendText(text, true));
            return this.sending;
        }

        synchronized void cancel() {
            this.done = true;
            this.ready = false;
            if (this.beat != null) {
                this.beat.cancel(false);
                this.beat = null;
            }
        }

        void closed(int code, String reason) {
            synchronized (this) {
                if (this.done) {
                    return;
                }
            }
            this.cancel();
            WebSocket w = this.ws;
            if (w != null) {
                w.abort();
            }
            if (BotGateway.this.current != this || BotGateway.this.stopped) {
                return;
            }
            String fatal = closeText(code);
            if (fatal != null) {
                BotGateway.this.stopped = true;
                BotGateway.this.handler.state(fatal, true);
                return;
            }
            BotGateway.this.handler.state("Disconnected (" + (code > 0 ? code + " " : "") + reason + ")", false);
            BotGateway.this.retry();
        }

        private void handle(JsonObject msg) {
            int op = msg.get("op").getAsInt();
            if (msg.has("s") && !msg.get("s").isJsonNull()) {
                this.seq = msg.get("s");
            }
            switch (op) {
                case 10 -> this.hello(msg.getAsJsonObject("d").get("heartbeat_interval").getAsLong());
                case 11 -> this.acked = true;
                case 1 -> this.heartbeat(false);
                case 7 -> this.closed(-1, "Discord asked for a reconnect");
                case 9 -> this.closed(-1, "session expired");
                case 0 -> {
                    String type = msg.get("t").getAsString();
                    JsonObject d = msg.get("d").isJsonObject() ? msg.getAsJsonObject("d") : new JsonObject();
                    if (type.equals("READY")) {
                        this.ready = true;
                        synchronized (BotGateway.this) {
                            BotGateway.this.failures = 0;
                        }
                    }
                    BotGateway.this.handler.dispatch(type, d);
                }
                default -> {
                }
            }
        }

        private void hello(long interval) {
            synchronized (this) {
                if (this.done) {
                    return;
                }
                long first = (long) (interval * ThreadLocalRandom.current().nextDouble());
                this.beat = BotGateway.this.timer.scheduleAtFixedRate(() -> this.heartbeat(true), first, interval, TimeUnit.MILLISECONDS);
            }
            JsonObject props = new JsonObject();
            props.addProperty("os", System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT));
            props.addProperty("browser", "DIHClient");
            props.addProperty("device", "DIHClient");
            JsonObject d = new JsonObject();
            d.addProperty("token", BotGateway.this.token);
            d.addProperty("intents", INTENTS);
            d.add("properties", props);
            JsonObject p = BotGateway.this.presence;
            if (p != null) {
                d.add("presence", p);
            }
            JsonObject identify = new JsonObject();
            identify.addProperty("op", 2);
            identify.add("d", d);
            this.send(identify.toString());
        }

        private void heartbeat(boolean scheduled) {
            if (scheduled && !this.acked) {
                this.closed(-1, "no heartbeat answer");
                return;
            }
            if (scheduled) {
                this.acked = false;
            }
            JsonObject msg = new JsonObject();
            msg.addProperty("op", 1);
            msg.add("d", this.seq);
            this.send(msg.toString());
        }
    }
}
