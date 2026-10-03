package dev.dihclient.glue;

import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.Performance;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Takes modules out of the client and adds the small settings that did not exist: the modules that were marked useless
 * or double are removed from the module list (so they are no longer in the GUI, are not ticked, rendered, saved or bound
 * to keys); the instance stays reachable for the few places in the code that look it up by class and then ask whether it is on.
 */
public final class Cleanup {
    /** Simple class names of the removed modules. */
    static final Set<String> REMOVED = Set.of(
            "AutoPot", "MaceCombo", "Step", "ReverseStep", "AutoReconnect", "InvManager", "SurvivalAlerts", "MoneyHud",
            "Xray", "Chams", "DamageNumbers", "Waypoints", "DihChat", "Friends", "Enemies", "NewChunks", "BaseTraces",
            "Watchlist", "PacketBuffer", "PacketFly", "HammerTool", "Fullbright", "SafeWalk", "BoatNoClip");

    /** Removed by display name (several modules share a class, like the hotbar macros). Glowstone Macro was only Anchor Macro again. */
    static final Set<String> REMOVED_NAMES = Set.of("Glowstone Macro");

    private static boolean gone(Module m) {
        return REMOVED.contains(m.getClass().getSimpleName()) || REMOVED_NAMES.contains(m.name());
    }

    /** Light / normal / heavy for the Performance module. */
    public enum Quality { LIGHT, NORMAL, HEAVY }

    private Cleanup() {
    }

    @SuppressWarnings("unchecked")
    public static void removeModules(ModuleManager manager) throws ReflectiveOperationException {
        Field modulesField = ModuleManager.class.getDeclaredField("modules");
        modulesField.setAccessible(true);
        List<Module> modules = (List<Module>) modulesField.get(manager);
        modules.removeIf(Cleanup::gone);
        Field namesField = ModuleManager.class.getDeclaredField("byName");
        namesField.setAccessible(true);
        Map<String, Module> byName = (Map<String, Module>) namesField.get(manager);
        byName.values().removeIf(Cleanup::gone);
    }

    /** PacketFly lives on as a hidden helper of Flight: its settings are added to Flight and only show in Packet mode. */
    @SuppressWarnings("unchecked")
    public static void mergePacketFly(dev.dihclient.modules.movement.Flight flight, dev.dihclient.modules.movement.PacketFly pf)
            throws ReflectiveOperationException {
        Method add = Module.class.getDeclaredMethod("add", dev.dihclient.setting.Setting.class);
        add.setAccessible(true);
        for (dev.dihclient.setting.Setting<?> s : List.copyOf(pf.settings())) {
            s.visibleWhen(() -> flight.mode.get() == dev.dihclient.modules.movement.Flight.Mode.PACKET);
            add.invoke(flight, s);
        }
    }

    /**
     * The 3x3 pickaxe is no module any more: every automation module with a "3x3 Pickaxe" switch gets the tool-name field
     * itself (it is the same setting everywhere, so it only has to be typed once).
     */
    public static void linkHammer(ModuleManager manager) throws ReflectiveOperationException {
        dev.dihclient.modules.player.HammerTool tool = manager.get(dev.dihclient.modules.player.HammerTool.class);
        Method add = Module.class.getDeclaredMethod("add", dev.dihclient.setting.Setting.class);
        add.setAccessible(true);
        java.util.List<dev.dihclient.setting.BoolSetting> switches = new java.util.ArrayList<>();
        java.util.List<Module> owners = new java.util.ArrayList<>();
        for (Module m : manager.all()) {
            try {
                Object field = m.getClass().getField("hammer").get(m);
                if (field instanceof dev.dihclient.setting.BoolSetting hammer) {
                    switches.add(hammer);
                    owners.add(m);
                }
            } catch (NoSuchFieldException ignored) {
                // this module has no 3x3 switch
            }
        }
        // one shared setting, shown wherever any of the 3x3 switches is on
        tool.names.visibleWhen(() -> switches.stream().anyMatch(dev.dihclient.setting.BoolSetting::get));
        for (Module m : owners) {
            add.invoke(m, tool.names);
        }
    }

    /** Performance gets one switch instead of four separate sliders and toggles. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void performance(Performance p) throws ReflectiveOperationException {
        Method mode = Module.class.getDeclaredMethod("mode", String.class, String.class, Enum.class);
        mode.setAccessible(true);
        dev.dihclient.setting.EnumSetting quality = (dev.dihclient.setting.EnumSetting) mode.invoke(p, "Quality",
                "Light: little lag, simple effects. Normal: the default. Heavy: everything on, scans chunks faster.", Quality.NORMAL);
        quality.onChange(() -> {
            Quality q = (Quality) quality.get();
            switch (q) {
                case LIGHT -> apply(p, true, 0.5, Performance.RenderMode.SAFE, false);
                case HEAVY -> apply(p, false, 6.0, Performance.RenderMode.FULL, true);
                default -> apply(p, false, 2.0, Performance.RenderMode.AUTO, false);
            }
        });
        Collection<dev.dihclient.setting.Setting<?>> old = List.of(p.renderMode, p.scanBudget, p.lowDetail, p.markerParticles);
        for (dev.dihclient.setting.Setting<?> s : old) {
            s.visibleWhen(() -> false);
        }
    }

    private static void apply(Performance p, boolean low, double budget, Performance.RenderMode render, boolean particles) {
        p.lowDetail.set(low);
        p.scanBudget.set(budget);
        p.renderMode.set(render);
        p.markerParticles.set(particles);
    }
}
