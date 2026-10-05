package dev.dihclient.port.chunks;

import dev.dihclient.DIHClient;
import dev.dihclient.port.PacketBus;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.class_1132;
import net.minecraft.class_1923;
import net.minecraft.class_2596;
import net.minecraft.class_2672;
import net.minecraft.class_2676;
import net.minecraft.class_310;
import net.minecraft.class_5218;
import net.minecraft.class_638;
import net.minecraft.class_642;
import net.minecraft.class_6606;
import net.minecraft.class_746;

/** Ported from an open-source client (GPL-3.0). */
public final class PlayerBypassLightTracker {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final ProtectedChunkStore PROTECTED_CHUNKS = ProtectedChunkStore.get();
    private static final ChunkFlagTracker TRACKER = new ChunkFlagTracker(PROTECTED_CHUNKS);
    private static final ChunkEntryGuard ENTRY_GUARD = new ChunkEntryGuard();
    private static final PacketBus.Listener APPLIED = PlayerBypassLightTracker::onPacketApplied;
    private static class_638 lastLevel;

    private PlayerBypassLightTracker() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        PacketBus.applied(APPLIED);
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    PROTECTED_CHUNKS.flushAndWait();
                } catch (Throwable ignored) {

                }
            }, "DIHClient Player Bypass flush"));
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Player Bypass could not register its shutdown save", t);
        }
    }

    public static void onTick() {
        try {
            class_310 mc = class_310.method_1551();
            syncLevel(mc);
            class_746 player = mc.field_1724;
            if (lastLevel != null && player != null) {
                ENTRY_GUARD.update(player.method_31476().method_8324(), PlayerBypassLightTracker::isLoaded, TRACKER::isFlaggedOrHeld,
                        PROTECTED_CHUNKS::protect);
            }
            PROTECTED_CHUNKS.tickAutosave();
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Player Bypass tick failed", t);
        }
    }

    private static void onPacketApplied(class_2596<?> packet) {
        int chunkX;
        int chunkZ;
        class_6606 data;
        if (packet instanceof class_2672 chunk) {
            chunkX = chunk.method_11523();
            chunkZ = chunk.method_11524();
            data = chunk.method_38599();
        } else if (packet instanceof class_2676 light) {
            chunkX = light.method_11558();
            chunkZ = light.method_11554();
            data = light.method_38600();
        } else {
            return;
        }
        class_310 mc = class_310.method_1551();
        class_638 level = mc.field_1687;
        syncLevel(mc);
        if (level == null || data == null) {
            return;
        }
        int sections = level.method_32890();
        int bottom = level.method_32891();
        if (mc.field_1724 != null && mc.field_1724.method_23318() < 0.0) {
            TRACKER.hold(chunkX, chunkZ, data.method_38601(), data.method_38604(), data.method_38606(), sections, bottom);
        } else {
            TRACKER.ingest(chunkX, chunkZ, data.method_38601(), data.method_38604(), data.method_38606(), sections, bottom);
        }
    }

    private static void syncLevel(class_310 mc) {
        class_638 level = mc.field_1687;
        if (level != lastLevel) {
            TRACKER.clear();
            ENTRY_GUARD.reset();
            lastLevel = level;
            PROTECTED_CHUNKS.flush();
            if (level == null) {
                PROTECTED_CHUNKS.select(null, null);
            } else {
                PROTECTED_CHUNKS.select(worldKey(mc), level.method_27983().method_29177().toString());
            }
        }
    }

    private static String worldKey(class_310 mc) {
        class_1132 local = mc.method_1576();
        if (local != null) {
            Path folder = local.method_27050(class_5218.field_24188).normalize().getFileName();
            return ProtectedChunkStore.singleplayerKey(folder == null ? "world" : folder.toString());
        }
        class_642 server = mc.method_1558();
        return ProtectedChunkStore.serverKey(server == null ? null : server.field_3761);
    }

    private static boolean isLoaded(long chunkKey) {
        return lastLevel != null && lastLevel.method_2935().method_12123(class_1923.method_8325(chunkKey), class_1923.method_8332(chunkKey));
    }

    public static void setSpottedListener(ChunkFlagTracker.SpottedListener listener) {
        TRACKER.setSpottedListener(listener);
    }

    public static ChunkFlagTracker tracker() {
        return TRACKER;
    }
}
