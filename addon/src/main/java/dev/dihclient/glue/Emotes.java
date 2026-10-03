package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.emote.Emote;
import dev.dihclient.emote.EmotePose;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.util.Notifications;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.class_10042;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_4587;
import org.joml.Quaternionf;

/**
 * Emotes for you and for everyone who has DIHClient: bind a key to the module, press it and your body dances, spins,
 * flips, bows ... Other DIHClient users on the same server see it too, because the emote is passed on through the
 * free relay ntfy.sh (sent: your player name, the emote, and a hash of the server address as the "room").
 * Players with a custom 3D model show the emote on the model, everyone else on the normal body.
 * Switch Share / Show Others off to keep it all on your own screen.
 */
public class Emotes extends Module {
    private static final String RELAY = "https://ntfy.sh/";
    private static final String TAG = "DIH1";
    private static final float PIVOT = 0.9F;

    private record Active(Emote emote, long startNanos) {
    }

    private static final Map<String, Active> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_REMOTE = new ConcurrentHashMap<>();
    private static final ExecutorService SEND = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DIHClient-emote-send");
        t.setDaemon(true);
        return t;
    });

    public final EnumSetting<Emote> emote = this.mode("Emote",
            "Dance · Spin · Backflip · Bow · Hop · Sleep · Wiggle · No · Faint. Bind a key to the module to play it.", Emote.DANCE);
    public final BoolSetting share = this.bool("Share", "Tell other DIHClient users on this server when you do an emote (through the free relay ntfy.sh: "
            + "your name, the emote and a hash of the server address).", true);
    public final BoolSetting others = this.bool("Show Others", "Show the emotes of other DIHClient users (listens on the relay while you are on a server).", true);

    private volatile boolean netRun;
    private volatile String netTopic = "";
    private volatile HttpURLConnection netConn;
    private long lastSend;
    private boolean announced;

    public Emotes() {
        super("Emotes", Category.FUN, "Emotes on a key: dance, spin, flip, bow ... Everyone with DIHClient on the server sees them. Bind a key to this module.");
        this.action("Play", "Plays the selected emote now.", this::onAction);
        this.action("Next Emote", "Selects the next emote.", () -> {
            Emote[] all = Emote.values();
            this.emote.set(all[(this.emote.get().ordinal() + 1) % all.length]);
            Notifications.info(this.name(), this.emote.get().title);
        });
    }

    @Override
    public boolean isActionModule() {
        return true;
    }

    @Override
    public void onAction() {
        if (mc.field_1724 == null || mc.field_1687 == null || mc.field_1755 != null) {
            return;
        }
        Emote e = this.emote.get();
        String me = mc.field_1724.method_7334().name();
        ACTIVE.put(me.toLowerCase(Locale.ROOT), new Active(e, System.nanoTime()));
        long now = System.currentTimeMillis();
        if (this.share.get() && now - this.lastSend > 2000 && serverAddress() != null) {
            this.lastSend = now;
            String topic = topic(serverAddress());
            String line = TAG + "|" + me + "|" + e.name();
            SEND.execute(() -> post(topic, line));
        }
    }

    @Override
    protected void onDisable() {
        this.stopNet();
        ACTIVE.clear();
    }

    @Override
    public void onWorldChange() {
        ACTIVE.clear();
        LAST_REMOTE.clear();
        this.stopNet();
    }

    @Override
    public String getInfo() {
        return this.emote.get().title;
    }

    @Override
    public void onTick() {
        if (mc.field_1724 == null || mc.field_1687 == null) {
            return;
        }
        if (mc.field_1724.field_6012 % 20 != 0) {
            return;
        }
        String address = serverAddress();
        boolean want = this.others.get() && address != null;
        if (!want) {
            this.stopNet();
        } else if (!this.netRun || !this.netTopic.equals(topic(address))) {
            this.startNet(topic(address));
        }
    }

    // ---- who is doing what

    static String serverAddress() {
        var info = mc.method_1558();
        if (info == null || info.field_3761 == null || info.field_3761.isBlank()) {
            return null;
        }
        return info.field_3761.trim().toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
    }

    static String topic(String address) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(("dih-emotes-v1|" + address).getBytes(StandardCharsets.UTF_8));
            return "dihemote1" + HexFormat.of().formatHex(h, 0, 12);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The pose of this player right now (the show ends when the player starts to walk). */
    public static EmotePose poseOf(class_1297 e) {
        if (ACTIVE.isEmpty() || !(e instanceof class_1657 p)) {
            return null;
        }
        String key = p.method_7334().name().toLowerCase(Locale.ROOT);
        Active a = ACTIVE.get(key);
        if (a == null) {
            return null;
        }
        double t = (System.nanoTime() - a.startNanos) / 1e9;
        double speed = Math.hypot(p.method_23317() - p.field_6038, p.method_23321() - p.field_5989);
        if (t >= a.emote.seconds || (t > 0.3 && speed > 0.12)) {
            ACTIVE.remove(key);
            return null;
        }
        return a.emote.at(t);
    }

    /** Called for every living entity when its render state is filled in. */
    public static void capture(class_1309 entity, class_10042 state) {
        try {
            ((EmoteHolder) (Object) state).dih$setPose(poseOf(entity));
        } catch (Throwable ignored) {
            // the render state has no holder (mixin not applied): no emotes on the plain body, nothing breaks
        }
    }

    /** Called at the end of the body set-up of the entity renderer. */
    public static void transform(class_10042 state, class_4587 matrices) {
        EmotePose pose;
        try {
            pose = ((EmoteHolder) (Object) state).dih$getPose();
        } catch (Throwable t) {
            return;
        }
        if (pose == null || pose.isNone()) {
            return;
        }
        matrices.method_22904(0.0, pose.dy(), 0.0);
        matrices.method_22904(0.0, PIVOT, 0.0);
        matrices.method_22907(new Quaternionf().rotateY((float) Math.toRadians(pose.yaw()))
                .rotateX((float) Math.toRadians(-pose.pitch())).rotateZ((float) Math.toRadians(pose.roll())));
        matrices.method_22904(0.0, -PIVOT, 0.0);
    }

    // ---- the relay

    private void startNet(String topic) {
        this.stopNet();
        this.netRun = true;
        this.netTopic = topic;
        if (!this.announced) {
            this.announced = true;
            Notifications.info(this.name(), "Emotes are shared with other DIHClient users on this server (relay ntfy.sh). Turn Share / Show Others off to stay local.");
        }
        Thread t = new Thread(() -> this.listen(topic), "DIHClient-emote-listen");
        t.setDaemon(true);
        t.start();
    }

    private void stopNet() {
        this.netRun = false;
        this.netTopic = "";
        HttpURLConnection c = this.netConn;
        if (c != null) {
            c.disconnect();
        }
    }

    private void listen(String topic) {
        while (this.netRun && topic.equals(this.netTopic)) {
            try {
                HttpURLConnection c = (HttpURLConnection) URI.create(RELAY + topic + "/raw").toURL().openConnection();
                this.netConn = c;
                c.setConnectTimeout(10_000);
                c.setReadTimeout(90_000);
                c.setRequestProperty("User-Agent", "DIHClient-Emotes");
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (this.netRun && topic.equals(this.netTopic) && (line = r.readLine()) != null) {
                        this.incoming(line);
                    }
                }
            } catch (IOException | RuntimeException e) {
                // offline, timeout or closed by us: try again below
            }
            if (this.netRun && topic.equals(this.netTopic)) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private static void post(String topic, String line) {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(RELAY + topic).toURL().openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            c.setRequestProperty("User-Agent", "DIHClient-Emotes");
            try (OutputStream out = c.getOutputStream()) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
            c.getResponseCode();
            c.disconnect();
        } catch (IOException e) {
            DIHClient.LOG.debug("[DIHClient] emote could not be sent: {}", e.toString());
        }
    }

    /** One line from the relay. Anything that does not look exactly right is dropped. */
    private void incoming(String line) {
        if (line.length() > 64 || !line.startsWith(TAG + "|")) {
            return;
        }
        String[] p = line.split("\\|", 3);
        if (p.length != 3 || !p[1].matches("[A-Za-z0-9_]{1,16}")) {
            return;
        }
        Emote e = Emote.byName(p[2]);
        if (e == null) {
            return;
        }
        String name = p[1];
        mc.execute(() -> {
            if (!this.isEnabled() || !this.others.get() || mc.field_1724 == null || mc.field_1687 == null) {
                return;
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (key.equals(mc.field_1724.method_7334().name().toLowerCase(Locale.ROOT))) {
                return; // our own message coming back
            }
            long now = System.currentTimeMillis();
            Long last = LAST_REMOTE.get(key);
            if (last != null && now - last < 1500) {
                return;
            }
            for (class_1657 p2 : mc.field_1687.method_18456()) {
                if (p2.method_7334().name().equalsIgnoreCase(name)) {
                    LAST_REMOTE.put(key, now);
                    ACTIVE.put(key, new Active(e, System.nanoTime()));
                    return;
                }
            }
        });
    }
}
