package dev.dihclient.port.packets;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.port.PacketBus;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Notifications;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.class_2596;

public class PacketLog extends Module {
    public enum FilterMode { ALL, WHITELIST, BLACKLIST }

    private static final int MAX_QUEUE = 400;
    private static final int CHAT_PER_TICK = 8;

    private record Entries(String raw, String[] parts) {
    }

    public final BoolSetting received = this.bool("Received", "Read packets the server sends to you (S2C).", true);
    public final BoolSetting sent = this.bool("Sent", "Read packets you send to the server (C2S).", true);
    public final BoolSetting showFields = this.bool("Show Fields", "Also print the contents (fields) of each packet, not just its name.", true);
    public final IntSetting maxValueLength = this.integer("Max Value Length", "Longest a single field value can be before it is cut off.", 80, 10, 500)
            .visibleWhen(this.showFields::get);
    public final BoolSetting toChat = this.bool("Print To Chat", "Print packets in the chat. Turn off if it is too spammy; they still go to the log.", true);
    public final BoolSetting toLog = this.bool("Print To Log", "Write packets to latest.log.", true);
    public final EnumSetting<FilterMode> filterMode = this.mode("Filter Mode",
            "All: read every packet · Whitelist: only the packets listed below · Blacklist: everything except the listed packets.", FilterMode.BLACKLIST);
    public final StringSetting packets = this.text("Packets",
            "Comma separated packet ids, e.g. move_player_pos, keep_alive. A part is enough: \"move\" covers every move packet.",
            "move_player, keep_alive, ping, pong, set_time, rotate_head, move_entity, teleport_entity, entity_position_sync, set_entity_motion, level_particles", 2048)
            .visibleWhen(() -> this.filterMode.get() != FilterMode.ALL);

    private final PacketBus.Listener receiveListener = this::onReceive;
    private final ConcurrentLinkedQueue<String> chatQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private volatile Entries entries = new Entries("", new String[0]);

    public PacketLog() {
        super("Packet Log", Category.DEBUG, "Reads packets sent to and received from the server (chat and latest.log). Read-only.");
    }

    @Override
    protected void onEnable() {
        this.chatQueue.clear();
        this.queued.set(0);
        PacketBus.remove(this.receiveListener);
        PacketBus.received(this.receiveListener);
    }

    @Override
    protected void onDisable() {
        PacketBus.remove(this.receiveListener);
        this.chatQueue.clear();
        this.queued.set(0);
    }

    @Override
    public void onTick() {
        for (int i = 0; i < CHAT_PER_TICK; i++) {
            String line = this.chatQueue.poll();
            if (line == null) {
                return;
            }
            this.queued.decrementAndGet();
            try {
                Notifications.chat(line);
            } catch (Throwable t) {
                DIHClient.LOG.warn("[DIHClient] Packet Log could not write to the chat", t);
            }
        }
    }

    @Override
    public boolean onPacketSend(class_2596<?> packet) {
        if (this.sent.get()) {
            this.read("->", packet);
        }
        return false;
    }

    private void onReceive(class_2596<?> packet) {
        if (this.received.get()) {
            this.read("<-", packet);
        }
    }

    private void read(String arrow, class_2596<?> packet) {
        try {
            String name = PacketNames.name(packet);
            if (!this.allowed(name)) {
                return;
            }
            String line = arrow + " " + name;
            if (this.showFields.get()) {
                line += PacketNames.describe(packet, this.maxValueLength.get());
            }
            if (this.toLog.get()) {
                DIHClient.LOG.info("[PacketLog] {}", line);
            }
            if (this.toChat.get() && this.queued.get() < MAX_QUEUE) {
                this.queued.incrementAndGet();
                this.chatQueue.add("§7" + line);
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Packet Log could not read a packet", t);
        }
    }

    private boolean allowed(String name) {
        FilterMode mode = this.filterMode.get();
        if (mode == FilterMode.ALL) {
            return true;
        }
        boolean listed = PacketFilter.matches(name, this.parts());
        return mode == FilterMode.WHITELIST ? listed : !listed;
    }

    private String[] parts() {
        String raw = this.packets.get();
        Entries current = this.entries;
        if (!raw.equals(current.raw())) {
            current = new Entries(raw, PacketFilter.parseList(raw));
            this.entries = current;
        }
        return current.parts();
    }

    @Override
    public String getInfo() {
        String mode = this.filterMode.get().name();
        return mode.charAt(0) + mode.substring(1).toLowerCase();
    }
}
