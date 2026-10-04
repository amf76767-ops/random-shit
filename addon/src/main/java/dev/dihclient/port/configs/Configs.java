package dev.dihclient.port.configs;

import dev.dihclient.DIHClient;
import dev.dihclient.port.PacketBus;
import dev.dihclient.util.Notifications;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.class_2678;
import net.minecraft.class_310;
import net.minecraft.class_642;

/** The one {@link ConfigStore} of the game, wired to DIH's profile files, and the "load on this server" step at join. */
public final class Configs {
    private static ConfigStore store;
    private static boolean installed;

    private Configs() {
    }

    public static synchronized ConfigStore store() {
        if (store == null) {
            store = new ConfigStore(DIHClient.config().dir().resolve("configs.json"), new ConfigStore.Backend() {
                @Override
                public boolean save(int id) {
                    return DIHClient.config().saveProfile(id);
                }

                @Override
                public boolean load(int id) {
                    return DIHClient.config().loadProfile(id);
                }

                @Override
                public boolean exists(int id) {
                    return DIHClient.config().profileExists(id);
                }

                @Override
                public void delete(int id) {
                    try {
                        Files.deleteIfExists(DIHClient.config().profilePath(id));
                    } catch (IOException e) {
                        DIHClient.LOG.warn("[DIHClient] could not delete config " + id, e);
                    }
                }
            });
            store.syncLegacy();
        }
        return store;
    }

    /** The address of the multiplayer server the player is on, or null (single player, main menu). */
    public static String currentServer() {
        class_310 mc = class_310.method_1551();
        if (mc.method_1496()) {
            return null;
        }
        class_642 info = mc.method_1558();
        return info == null ? null : info.field_3761;
    }

    /** Listens for the join packet and loads the config that belongs to the server. Called once at start. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        PacketBus.applied(packet -> {
            if (packet instanceof class_2678) {
                try {
                    onJoin();
                } catch (Throwable t) {
                    DIHClient.LOG.warn("[DIHClient] config auto-load failed", t);
                }
            }
        });
    }

    private static void onJoin() {
        String address = currentServer();
        if (address == null) {
            return;
        }
        ConfigStore.Entry entry = store().forServer(address);
        if (entry == null) {
            return;
        }
        boolean ok = store().apply(entry);
        Notifications.push("Configs", ok ? "Loaded \"" + entry.name + "\" for " + ConfigStore.normalize(address) : "Config \"" + entry.name + "\" could not be loaded",
                ok ? Notifications.Type.SUCCESS : Notifications.Type.WARNING);
    }
}
