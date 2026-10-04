package dev.dihclient.glue;

import dev.dihclient.merge.ConfigMigration;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.modules.movement.Blink;
import dev.dihclient.modules.movement.ElytraBot;
import dev.dihclient.modules.world.AutoMine;
import dev.dihclient.modules.world.Goto;
import dev.dihclient.modules.world.SafeRoute;
import dev.dihclient.modules.world.Scaffold;
import dev.dihclient.modules.world.SmartBridge;
import dev.dihclient.modules.world.Tunnel;
import dev.dihclient.port.donutc.AirStuck;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** The modules that several old modules were folded into. Each one shows the settings of the part that is selected. */
public final class Hubs {
    private Hubs() {
    }

    /** A module that stands for one of its parts. */
    public static class Hub extends Module {
        Supplier<Module> active = () -> null;

        Hub(String name, Category category, String description) {
            super(name, category, description);
        }

        @Override
        public String getInfo() {
            Module m = this.active.get();
            return m == null ? null : m.getInfo();
        }

        @Override
        public List<String> details() {
            Module m = this.active.get();
            return m == null ? super.details() : m.details();
        }
    }

    /** AutoMine + Tunnel. */
    public static final class Miner extends Hub {
        public enum Mode { ORES, TUNNEL }

        public final EnumSetting<Mode> mode = this.mode("Mode", "Ores: finds ores near you and mines the whole vein · Tunnel: digs a tunnel where you look.", Mode.ORES);

        public Miner(AutoMine ore, Tunnel tunnel) throws ReflectiveOperationException {
            super("Miner", Category.AUTOMATION, "Mines for you: ore veins (AutoMine) or a straight tunnel (Tunnel).");
            this.active = () -> this.mode.get() == Mode.ORES ? ore : tunnel;
            Map<String, String> a = Merge.absorb(this, ore, "Ore", () -> this.mode.get() == Mode.ORES);
            Map<String, String> t = Merge.absorb(this, tunnel, "Tunnel", () -> this.mode.get() == Mode.TUNNEL);
            Merge.rule(new ConfigMigration.Rule(ore.id(), this.id(), a, null, "mode", "ORES", true, false));
            Merge.rule(new ConfigMigration.Rule(tunnel.id(), this.id(), t, null, "mode", "TUNNEL", true, false));
            Merge.link(this, () -> this.mode.get() == Mode.ORES, ore, true);
            Merge.link(this, () -> this.mode.get() == Mode.TUNNEL, tunnel, true);
        }
    }

    /** Goto + ElytraBot, with SafeRoute as an option. Keeps the name Goto, so the saved settings stay where they were. */
    public static final class GotoHub extends Hub {
        public enum Mode { WALK, ELYTRA }

        public final EnumSetting<Mode> mode = this.mode("Mode", "Walk: pathfinding on foot, digging and bridging · Elytra: flies there with rockets.", Mode.WALK);
        public final BoolSetting safeRoute = this.bool("Safe Route",
                "Safest instead of shortest: Goto and all bots avoid mobs, darkness, lava, deep drops and water.", false);

        public GotoHub(Goto walk, ElytraBot fly, SafeRoute safe) throws ReflectiveOperationException {
            super("Goto", Category.AUTOMATION, "Takes you to coordinates by itself: on foot (pathfinding) or by elytra. Safe Route makes the way safer.");
            this.active = () -> this.mode.get() == Mode.WALK ? walk : fly;
            Merge.absorb(this, walk, "Walk", () -> this.mode.get() == Mode.WALK);
            Map<String, String> f = Merge.absorb(this, fly, "Elytra", () -> this.mode.get() == Mode.ELYTRA);
            Map<String, String> s = Merge.absorb(this, safe, "Safe", this.safeRoute::get);
            Merge.rule(new ConfigMigration.Rule(fly.id(), this.id(), f, null, "mode", "ELYTRA", true, false));
            Merge.rule(new ConfigMigration.Rule(safe.id(), this.id(), s, "safe_route", null, null, false, false));
            Merge.link(this, () -> this.mode.get() == Mode.WALK, walk, true);
            Merge.link(this, () -> this.mode.get() == Mode.ELYTRA, fly, true);
            Merge.link(this, this.safeRoute::get, safe, false);
        }
    }

    /** Scaffold + SmartBridge. Keeps the name Scaffold. */
    public static final class ScaffoldHub extends Hub {
        public enum Mode { CLASSIC, SMART }

        public final EnumSetting<Mode> mode = this.mode("Mode", "Classic: places blocks under you · Smart: looks ahead along your path and has a human mode.", Mode.CLASSIC);

        public ScaffoldHub(Scaffold classic, SmartBridge smart) throws ReflectiveOperationException {
            super("Scaffold", classic.category(), "Places blocks under you while you walk: Classic or Smart (lookahead, human mode).");
            this.active = () -> this.mode.get() == Mode.CLASSIC ? classic : smart;
            Merge.absorb(this, classic, "Classic", () -> this.mode.get() == Mode.CLASSIC);
            Map<String, String> s = Merge.absorb(this, smart, "Smart", () -> this.mode.get() == Mode.SMART);
            Merge.rule(new ConfigMigration.Rule(smart.id(), this.id(), s, null, "mode", "SMART", true, false));
            Merge.link(this, () -> this.mode.get() == Mode.CLASSIC, classic, true);
            Merge.link(this, () -> this.mode.get() == Mode.SMART, smart, true);
        }
    }

    /** Blink + Air Stuck. Keeps the name Blink, so the saved settings of Blink stay where they were. */
    public static final class BlinkHub extends Hub {
        public enum Type { BLINK, FREEZE }

        public final EnumSetting<Type> type = this.mode("Type",
                "Blink: holds your movement packets and releases them later · Freeze: you stay where you are for the server, even mid-air (the old Air Stuck).", Type.BLINK);

        public BlinkHub(Blink blink, AirStuck freeze) throws ReflectiveOperationException {
            super("Blink", blink.category(), "Holds your movement packets (Blink) or freezes you in place, even mid-air (Freeze).");
            this.active = () -> this.type.get() == Type.BLINK ? blink : freeze;
            Merge.absorb(this, blink, "Blink", () -> this.type.get() == Type.BLINK);
            Merge.rule(new ConfigMigration.Rule(freeze.id(), this.id(), Map.of(), null, "type", "FREEZE", true, false));
            Merge.link(this, () -> this.type.get() == Type.BLINK, blink, true);
            Merge.link(this, () -> this.type.get() == Type.FREEZE, freeze, true);
        }
    }
}
