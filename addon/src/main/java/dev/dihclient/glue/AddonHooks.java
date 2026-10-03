package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.update.GithubReleases;
import dev.dihclient.update.UpdateManager;
import dev.dihclient.util.Notifications;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_310;
import net.minecraft.class_442;

/**
 * The few entry points the patched {@code DIHClient} and {@code ModuleManager} call. Every one of them
 * swallows its own failures: an update check or the supervisor must never be able to crash the game.
 */
public final class AddonHooks {
    private static boolean toasted;

    private AddonHooks() {
    }

    /** Called at the end of {@code DIHClient.onInitializeClient}. */
    public static void init() {
        try {
            UpdateManager.init(FabricLoader.getInstance().getConfigDir().resolve("dihclient"));
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] update check could not start", t);
        }
    }

    /** Called at the end of {@code ModuleManager.registerAll}. */
    public static void registerModules(ModuleManager modules) {
        try {
            Method add = ModuleManager.class.getDeclaredMethod("add", dev.dihclient.module.Module.class);
            add.setAccessible(true);
            AutomationSupervisor supervisor = new AutomationSupervisor();
            add.invoke(modules, supervisor);
            // on by default; the saved config, loaded right after this, wins as soon as the player has switched it off
            supervisor.setEnabledSilently(true);
            add.invoke(modules, new VisualPack());
            dev.dihclient.modules.world.AutoBuild build = modules.get(dev.dihclient.modules.world.AutoBuild.class);
            if (build != null) {
                LegalPlace.prepare(build);
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] AutoSupervisor could not be registered", t);
        }
    }

    /** Called at the start of every client tick. */
    public static void tick(class_310 mc) {
        try {
            UpdateManager.tick();
            if (UpdateManager.state() != UpdateManager.State.AVAILABLE && UpdateManager.state() != UpdateManager.State.FAILED) {
                return;
            }
            if (mc.field_1755 instanceof class_442) {
                if (UpdateManager.shouldPrompt()) {
                    mc.method_1507(new UpdateScreen(mc.field_1755));
                }
            } else if (!toasted && mc.field_1724 != null && UpdateManager.release() != null) {
                toasted = true;
                GithubReleases.Release r = UpdateManager.release();
                Notifications.info("DIHClient", "Version " + r.version().display() + " is available. You will be asked on the title screen.");
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] update hook failed", t);
        }
    }
}
