package dev.dihclient.modules.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.ActionSetting;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Notifications;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.class_642;

public class DiscordAlarm extends Module {
    public enum Via { WEBHOOK, BOT }

    public static volatile Predicate<String> botSink;

    public final EnumSetting<Via> via = this.mode("Send Via", "WEBHOOK: a channel webhook. BOT: your own bot from the Discord Bot module sends you a DM.", Via.WEBHOOK);
    public final StringSetting webhook = this.text("Webhook URL", "Discord → Channel settings → Integrations → Webhooks → Copy URL.", "", 256)
            .visibleWhen(() -> this.via.get() == Via.WEBHOOK);
    public final BoolSetting warnings = this.bool("Warnings", "Forward warning toasts from all modules (Player Bypass, finders, notifier …).", true);
    public final BoolSetting errors = this.bool("Errors", "Forward error toasts.", true);
    public final BoolSetting alerts = this.bool("Big Alerts", "Forward the big centre alerts (usually duplicates a warning).", false);
    public final StringSetting filter = this.text("Only Modules", "Comma separated module/toast titles to forward. Empty = all.", "", 256);
    public final BoolSetting death = this.bool("On Death", "Message when you die, with coordinates.", true);
    public final BoolSetting disconnect = this.bool("On Disconnect", "Message when you get disconnected or kicked.", true);
    public final BoolSetting onlyUnfocused = this.bool("Only When Tabbed Out", "Only send while the Minecraft window is not focused.", false);
    public final BoolSetting coords = this.bool("Add Position", "Adds your current position and dimension to every message.", false);
    public final StringSetting mention = this.text("Mention", "Put in front of messages, e.g. @everyone or <@your-user-id>. Empty = none.", "", 64);
    public final IntSetting minGap = this.integer("Min Gap", "Seconds between messages; everything in between is bundled.", 3, 1, 60);
    public final ActionSetting test = this.action("Send Test", "Sends a test message.", this::sendTest);
    private static final ExecutorService HTTP = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DIHClient-Discord");
        t.setDaemon(true);
        return t;
    });
    private final Deque<String> queue = new ArrayDeque<>();
    private long lastSend;
    private boolean wasDead;
    private int failures;

    public DiscordAlarm() {
        super("Discord Alarm", Category.CLIENT,
                "Sends alarms from all modules to Discord (webhook or your own bot) – get Player Bypass, Watchlist and finder alerts on your phone.");
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (this.isEnabled() && this.disconnect.get()) {
                this.enqueue("🔌 Disconnected from " + this.serverName());
                this.flush(true);
            }
        });
    }

    public static void onToast(String title, String text, Notifications.Type type) {
        DiscordAlarm a = instance();
        if (a != null
                && (type != Notifications.Type.WARNING || a.warnings.get())
                && (type != Notifications.Type.ERROR || a.errors.get())
                && (type == Notifications.Type.WARNING || type == Notifications.Type.ERROR)
                && a.passesFilter(title)) {
            a.enqueue((type == Notifications.Type.ERROR ? "❗ " : "⚠️ ") + "**" + title + "** – " + text);
        }
    }

    public static void onAlert(String text) {
        DiscordAlarm a = instance();
        if (a != null && a.alerts.get()) {
            a.enqueue("🚨 **" + text + "**");
        }
    }

    private static DiscordAlarm instance() {
        return DIHClient.modules() != null && ModuleManager.on(DiscordAlarm.class) ? ModuleManager.of(DiscordAlarm.class) : null;
    }

    private boolean passesFilter(String title) {
        String f = this.filter.get().trim();
        if (f.isEmpty()) {
            return true;
        }
        String t = title.toLowerCase(Locale.ROOT);
        for (String part : f.split("\\s*,\\s*")) {
            if (!part.isBlank() && t.contains(part.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String serverName() {
        class_642 s = mc.method_1558();
        return s != null ? s.field_3761 : "singleplayer";
    }

    private String position() {
        if (mc.field_1724 != null && mc.field_1687 != null) {
            String dim = mc.field_1687.method_27983().method_29177().method_12832();
            return " · " + (int) mc.field_1724.method_23317() + " " + (int) mc.field_1724.method_23318() + " " + (int) mc.field_1724.method_23321() + " (" + dim + ")";
        }
        return "";
    }

    private boolean configured() {
        return this.via.get() == Via.BOT || !this.webhook.get().isBlank();
    }

    private synchronized void enqueue(String text) {
        if (this.configured() && (!this.onlyUnfocused.get() || !mc.method_1569())) {
            if (this.coords.get()) {
                text = text + this.position();
            }
            if (text.length() > 1700) {
                text = text.substring(0, 1700) + "…";
            }
            this.queue.addLast(text);
            while (this.queue.size() > 20) {
                this.queue.removeFirst();
            }
        }
    }

    @Override
    public void onTick() {
        if (this.death.get()) {
            boolean dead = mc.field_1724.method_29504() || mc.field_1724.method_6032() <= 0.0F;
            if (dead && !this.wasDead) {
                this.enqueue("💀 You died at " + (int) mc.field_1724.method_23317() + " " + (int) mc.field_1724.method_23318() + " " + (int) mc.field_1724.method_23321()
                        + " (" + mc.field_1687.method_27983().method_29177().method_12832() + ") on " + this.serverName());
            }
            this.wasDead = dead;
        }
        this.flush(false);
    }

    private synchronized void flush(boolean now) {
        if (this.queue.isEmpty()) {
            return;
        }
        long time = System.currentTimeMillis();
        if (now || time - this.lastSend >= this.minGap.get().intValue() * 1000L) {
            this.lastSend = time;
            List<String> batch = new ArrayList<>();
            int length = 0;
            while (!this.queue.isEmpty() && length + this.queue.peekFirst().length() < 1800) {
                String s = this.queue.removeFirst();
                length += s.length() + 1;
                batch.add(s);
            }
            String prefix = this.mention.get().isBlank() ? "" : this.mention.get().trim() + " ";
            this.post(prefix + String.join("\n", batch));
        }
    }

    private void sendTest() {
        if (!this.configured()) {
            Notifications.info(this.name(), "Enter a webhook URL first.");
        } else if (this.post("✅ DIHClient test message" + this.position())) {
            Notifications.info(this.name(), "Test message sent.");
        }
    }

    private boolean post(String text) {
        if (this.via.get() == Via.BOT) {
            Predicate<String> sink = botSink;
            if (sink == null || !sink.test(text)) {
                this.failures++;
                if (this.failures == 1 || this.failures % 10 == 0) {
                    Notifications.info(this.name(), "Turn on the Discord Bot module and put your bot token in, or switch Send Via to WEBHOOK.");
                }
                return false;
            }
            this.failures = 0;
            return true;
        }
        String url = this.webhook.get().trim();
        if (!url.startsWith("https://discord.com/api/webhooks/")
                && !url.startsWith("https://discordapp.com/api/webhooks/")
                && !url.startsWith("https://ptb.discord.com/api/webhooks/")
                && !url.startsWith("https://canary.discord.com/api/webhooks/")) {
            Notifications.info(this.name(), "Webhook URL looks wrong (must start with https://discord.com/api/webhooks/).");
            return false;
        }
        JsonObject body = new JsonObject();
        body.addProperty("username", "DIHClient");
        body.addProperty("content", text);
        JsonObject mentions = new JsonObject();
        JsonArray parse = new JsonArray();
        parse.add("everyone");
        parse.add("users");
        parse.add("roles");
        mentions.add("parse", parse);
        body.add("allowed_mentions", mentions);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        HTTP.execute(() -> {
            String error = null;
            try {
                HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("User-Agent", "DIHClient");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(bytes);
                }
                int code = c.getResponseCode();
                if (code >= 300) {
                    error = "HTTP " + code;
                }
                c.disconnect();
            } catch (Exception e) {
                error = e.getClass().getSimpleName();
            }
            if (error != null) {
                this.failures++;
                DIHClient.LOG.warn("[DIHClient] Discord webhook failed: {}", error);
                String shown = error;
                if (this.failures == 1 || this.failures % 10 == 0) {
                    mc.execute(() -> Notifications.info("Discord Alarm", "Webhook failed (" + shown + ")"));
                }
            } else {
                this.failures = 0;
            }
        });
        return true;
    }

    @Override
    public List<String> details() {
        if (this.via.get() == Via.BOT) {
            return List.of("Sending through the Discord Bot", "Queued: " + this.queue.size());
        }
        return List.of(this.webhook.get().isBlank() ? "No webhook set" : "Webhook set", "Queued: " + this.queue.size());
    }
}
