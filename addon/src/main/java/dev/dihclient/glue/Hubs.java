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
import dev.dihclient.modules.fun.ModelReplacer;
import dev.dihclient.modules.movement.NoFall;
import dev.dihclient.modules.player.PacketMine;
import dev.dihclient.port.donuta.DonutNoFall;
import dev.dihclient.port.donuta.DonutSpeedMine;
import dev.dihclient.port.tools.FastBreak;
import dev.dihclient.port.trident.RiptideModule;
import dev.dihclient.port.trident.TridentBoostModule;
import dev.dihclient.port.trident.TridentUtilModule;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class Hubs {
    private Hubs() {
    }

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

    public static final class NoFallHub extends Hub {
        public enum Mode { NORMAL, DONUT }

        public final EnumSetting<Mode> mode = this.mode("Mode", "Normal: spoofs the on-ground flag while you fall · Donut: the method made for DonutSMP.", Mode.NORMAL);

        public NoFallHub(NoFall normal, DonutNoFall donut) throws ReflectiveOperationException {
            super("NoFall", Category.MOVEMENT, "No fall damage: Normal or the DonutSMP method.");
            this.active = () -> this.mode.get() == Mode.NORMAL ? normal : donut;
            Merge.absorb(this, normal, "Normal", () -> this.mode.get() == Mode.NORMAL);
            Map<String, String> d = Merge.absorb(this, donut, "Donut", () -> this.mode.get() == Mode.DONUT);
            Merge.rule(new ConfigMigration.Rule(donut.id(), this.id(), d, null, "mode", "DONUT", true, false));
            Merge.link(this, () -> this.mode.get() == Mode.NORMAL, normal, true);
            Merge.link(this, () -> this.mode.get() == Mode.DONUT, donut, true);
        }
    }

    public static final class FastBreakHub extends Hub {
        public enum Engine { FAST, DONUT, PACKET }

        public final EnumSetting<Engine> engine = this.mode("Engine",
                "Fast: faster breaking with its own modes · Donut: finishes blocks early (made for DonutSMP) · Packet: keeps mining the block you started even when you look away.",
                Engine.FAST);

        public FastBreakHub(FastBreak fast, DonutSpeedMine donut, PacketMine packet) throws ReflectiveOperationException {
            super("Fast Break", Category.WORLD, "Breaks blocks faster: Fast, Donut Speed Mine or Packet Mine in one module.");
            this.active = () -> switch (this.engine.get()) {
                case FAST -> fast;
                case DONUT -> donut;
                case PACKET -> packet;
            };
            Merge.absorb(this, fast, "Fast", () -> this.engine.get() == Engine.FAST);
            Map<String, String> d = Merge.absorb(this, donut, "Donut", () -> this.engine.get() == Engine.DONUT);
            Map<String, String> p = Merge.absorb(this, packet, "Packet", () -> this.engine.get() == Engine.PACKET);
            Merge.rule(new ConfigMigration.Rule(donut.id(), this.id(), d, null, "engine", "DONUT", true, false));
            Merge.rule(new ConfigMigration.Rule(packet.id(), this.id(), p, null, "engine", "PACKET", true, false));
            Merge.link(this, () -> this.engine.get() == Engine.FAST, fast, true);
            Merge.link(this, () -> this.engine.get() == Engine.DONUT, donut, true);
            Merge.link(this, () -> this.engine.get() == Engine.PACKET, packet, true);
        }
    }

    public static final class TridentHub extends Hub {
        public enum Mode { BOOST, UTIL, RIPTIDE }

        public final EnumSetting<Mode> mode = this.mode("Mode",
                "Boost: more speed when you use riptide · Util: tridents out of water and faster charging · Riptide: uses a riptide trident while you hold right-click.",
                Mode.BOOST);

        public TridentHub(TridentBoostModule boost, TridentUtilModule util, RiptideModule riptide) throws ReflectiveOperationException {
            super("Trident", Category.DONUT, "Everything for tridents in one module: Boost, Util or Riptide.");
            this.active = () -> switch (this.mode.get()) {
                case BOOST -> boost;
                case UTIL -> util;
                case RIPTIDE -> riptide;
            };
            Map<String, String> b = Merge.absorb(this, boost, "Boost", () -> this.mode.get() == Mode.BOOST);
            Map<String, String> u = Merge.absorb(this, util, "Util", () -> this.mode.get() == Mode.UTIL);
            Map<String, String> r = Merge.absorb(this, riptide, "Riptide", () -> this.mode.get() == Mode.RIPTIDE);
            Merge.rule(new ConfigMigration.Rule(boost.id(), this.id(), b, null, "mode", "BOOST", true, false));
            Merge.rule(new ConfigMigration.Rule(util.id(), this.id(), u, null, "mode", "UTIL", true, false));
            Merge.rule(new ConfigMigration.Rule(riptide.id(), this.id(), r, null, "mode", "RIPTIDE", true, false));
            Merge.link(this, () -> this.mode.get() == Mode.BOOST, boost, true);
            Merge.link(this, () -> this.mode.get() == Mode.UTIL, util, true);
            Merge.link(this, () -> this.mode.get() == Mode.RIPTIDE, riptide, true);
        }
    }

    public static final class ModelHub extends Hub {
        public enum Type { CUSTOM, MOBS }

        public final EnumSetting<Type> type = this.mode("Type",
                "Custom: your own 3D model (.glb / .obj) instead of players · Mobs: players and mobs look like other mobs (the old Model Replacer).", Type.CUSTOM);

        public ModelHub(CustomModel custom, ModelReplacer mobs) throws ReflectiveOperationException {
            super("CustomModel", Category.FUN, "Changes how players look: your own 3D model or other mobs. Only you see it.");
            this.active = () -> this.type.get() == Type.CUSTOM ? custom : mobs;
            Merge.absorb(this, custom, "Custom", () -> this.type.get() == Type.CUSTOM);
            Map<String, String> m = Merge.absorb(this, mobs, "Mobs", () -> this.type.get() == Type.MOBS);
            Merge.rule(new ConfigMigration.Rule(mobs.id(), this.id(), m, null, "type", "MOBS", true, false));
            Merge.link(this, () -> this.type.get() == Type.CUSTOM, custom, true);
            Merge.link(this, () -> this.type.get() == Type.MOBS, mobs, true);
        }
    }

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
