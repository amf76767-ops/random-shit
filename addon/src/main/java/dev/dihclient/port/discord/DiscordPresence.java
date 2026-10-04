package dev.dihclient.port.discord;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.StringSetting;
import java.time.Instant;
import net.minecraft.class_2535;
import net.minecraft.class_310;
import net.minecraft.class_634;
import net.minecraft.class_642;
import net.minecraft.class_8732;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Shows DIHClient (and, if you allow it, the server you are on) in your Discord status. It talks only to the local Discord
 * app over its IPC pipe / unix socket (see {@link DiscordIpc}); no web request is made. By default no server address is sent.
 * Runs in the main menu too ({@link ModuleManager.MenuTicking}), so leaving a world is noticed.
 */
public class DiscordPresence extends Module implements ModuleManager.MenuTicking {
    /** The Anubis Client application. Its name shows as the game name in Discord; replace it to get your own. */
    static final String DEFAULT_APP_ID = "1553391775840866314";
    static final String DEFAULT_IMAGE = "logo";
    private static final String LARGE_TEXT = "DIHClient";
    private static final long GAME_STARTED = ProcessHandle.current().info().startInstant().orElseGet(Instant::now).getEpochSecond();

    private static volatile DiscordLink current;
    private static boolean hookInstalled;

    public final StringSetting appId = this.text("Application ID",
            "Discord application the status belongs to (discord.com/developers). The default is the Anubis Client one: Discord will show its name and 'Image Asset' from it. Put your own id here, and an image uploaded to it below.",
            DEFAULT_APP_ID, 24);
    public final StringSetting imageAsset = this.text("Image Asset",
            "Name of the large image in your Discord application (Rich Presence > Art Assets). Empty = no image.",
            DEFAULT_IMAGE, 64);
    public final BoolSetting showAddress = this.bool("Show Server Address",
            "Show the server you play on (or Singleplayer / LAN World / Realms). Off: only 'Playing Minecraft' and the client version.", false);
    public final BoolSetting showTime = this.bool("Show Elapsed Time",
            "Show how long you have been on the server (or in the game).", true);
    public final StringSetting stateText = this.text("State Text",
            "Second line of the status. Empty = 'DIHClient v" + DIHClient.VERSION + "'.", "", 128);
    public final BoolSetting showMenu = this.bool("Show In Main Menu",
            "Keep a status ('In Main Menu') while no world is open. Off: the status is removed when you leave a world.", false);

    private DiscordLink link;
    private class_2535 session;
    private long joinedAt;
    private boolean loggedBadId;
    private String stateIn;
    private String stateOut;

    public DiscordPresence() {
        super("Discord Presence", Category.CLIENT, "Shows DIHClient and the server you are on in your Discord status.");
    }

    @Override
    protected void onEnable() {
        this.session = null;
        this.ensureLink();
        this.refresh();
    }

    @Override
    protected void onDisable() {
        this.stopLink();
    }

    @Override
    public void onTick() {
        try {
            // a config-enabled module gets onEnable late (first world), but ticks in the menu already
            this.ensureLink();
            this.refresh();
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Discord Presence tick failed: {}", t.toString());
        }
    }

    @Override
    public String getInfo() {
        return null;
    }

    private void ensureLink() {
        if (this.link != null && !this.link.finished()) {
            return;
        }
        DiscordLink fresh = new DiscordLink(this.link, this::isEnabled);
        this.link = fresh;
        current = fresh;
        installHook();
        fresh.start();
    }

    private void stopLink() {
        if (this.link != null) {
            this.link.stop();
        }
        this.session = null;
    }

    /** Builds the wanted status on the game thread and hands it over; the link thread does the IO. */
    private void refresh() {
        DiscordLink l = this.link;
        if (l == null) {
            return;
        }
        l.publish(this.build());
    }

    private DiscordCard build() {
        String id = this.appId.get() == null ? "" : this.appId.get().strip();
        if (!DiscordCard.validAppId(id)) {
            if (!this.loggedBadId) {
                this.loggedBadId = true;
                DIHClient.LOG.info("[DIHClient] Discord Presence: no valid Discord application id set; idle");
            }
            return null;
        }
        this.loggedBadId = false;
        class_310 mc = class_310.method_1551();
        class_634 listener = mc.method_1562();
        DiscordCard.Place place;
        String address = null;
        long startedAt;
        if (listener != null) {
            if (listener.method_48296() != this.session) {
                this.session = listener.method_48296();
                this.joinedAt = System.currentTimeMillis() / 1000L;
            }
            class_642 server = listener.method_45734();
            if (mc.method_1542()) {
                place = DiscordCard.Place.SINGLEPLAYER;
            } else if (server == null) {
                place = DiscordCard.Place.MULTIPLAYER;
            } else if (server.method_52811()) {
                place = DiscordCard.Place.REALMS;
            } else if (server.method_2994()) {
                place = DiscordCard.Place.LAN;
            } else {
                place = DiscordCard.Place.MULTIPLAYER;
                address = server.field_3761;
            }
            startedAt = this.joinedAt;
        } else {
            if (this.session != null && this.session.method_10758() && this.session.method_10744() instanceof class_8732) {
                // server switch (reconfiguration): keep what is shown instead of flickering through the menu
                DiscordLink l = this.link;
                return l == null ? null : l.current();
            }
            this.session = null;
            if (!this.showMenu.get()) {
                return null;
            }
            place = DiscordCard.Place.MENU;
            startedAt = GAME_STARTED;
        }
        String details = DiscordCard.details(place, address, this.showAddress.get());
        return new DiscordCard(details, this.state(), this.showTime.get() ? startedAt : 0L, id,
                DiscordCard.assetKey(this.imageAsset.get()), LARGE_TEXT);
    }

    /** Custom state text, or the default; cleaned once per change, not per tick. */
    private String state() {
        String in = this.stateText.get();
        if (!java.util.Objects.equals(in, this.stateIn)) {
            this.stateIn = in;
            this.stateOut = DiscordCard.clip(in);
        }
        return this.stateOut != null ? this.stateOut : "DIHClient v" + DIHClient.VERSION;
    }

    /** Game close: give the link a moment to remove the status. */
    private static synchronized void installHook() {
        if (hookInstalled) {
            return;
        }
        hookInstalled = true;
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                DiscordLink l = current;
                if (l != null) {
                    l.stop();
                    l.join(1500L);
                }
            }, "DIHClient Discord Presence shutdown"));
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Discord Presence: no shutdown hook ({})", t.toString());
        }
    }
}
