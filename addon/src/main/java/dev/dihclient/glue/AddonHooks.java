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

    /** One step of the module set-up; a failing step is logged and does not stop the others. */
    private interface Step {
        void run() throws Throwable;
    }

    private static void step(String what, Step step) {
        try {
            step.run();
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] set-up step failed: " + what, t);
        }
    }

    /** Called at the end of {@code ModuleManager.registerAll}. */
    public static void registerModules(ModuleManager modules) {
        step("add modules", () -> {
            Method add = ModuleManager.class.getDeclaredMethod("add", dev.dihclient.module.Module.class);
            add.setAccessible(true);
            AutomationSupervisor supervisor = new AutomationSupervisor();
            add.invoke(modules, supervisor);
            // on by default; the saved config, loaded right after this, wins as soon as the player has switched it off
            supervisor.setEnabledSilently(true);
            add.invoke(modules, new VisualPack());
            add.invoke(modules, new CustomModel());
            add.invoke(modules, new FakeTime());
            add.invoke(modules, new Scenes());
            Emotes emotes = new Emotes();
            add.invoke(modules, emotes);
            emotes.setEnabledSilently(true); // idle until a key is pressed
            BuildPath path = new BuildPath();
            add.invoke(modules, path);
            path.setEnabledSilently(true); // harmless: draws only while AutoBuild walks
            // modules ported from the Anubis client; Player Bypass takes the place of the old module of the same name
            for (dev.dihclient.module.Module m : new dev.dihclient.module.Module[]{
                    new dev.dihclient.port.crystal.CrystalAuraModule(), new dev.dihclient.port.crystal.CrystalOptimizerModule(),
                    new dev.dihclient.port.tools.SpearSwap(), new dev.dihclient.port.tools.SpawnerProtect(),
                    new dev.dihclient.port.tools.FastXp(), new dev.dihclient.port.tools.HoverTotem(),
                    new dev.dihclient.port.chunks.PlayerBypass(), new dev.dihclient.port.chunks.PrimeChunkFinder(),
                    new dev.dihclient.port.vanish.AntiVanish(), new dev.dihclient.port.staff.StaffList(), new dev.dihclient.port.noinvleak.NoInvLeakModule(),
                    new dev.dihclient.port.discord.DiscordPresence(),
                    new dev.dihclient.port.spotify.SpotifyHudModule()}) {
                add.invoke(modules, m);
            }
        });
        step("3x3 pickaxe name", () -> Cleanup.linkHammer(modules));
        step("PacketFly in Flight", () -> Cleanup.mergePacketFly(modules.get(dev.dihclient.modules.movement.Flight.class),
                modules.get(dev.dihclient.modules.movement.PacketFly.class)));
        step("Performance quality", () -> Cleanup.performance(modules.get(dev.dihclient.modules.client.Performance.class)));
        step("remove modules", () -> Cleanup.removeModules(modules));
        step("legal AutoBuild", () -> {
            dev.dihclient.modules.world.AutoBuild build = modules.get(dev.dihclient.modules.world.AutoBuild.class);
            if (build != null) {
                LegalPlace.prepare(build);
            }
        });
        step("fold modules", () -> Folds.apply(modules));
        step("config dock", dev.dihclient.port.configs.Configs::install);
    }

    /** Called at the start of every client tick. */
    public static void tick(class_310 mc) {
        try {
            AutomationSupervisor.creativeGuard();
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] creative guard failed", t);
        }
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
