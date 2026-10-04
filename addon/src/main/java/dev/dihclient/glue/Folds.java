package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.gui.SchematicBrowserScreen;
import dev.dihclient.merge.ConfigMigration;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.automation.FlipFinder;
import dev.dihclient.modules.automation.StashSorter;
import dev.dihclient.modules.basefinding.BlockNotifier;
import dev.dihclient.modules.misc.AutoLog;
import dev.dihclient.modules.movement.Blink;
import dev.dihclient.modules.render.BlockEsp;
import dev.dihclient.modules.render.StorageEsp;
import dev.dihclient.port.donuta.AutoRelog;
import dev.dihclient.port.donutc.AirStuck;
import dev.dihclient.modules.movement.ElytraBot;
import dev.dihclient.modules.player.AutoArmor;
import dev.dihclient.modules.player.AutoMend;
import dev.dihclient.modules.player.AutoRestock;
import dev.dihclient.modules.world.AutoBuild;
import dev.dihclient.modules.world.AutoMine;
import dev.dihclient.modules.world.Goto;
import dev.dihclient.modules.world.MapArt;
import dev.dihclient.modules.world.SafeRoute;
import dev.dihclient.modules.world.Scaffold;
import dev.dihclient.modules.world.SmartBridge;
import dev.dihclient.modules.world.Tunnel;
import dev.dihclient.setting.ActionSetting;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.StringSetting;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * The module clean-up of 6.7.0:
 * <ul>
 *   <li>AutoMine + Tunnel → <b>Miner</b></li>
 *   <li>Goto + ElytraBot (+ SafeRoute as a switch) → <b>Goto</b></li>
 *   <li>Scaffold + SmartBridge → <b>Scaffold</b></li>
 *   <li>AutoMend → a switch "Mend" in AutoArmor</li>
 *   <li>MapArt, ModelBuild, Demolish and AutoRestock → options of <b>AutoBuild</b> (Source: Schematic / MapArt / Model / Demolish)</li>
 *   <li>StashSorter disappears from the lists (TaskQueue still uses its engine), AutoTrade is removed (see Cleanup), FlipFinder is now AutoFlipper</li>
 * </ul>
 */
public final class Folds {
    private Folds() {
    }

    /** What AutoBuild builds from. */
    public enum Source { SCHEMATIC, MAPART, MODEL, DEMOLISH }

    private static void add(ModuleManager modules, Module m) throws ReflectiveOperationException {
        Method add = ModuleManager.class.getDeclaredMethod("add", Module.class);
        add.setAccessible(true);
        add.invoke(modules, m);
    }

    public static void apply(ModuleManager modules) throws ReflectiveOperationException {
        // the engine that keeps the folded modules running
        MergeBus bus = new MergeBus();
        add(modules, bus);
        bus.setEnabledSilently(true);

        AutoMine mine = modules.get(AutoMine.class);
        Tunnel tunnel = modules.get(Tunnel.class);
        Goto walk = modules.get(Goto.class);
        ElytraBot fly = modules.get(ElytraBot.class);
        SafeRoute safe = modules.get(SafeRoute.class);
        Scaffold scaffold = modules.get(Scaffold.class);
        SmartBridge smart = modules.get(SmartBridge.class);

        step("Miner", () -> {
            Hubs.Miner miner = new Hubs.Miner(mine, tunnel);
            Merge.hide(modules, mine);
            Merge.hide(modules, tunnel);
            add(modules, miner);
            Merge.alias(modules, "miner", miner);
        });
        step("Goto", () -> {
            Hubs.GotoHub hub = new Hubs.GotoHub(walk, fly, safe);
            Merge.hide(modules, walk);
            Merge.hide(modules, fly);
            Merge.hide(modules, safe);
            add(modules, hub); // same name as the old Goto: the saved settings of Goto are the settings of the hub
        });
        step("Scaffold", () -> {
            Hubs.ScaffoldHub hub = new Hubs.ScaffoldHub(scaffold, smart);
            Merge.hide(modules, scaffold);
            Merge.hide(modules, smart);
            add(modules, hub);
        });
        step("AutoArmor + Mend", () -> mend(modules));
        step("AutoBuild options", () -> build(modules));
        step("StashSorter", () -> Merge.hide(modules, modules.get(StashSorter.class)));
        step("Blink + Air Stuck", () -> {
            Blink blink = modules.get(Blink.class);
            AirStuck freeze = modules.get(AirStuck.class);
            Hubs.BlinkHub hub = new Hubs.BlinkHub(blink, freeze);
            Merge.hide(modules, blink);
            Merge.hide(modules, freeze);
            add(modules, hub); // same name as Blink
        });
        step("Block ESP + Storage ESP + Block Notifier", () -> blockEsp(modules));
        step("Amethyst Bypass in Sus ChunkFinder", () -> amethyst(modules));
        step("Auto Log + Auto Relog", () -> autoLog(modules));
        step("AutoFlipper", () -> {
            FlipFinder flip = modules.get(FlipFinder.class);
            Merge.alias(modules, "flipfinder", flip);
            Merge.rule(ConfigMigration.Rule.rename("flipfinder", flip.id()));
        });
    }

