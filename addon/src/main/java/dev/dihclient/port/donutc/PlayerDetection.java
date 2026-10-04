package dev.dihclient.port.donutc;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.DiscordAlarm;
import dev.dihclient.modules.client.NotificationsModule;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Notifications;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.class_2535;
import net.minecraft.class_2561;
import net.minecraft.class_634;
import net.minecraft.class_638;
import net.minecraft.class_742;
import net.minecraft.class_746;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Detects when other players are in the world around you (every player entity the client has loaded, so within render
 * range, not the tab list). On a change of that group it warns you, switches the listed modules, can send a panic
 * /pay, turns itself off and can disconnect after half a second.
 * <p>
 * Differences to Anubis: the Discord webhook (URL, self ping, Discord ID) is gone, the module has no HTTP code of its
 * own: the alert is handed to Discord Alarm, which forwards it to the webhook you set there when it is on. The toast
 * is the DIH notification toast. Module names for "Modules To Toggle" are matched against the DIH modules.
 */
public class PlayerDetection extends Module {
    public enum Notify { CHAT, TOAST, BOTH }

    private static final long DISCONNECT_DELAY_MS = 500L;

    public final StringSetting modulesToToggle = this.text("Modules To Toggle", "Comma separated module names that are toggled when a player is detected.", "", 512);
    public final BoolSetting enableDisconnect = this.bool("Disconnect", "Leaves the server half a second after a player is detected.", true);
    public final EnumSetting<Notify> notificationMode = this.mode("Notification Mode", "Chat: message in the chat · Toast: DIH toast · Both.", Notify.BOTH);
    public final BoolSetting toggleOnPlayer = this.bool("Toggle On Detect", "Turns this module off after it detected someone, so it does not fire again.", true);
    public final StringSetting userWhitelist = this.text("User Whitelist", "Comma separated player names that never count.", "", 512);
    public final BoolSetting enablePanicPay = this.bool("Enable Panic Pay", "Sends /pay to the target below when a player is detected.", false);
    public final StringSetting panicPayTarget = this.text("Target Player", "Who gets the money.", "", 32)
            .visibleWhen(this.enablePanicPay::get);
    public final StringSetting panicPayAmount = this.text("Amount", "How much to send, as the /pay command wants it (e.g. 1m).", "", 32)
            .visibleWhen(this.enablePanicPay::get);

    private final Set<String> detectedPlayers = new LinkedHashSet<>();
    private class_638 lastLevel;
    private String whitelistRaw;
    private Set<String> whitelist = Set.of();

    public PlayerDetection() {
        super("Player Detection", Category.DONUT, "Detects when players are in the world.");
    }

    @Override
    protected void onEnable() {
        this.detectedPlayers.clear();
        this.lastLevel = null;
    }

    @Override
    protected void onDisable() {
        this.detectedPlayers.clear();
        this.lastLevel = null;
    }

    @Override
    public void onWorldChange() {
        this.detectedPlayers.clear();
        this.lastLevel = null;
    }

    @Override
    public void onTick() {
        class_746 self = mc.field_1724;
        class_638 level = mc.field_1687;
        if (self == null || level == null) {
            this.detectedPlayers.clear();
            this.lastLevel = null;
            return;
        }
        if (level != this.lastLevel) {
            this.detectedPlayers.clear();
            this.lastLevel = level;
        }
        String selfName = self.method_7334().name();
        UUID selfId = self.method_5667();
        List<String> names = new java.util.ArrayList<>();
        for (class_742 player : level.method_18456()) {
            if (player != self && !selfId.equals(player.method_5667())) {
                names.add(player.method_7334().name());
            }
        }
        Set<String> current = PlayerDetectionLogic.relevant(names, selfName, this.whitelist());
        if (current.isEmpty()) {
            this.detectedPlayers.clear();
        } else if (!current.equals(this.detectedPlayers)) {
            this.detectedPlayers.clear();
            this.detectedPlayers.addAll(current);
            try {
                this.handleDetection(current);
            } catch (Throwable t) {
                DIHClient.LOG.warn("[DIHClient] Player Detection reaction failed", t);
            }
        }
    }

