package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.merge.ConfigMigration;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.automation.FlipFinder;
import dev.dihclient.modules.automation.StashSorter;
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
    }
}