    private interface Action {
        void run() throws Exception;
    }

    private static void step(String what, Action a) {
        try {
            a.run();
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] fold step failed: " + what, t);
        }
    }

    private static void mend(ModuleManager modules) throws ReflectiveOperationException {
        AutoArmor armor = modules.get(AutoArmor.class);
        AutoMend mend = modules.get(AutoMend.class);
        BoolSetting on = new BoolSetting("Mend", "Also repairs Mending tools and armour with XP bottles: swaps them through your offhand one after another.", false);
        Merge.addSetting(armor, on, false);
        Map<String, String> m = Merge.absorb(armor, mend, "Mend", on::get);
        Merge.rule(new ConfigMigration.Rule(mend.id(), armor.id(), m, "mend", null, null, false, false));
        Merge.link(armor, on::get, mend, false);
        Merge.hide(modules, mend);
    }

    private static void build(ModuleManager modules) throws ReflectiveOperationException {
        AutoBuild build = modules.get(AutoBuild.class);
        MapArt mapArt = modules.get(MapArt.class);
        AutoRestock restock = modules.get(AutoRestock.class);
        ModelBuild model = new ModelBuild();
        Demolish demolish = new Demolish();
        add(modules, model);
        add(modules, demolish);

        EnumSetting<Source> source = new EnumSetting<>("Source",
                "Schematic: build a file · MapArt: turn a picture into map art · Model: turn a 3D model into blocks · Demolish: take down the loaded build.", Source.SCHEMATIC);
        BoolSetting refill = new BoolSetting("Hotbar Refill",
                "Refills the hotbar from your inventory and fetches low items from nearby chests (the old AutoRestock).", false);
        // everything AutoBuild had belongs to "Schematic"
        Merge.gateAll(build, () -> source.get() == Source.SCHEMATIC);
        Merge.addSetting(build, source, true);
        Merge.addSetting(build, refill, false);
        refill.visibleWhen(() -> source.get() == Source.SCHEMATIC);

        Map<String, String> r = Merge.absorb(build, restock, "Refill", () -> refill.get() && source.get() == Source.SCHEMATIC);
        Map<String, String> a = Merge.absorb(build, mapArt, "Map", () -> source.get() == Source.MAPART);
        Map<String, String> b = Merge.absorb(build, model, "Model", () -> source.get() == Source.MODEL);
        Map<String, String> d = Merge.absorb(build, demolish, "Demolish", () -> source.get() == Source.DEMOLISH);
        ActionSetting start = new ActionSetting("Start Demolish", "Takes down the build that is loaded in AutoBuild (asks once before it starts).", () -> demolish.setEnabled(true));
        start.visibleWhen(() -> source.get() == Source.DEMOLISH);
        Merge.addSetting(build, start, false);

        Merge.rule(new ConfigMigration.Rule(restock.id(), build.id(), r, "hotbar_refill", null, null, false, false));
        Merge.rule(new ConfigMigration.Rule(mapArt.id(), build.id(), a, null, null, null, false, false));
        Merge.rule(new ConfigMigration.Rule(model.id(), build.id(), b, null, null, null, false, false));
        Merge.link(build, refill::get, restock, false);

        Merge.hide(modules, restock);
        Merge.hide(modules, mapArt);
        Merge.hide(modules, model);
        Merge.hide(modules, demolish);

        // plan the whole route before the build, and keep the module menu short (the rest lives in the browser)
        BoolSetting planFirst = new BoolSetting("Plan First",
                "Before a build starts, works out the whole route (all layers, the fewest stand spots, the shortest way between them) and then follows it.", true);
        Merge.addSetting(build, planFirst, false);
        BuildPlannerModule planner = new BuildPlannerModule(build, planFirst);
        add(modules, planner);
        planner.setEnabledSilently(true);
        Merge.addSetting(build, new ActionSetting("Plan Route",
                "Works out the route of the loaded build now and draws it in the world (blue, purple, pink ... per layer). Also shows how long it will take.", planner::planNow), false);
        Merge.addSetting(build, new ActionSetting("More Options", "Opens the schematic browser on its OPTIONS tab: all other AutoBuild options are there.",
                () -> {
                    net.minecraft.class_310 mc = net.minecraft.class_310.method_1551();
                    mc.execute(() -> SchematicBrowserScreen.openOptions(mc.field_1755, build));
                }), false);
        // team codes: build together with the alt accounts
        StringSetting teamCode = new StringSetting("Team Code", "The code of a team. Create Team shows it; type it here on the other accounts and press Join Team.", "", 16);
        Merge.addSetting(build, teamCode, false);
        TeamBuild team = new TeamBuild(build, teamCode);
        add(modules, team);
        team.setEnabledSilently(true);
        Merge.addSetting(build, new ActionSetting("Create Team", "Makes a team of the build that is loaded (pick a schematic in the browser first) and gives a code for your alt accounts.", team::create), false);
        Merge.addSetting(build, new ActionSetting("Join Team", "Builds with the team whose code is in Team Code, at the same place and the same schematic.", team::join), false);
        Merge.addSetting(build, new ActionSetting("Leave Team", "Leaves the team (the build goes on alone).", team::leave), false);
        AdvancedOptions.apply(build, AdvancedOptions.AUTOBUILD_MAIN);
    }

    /**
     * Amethyst Bypass is no module of its own any more: it is the "Amethyst" part of Sus ChunkFinder (the switch keeps its name).
     * Its settings (Method, Min Cells, Tracer) show there while Amethyst is on, and its finds count as amethyst points.
     */
    private static void amethyst(ModuleManager modules) throws ReflectiveOperationException {
        dev.dihclient.modules.basefinding.SusChunkFinder sus = modules.get(dev.dihclient.modules.basefinding.SusChunkFinder.class);
        dev.dihclient.port.donutd.AmethystBypassModule bypass = modules.get(dev.dihclient.port.donutd.AmethystBypassModule.class);
        if (sus == null || bypass == null) {
            return;
        }
        Map<String, String> renamed = Merge.absorb(sus, bypass, "Amethyst", sus.amethyst::get);
        Merge.hide(modules, bypass);
        Merge.rule(new ConfigMigration.Rule(bypass.id(), sus.id(), renamed, null, null, null, false, false));
        Merge.link(sus, sus.amethyst::get, bypass, false);
        dev.dihclient.modules.basefinding.SusChunkFinder.amethystSource = () -> bypass.isEnabled() ? bypass.perChunk() : null;
    }

    private static void blockEsp(ModuleManager modules) throws ReflectiveOperationException {
        BlockEsp esp = modules.get(BlockEsp.class);
        StorageEsp storage = modules.get(StorageEsp.class);
        BlockNotifier notifier = modules.get(BlockNotifier.class);
        BoolSetting storageOn = new BoolSetting("Storage", "Also highlights chests, barrels, shulkers and other containers (the old Storage ESP).", false);
        BoolSetting notifyOn = new BoolSetting("Notify", "Tells you once when selected blocks or entity types show up (the old Block Notifier).", false);
        Merge.addSetting(esp, storageOn, false);
        Map<String, String> s = Merge.absorb(esp, storage, "Storage", storageOn::get);
        Merge.addSetting(esp, notifyOn, false);
        Map<String, String> n = Merge.absorb(esp, notifier, "Notify", notifyOn::get);
        Merge.rule(new ConfigMigration.Rule(storage.id(), esp.id(), s, "storage", null, null, false, false));
        Merge.rule(new ConfigMigration.Rule(notifier.id(), esp.id(), n, "notify", null, null, false, false));
        Merge.link(esp, storageOn::get, storage, false);
        Merge.link(esp, notifyOn::get, notifier, false);
        Merge.hide(modules, storage);
        Merge.hide(modules, notifier);
    }

    private static void autoLog(ModuleManager modules) throws ReflectiveOperationException {
        AutoLog log = modules.get(AutoLog.class);
        AutoRelog relog = modules.get(AutoRelog.class);
        BoolSetting on = new BoolSetting("Relog", "Also leaves and rejoins once when you go below a Y level (the old Auto Relog), for servers that reset something at a certain depth.", false);
        Merge.addSetting(log, on, false);
        Map<String, String> m = Merge.absorb(log, relog, "Relog", on::get);
        Merge.rule(new ConfigMigration.Rule(relog.id(), log.id(), m, "relog", null, null, false, false));
        Merge.link(log, on::get, relog, false);
        Merge.hide(modules, relog);
    }
}