    /** Everything that happens when the group of players changes (not empty). */
    private void handleDetection(Set<String> players) {
        String text = (players.size() == 1 ? "Player detected: " : "Players detected: ") + PlayerDetectionLogic.shortNames(players, 3);
        switch (this.notificationMode.get()) {
            case CHAT -> {
                Notifications.chat(text);
                forwardToDiscord(text);
            }
            case TOAST -> Notifications.warn(this.name(), text);
            default -> {
                Notifications.warn(this.name(), text);
                if (!echoesToChat()) {
                    Notifications.chat(text);
                }
            }
        }

        for (Module module : this.resolveModulesToToggle()) {
            module.toggle();
        }
        if (this.enablePanicPay.get()) {
            this.panicPay();
        }
        if (this.toggleOnPlayer.get()) {
            this.toggle();
        }
        if (this.enableDisconnect.get()) {
            this.scheduleDisconnect(new LinkedHashSet<>(players));
        }
    }

    /** True when the Notifications module already prints every toast into the chat. */
    private static boolean echoesToChat() {
        NotificationsModule notes = ModuleManager.of(NotificationsModule.class);
        return notes != null && notes.isEnabled() && notes.chat.get();
    }

    /** The chat-only mode shows no toast, so Discord Alarm would not hear of it: tell it directly. */
    private static void forwardToDiscord(String text) {
        try {
            DiscordAlarm.onToast("Player Detection", text, Notifications.Type.WARNING);
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Player Detection could not reach Discord Alarm", t);
        }
    }

    private List<Module> resolveModulesToToggle() {
        Set<Module> resolved = new LinkedHashSet<>();
        ModuleManager manager = DIHClient.modules();
        if (manager == null) {
            return List.of();
        }
        for (String key : PlayerDetectionLogic.parseList(this.modulesToToggle.get())) {
            Module module = manager.get(key);
            if (module != null && module != this) {
                resolved.add(module);
            }
        }
        return List.copyOf(resolved);
    }

    private Set<String> whitelist() {
        String raw = this.userWhitelist.get();
        if (!raw.equals(this.whitelistRaw)) {
            this.whitelist = PlayerDetectionLogic.parseNames(raw);
            this.whitelistRaw = raw;
        }
        return this.whitelist;
    }

    /** Sends /pay to the target; shows it in the chat history like a typed command. */
    private void panicPay() {
        String command = PlayerDetectionLogic.payCommand(this.panicPayTarget.get(), this.panicPayAmount.get());
        if (command == null) {
            Notifications.warn(this.name(), "Panic pay target or amount is not set (no spaces allowed).");
            return;
        }
        class_634 connection = mc.method_1562();
        if (connection != null) {
            if (mc.field_1705 != null) {
                mc.field_1705.method_1743().method_1803("/" + command);
            }
            connection.method_45730(command);
            Notifications.chat("Panic pay sent: " + this.panicPayAmount.get().trim() + " to " + this.panicPayTarget.get().trim());
        }
    }

    private void scheduleDisconnect(Set<String> players) {
        class_634 listener = mc.method_1562();
        if (listener == null) {
            return;
        }
        class_2535 session = listener.method_48296();
        CompletableFuture.delayedExecutor(DISCONNECT_DELAY_MS, TimeUnit.MILLISECONDS, mc).execute(() -> this.disconnectFromServer(session, players));
    }

    /** Only if it is still the same connection: a reconnect in the meantime must not be cut. */
    private void disconnectFromServer(class_2535 session, Set<String> players) {
        try {
            class_634 listener = mc.method_1562();
            if (mc.field_1687 != null && listener != null && listener.method_48296() == session) {
                String names = String.join(", ", players);
                session.method_10747(class_2561.method_43470("Player(s) detected: " + names));
                Notifications.warn(this.name(), "Disconnected from the server (" + PlayerDetectionLogic.shortNames(players, 3) + ")");
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Player Detection could not disconnect", t);
        }
    }
}
