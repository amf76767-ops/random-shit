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
            "Watchlist", "PacketBuffer");

    /** Light / normal / heavy for the Performance module. */
    public enum Quality { LIGHT, NORMAL, HEAVY }

    private Cleanup() {
    }

    @SuppressWarnings("unchecked")
    public static void removeModules(ModuleManager manager) throws ReflectiveOperationException {
        Field modulesField = ModuleManager.class.getDeclaredField("modules");
        modulesField.setAccessible(true);
        List<Module> modules = (List<Module>) modulesField.get(manager);
        modules.removeIf(m -> REMOVED.contains(m.getClass().getSimpleName()));
        Field namesField = ModuleManager.class.getDeclaredField("byName");
        namesField.setAccessible(true);
        Map<String, Module> byName = (Map<String, Module>) namesField.get(manager);
        byName.values().removeIf(m -> REMOVED.contains(m.getClass().getSimpleName()));
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
