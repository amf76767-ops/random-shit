package dev.dihclient.port.discordbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.DiscordAlarm;
import dev.dihclient.setting.ActionSetting;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Notifications;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.class_642;

public class DiscordBot extends Module implements ModuleManager.MenuTicking {
    private static final class SecretText extends StringSetting {
        SecretText(String name, String description, int max) {
            super(name, description, "", max);
        }

        @Override
        public String displayValue() {
            return this.get().isEmpty() ? "" : "set (hidden)";
        }
    }

    public final StringSetting token = this.add(new SecretText("Bot Token",
            "Token of your own bot (discord.com/developers > your application > Bot > Reset Token). Stays on this PC and is never put into shared configs.", 120));
    public final StringSetting allowed = this.text("Allowed Users",
            "Discord user IDs (comma separated) that may use the bot. Empty = only the owner of the bot application.", "", 256);
    public final StringSetting activity = this.text("Activity", "What the bot shows as 'Playing ...'.", "Minecraft", 64);
    public final BoolSetting showServer = this.bool("Show Server", "Adds the server you are on to the bot's status.", false);
    public final BoolSetting privateReplies = this.bool("Private Replies", "Slash command answers are only visible to you.", true);
    public final BoolSetting control = this.bool("Allow Control", "Allows stop, toggle and disconnect from Discord. Off: the bot only answers status questions.", true);
    public final BoolSetting say = this.bool("Allow Say", "Allows say: chat messages and /commands in game from Discord.", true);
    public final BoolSetting startMessage = this.bool("Start Message", "Sends you a DM every time the game starts and the bot comes online.", false);
    public final ActionSetting invite = this.action("Copy Invite Link", "Copies the link that adds your bot to a server (the bot has to be online).", this::copyInvite);
    public final ActionSetting test = this.action("Send Test", "Sends you a test DM from the bot.", this::sendTest);

    private static final ThreadFactory DAEMON = r -> {
        Thread t = new Thread(r, "DIHClient-DiscordBot");
        t.setDaemon(true);
        return t;
    };
    private static boolean stopHook;
    private static volatile DiscordBot instance;

    private final BotCommands commands = new BotCommands(this);
    private volatile Runtime runtime;
    private String lastPresence = "";
    private long lastPresenceAt;
    private volatile String state = "Off";
    private volatile String failedToken;

    public DiscordBot() {
        super("Discord Bot", Category.CLIENT,
                "Your own Discord bot: online while the game runs, offline when it closes. Ask it with /status, /stop, /toggle, /say or by DM. Discord Alarm can send through it instead of a webhook.");
        instance = this;
        DiscordAlarm.botSink = DiscordBot::alarm;
        if (!stopHook) {
            stopHook = true;
            ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
                DiscordBot b = instance;
                if (b != null) {
                    b.shutdown(1500);
                }
            });
        }
    }

    boolean allowControl() {
        return this.control.get();
    }

    boolean allowSay() {
        return this.say.get();
    }

    @Override
    protected void onEnable() {
        this.ensure();
    }

    @Override
    protected void onDisable() {
        this.shutdown(0);
        this.state = "Off";
        this.failedToken = null;
    }

    @Override
    public void onTick() {
        this.ensure();
        Runtime r = this.runtime;
        if (r != null && r.gateway.online()) {
            long now = System.currentTimeMillis();
            String text = this.presenceText();
            if (!text.equals(this.lastPresence) && now - this.lastPresenceAt > 20_000L) {
                this.lastPresence = text;
                this.lastPresenceAt = now;
                r.gateway.presence(presence(text));
            }
        }
    }

    @Override
    public String getInfo() {
        Runtime r = this.runtime;
        return r != null && r.gateway.online() ? "Online" : null;
    }

    @Override
    public List<String> details() {
        return List.of("State: " + this.state);
    }

    private void copyInvite() {
        Runtime r = this.runtime;
        if (r == null || r.appId == null) {
            Notifications.info(this.name(), "Turn the bot on and wait until it is online");
            return;
        }
        mc.field_1774.method_1455("https://discord.com/oauth2/authorize?client_id=" + r.appId + "&scope=bot+applications.commands&permissions=0");
        Notifications.info(this.name(), "Invite link copied");
    }

    private void sendTest() {
        if (alarm("✅ Test from DIHClient. Write `help` for commands.")) {
            Notifications.info(this.name(), "Test DM sent");
        } else {
            Notifications.info(this.name(), "Turn the bot on first");
        }
    }

    private void ensure() {
        if (!this.isEnabled()) {
            return;
        }
        String t = this.token.get().trim();
        if (t.startsWith("Bot ")) {
            t = t.substring(4).trim();
        }
        Runtime r = this.runtime;
        if (r != null && r.token.equals(t)) {
            return;
        }
        if (r != null) {
            this.shutdown(0);
        }
        if (t.isEmpty()) {
            if (!this.state.equals("No token")) {
                this.state = "No token";
                Notifications.warn(this.name(), "Put your bot token into the settings first");
            }
            return;
        }
        if (t.equals(this.failedToken)) {
            return;
        }
        this.lastPresence = this.presenceText();
        this.lastPresenceAt = System.currentTimeMillis();
        this.runtime = new Runtime(t);
        this.runtime.start(presence(this.lastPresence));
    }

    private void shutdown(long wait) {
        Runtime r = this.runtime;
        this.runtime = null;
        if (r != null) {
            r.stop(wait);
        }
    }

    private String presenceText() {
        String a = this.activity.get().isBlank() ? "Minecraft" : this.activity.get().trim();
        if (this.showServer.get() && mc.field_1687 != null) {
            class_642 s = mc.method_1558();
            a += s != null ? " on " + s.field_3761 : " in Singleplayer";
        }
        return a.length() > 120 ? a.substring(0, 120) : a;
    }

    private static JsonObject presence(String text) {
        JsonObject act = new JsonObject();
        act.addProperty("name", text);
        act.addProperty("type", 0);
        JsonArray acts = new JsonArray();
        acts.add(act);
        JsonObject p = new JsonObject();
        p.add("since", null);
        p.add("activities", acts);
        p.addProperty("status", "online");
        p.addProperty("afk", false);
        return p;
    }

    private static boolean alarm(String text) {
        DiscordBot b = instance;
        Runtime r = b == null || !b.isEnabled() ? null : b.runtime;
        if (r == null) {
            return false;
        }
        try {
            r.work.execute(() -> r.dmAll(text));
            return true;
        } catch (java.util.concurrent.RejectedExecutionException e) {
            return false;
        }
    }

    private void setState(String s, boolean fatal) {
        this.state = s;
        DIHClient.LOG.info("[DIHClient] Discord Bot: {}", s);
        if (fatal) {
            mc.execute(() -> Notifications.error(this.name(), s));
        }
    }

    private Set<String> allowedIds(Runtime r) {
        Set<String> ids = new LinkedHashSet<>();
        for (String s : this.allowed.get().split("[,\\s]+")) {
            if (s.matches("\\d{5,25}")) {
                ids.add(s);
            }
        }
        if (ids.isEmpty()) {
            ids.addAll(r.owners);
        }
        return ids;
    }

    private static String clip(String s) {
        return s.length() > 1900 ? s.substring(0, 1900) + "…" : s;
    }

    private static JsonObject message(String content) {
        JsonObject o = new JsonObject();
        o.addProperty("content", clip(content));
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        o.add("allowed_mentions", mentions);
        return o;
    }

    private static JsonObject option(String name, String description, boolean autocomplete) {
        JsonObject o = new JsonObject();
        o.addProperty("type", 3);
        o.addProperty("name", name);
        o.addProperty("description", description);
        o.addProperty("required", true);
        if (autocomplete) {
            o.addProperty("autocomplete", true);
        }
        return o;
    }

    private static JsonObject command(String name, String description, JsonObject option, boolean userInstall) {
        JsonObject c = new JsonObject();
        c.addProperty("name", name);
        c.addProperty("description", description);
        c.addProperty("type", 1);
        JsonArray integration = new JsonArray();
        integration.add(0);
        JsonArray contexts = new JsonArray();
        contexts.add(0);
        contexts.add(1);
        if (userInstall) {
            integration.add(1);
            contexts.add(2);
        }
        c.add("integration_types", integration);
        c.add("contexts", contexts);
        if (option != null) {
            JsonArray opts = new JsonArray();
            opts.add(option);
            c.add("options", opts);
        }
        return c;
    }

    private static JsonArray commandList(boolean userInstall) {
        JsonArray a = new JsonArray();
        a.add(command("status", "What the game is doing right now", null, userInstall));
        a.add(command("modules", "Active modules", null, userInstall));
        a.add(command("log", "The last notifications", null, userInstall));
        a.add(command("stop", "Turn off every active module except visuals", null, userInstall));
        a.add(command("toggle", "Turn a module on or off", option("module", "Module name", true), userInstall));
        a.add(command("say", "Send a chat message or /command in game", option("message", "Text or /command", false), userInstall));
        a.add(command("disconnect", "Leave the server", null, userInstall));
        a.add(command("tell", "Write a command as text, e.g. status, stop, toggle AutoBuild, say hi", option("text", "status, stop, toggle <module>, say <text> ...", false), userInstall));
        a.add(command("help", "List of commands", null, userInstall));
        return a;
    }

    private final class Runtime implements BotGateway.Handler {
        final String token;
        final ExecutorService work = Executors.newSingleThreadExecutor(DAEMON);
        final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(DAEMON);
        final HttpClient http = HttpClient.newBuilder().executor(Executors.newCachedThreadPool(DAEMON)).connectTimeout(Duration.ofSeconds(10)).build();
        final BotRest rest;
        final BotGateway gateway;
        final Set<String> owners = ConcurrentHashMap.newKeySet();
        final Map<String, String> dms = new ConcurrentHashMap<>();
        volatile String appId;
        volatile boolean registered;
        volatile boolean greeted;

        Runtime(String token) {
            this.token = token;
            this.rest = new BotRest(this.http, token);
            this.gateway = new BotGateway(this.http, token, this, this.timer);
        }

        void start(JsonObject presence) {
            DiscordBot.this.state = "Connecting";
            this.gateway.presence(presence);
            this.gateway.start();
        }

        void stop(long wait) {
            this.gateway.stop(wait);
            this.work.shutdownNow();
            this.timer.shutdownNow();
        }

        @Override
        public void state(String text, boolean fatal) {
            if (DiscordBot.this.runtime != this) {
                return;
            }
            DiscordBot.this.setState(fatal ? "Stopped: " + text : text, fatal);
            if (fatal) {
                DiscordBot.this.failedToken = this.token;
                mc.execute(() -> {
                    if (DiscordBot.this.runtime == this) {
                        DiscordBot.this.runtime = null;
                        this.stop(0);
                    }
                });
            }
        }

        @Override
        public void dispatch(String type, JsonObject d) {
            if (DiscordBot.this.runtime != this) {
                return;
            }
            switch (type) {
                case "READY" -> {
                    JsonObject user = d.getAsJsonObject("user");
                    this.appId = d.has("application") ? d.getAsJsonObject("application").get("id").getAsString() : user.get("id").getAsString();
                    DiscordBot.this.setState("Online as " + user.get("username").getAsString(), false);
                    this.work.execute(this::setup);
                }
                case "INTERACTION_CREATE" -> this.work.execute(() -> this.interaction(d));
                case "MESSAGE_CREATE" -> this.work.execute(() -> this.directMessage(d));
                default -> {
                }
            }
        }

        private void setup() {
            try {
                JsonObject app = this.rest.get("/applications/@me").getAsJsonObject();
                this.owners.clear();
                if (app.has("team") && app.get("team").isJsonObject()) {
                    JsonObject team = app.getAsJsonObject("team");
                    if (team.has("owner_user_id")) {
                        this.owners.add(team.get("owner_user_id").getAsString());
                    }
                } else if (app.has("owner") && app.get("owner").isJsonObject()) {
                    this.owners.add(app.getAsJsonObject("owner").get("id").getAsString());
                }
            } catch (Exception e) {
                DiscordBot.this.setState("Online, but could not read the bot owner (" + e.getMessage() + ")", false);
            }
            if (!this.registered) {
                try {
                    this.rest.put("/applications/" + this.appId + "/commands", commandList(true));
                    this.registered = true;
                } catch (BotRest.HttpError e) {
                    try {
                        this.rest.put("/applications/" + this.appId + "/commands", commandList(false));
                        this.registered = true;
                    } catch (Exception e2) {
                        DiscordBot.this.setState("Online, slash commands failed (" + e2.getMessage() + "), DMs still work", false);
                    }
                } catch (Exception e) {
                    DiscordBot.this.setState("Online, slash commands failed (" + e.getMessage() + "), DMs still work", false);
                }
            }
            if (DiscordBot.this.startMessage.get() && !this.greeted) {
                this.greeted = true;
                this.dmAll("🟢 Game started, DIHClient is online. Write `help` for commands.");
            }
        }

        private boolean allowed(String userId) {
            return DiscordBot.this.allowedIds(this).contains(userId);
        }

        private String answer(String name, String arg) {
            try {
                return name.equals("tell") ? DiscordBot.this.commands.tell(arg) : DiscordBot.this.commands.run(name, arg);
            } catch (RuntimeException e) {
                return "Failed: " + e.getMessage();
            }
        }

        private void interaction(JsonObject d) {
            try {
                JsonObject user = d.has("member") && d.get("member").isJsonObject() ? d.getAsJsonObject("member").getAsJsonObject("user") : d.getAsJsonObject("user");
                String uid = user.get("id").getAsString();
                int type = d.get("type").getAsInt();
                String path = "/interactions/" + d.get("id").getAsString() + "/" + d.get("token").getAsString() + "/callback";
                JsonObject data = d.getAsJsonObject("data");
                String name = data.get("name").getAsString();
                String arg = "";
                if (data.has("options")) {
                    for (JsonElement e : data.getAsJsonArray("options")) {
                        JsonObject o = e.getAsJsonObject();
                        if (o.has("value")) {
                            arg = o.get("value").getAsString();
                        }
                    }
                }
                JsonObject response = new JsonObject();
                if (type == 4) {
                    JsonArray choices = new JsonArray();
                    if (this.allowed(uid)) {
                        for (String m : DiscordBot.this.commands.moduleNames(arg)) {
                            JsonObject c = new JsonObject();
                            c.addProperty("name", m);
                            c.addProperty("value", m);
                            choices.add(c);
                        }
                    }
                    JsonObject choiceData = new JsonObject();
                    choiceData.add("choices", choices);
                    response.addProperty("type", 8);
                    response.add("data", choiceData);
                } else if (type == 2) {
                    String text = this.allowed(uid) ? this.answer(name, arg) : "You are not allowed to use this bot. The owner can add your ID under Allowed Users.";
                    JsonObject msg = message(text);
                    if (DiscordBot.this.privateReplies.get()) {
                        msg.addProperty("flags", 64);
                    }
                    response.addProperty("type", 4);
                    response.add("data", msg);
                } else {
                    return;
                }
                this.rest.post(path, response);
            } catch (Exception e) {
                DIHClient.LOG.warn("[DIHClient] Discord Bot interaction failed: {}", e.toString());
            }
        }

        private void directMessage(JsonObject d) {
            try {
                if (d.has("guild_id") || !d.has("author")) {
                    return;
                }
                JsonObject author = d.getAsJsonObject("author");
                if (author.has("bot") && author.get("bot").getAsBoolean()) {
                    return;
                }
                if (!this.allowed(author.get("id").getAsString())) {
                    return;
                }
                String content = d.has("content") ? d.get("content").getAsString() : "";
                if (content.isBlank()) {
                    return;
                }
                this.rest.post("/channels/" + d.get("channel_id").getAsString() + "/messages", message(this.answer("tell", content)));
            } catch (Exception e) {
                DIHClient.LOG.warn("[DIHClient] Discord Bot DM failed: {}", e.toString());
            }
        }

        void dmAll(String text) {
            List<String> failed = new ArrayList<>();
            for (String uid : DiscordBot.this.allowedIds(this)) {
                try {
                    String channel = this.dms.get(uid);
                    if (channel == null) {
                        JsonObject body = new JsonObject();
                        body.addProperty("recipient_id", uid);
                        channel = this.rest.post("/users/@me/channels", body).getAsJsonObject().get("id").getAsString();
                        this.dms.put(uid, channel);
                    }
                    this.rest.post("/channels/" + channel + "/messages", message(text));
                } catch (Exception e) {
                    failed.add(uid + " (" + e.getMessage() + ")");
                }
            }
            if (!failed.isEmpty()) {
                DIHClient.LOG.warn("[DIHClient] Discord Bot could not DM {}", failed);
            }
        }
    }
}
