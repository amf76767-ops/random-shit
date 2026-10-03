package dev.dihclient.glue;

import com.google.gson.JsonObject;
import dev.dihclient.DIHClient;
import dev.dihclient.merge.ConfigMigration;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.Setting;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Folds modules into other modules without touching their code. A folded module (the "guest") leaves the module list,
 * so it is no longer in the GUI, the config or the key bindings, but it stays alive: its settings move to the module that
 * took it in (the "host", renamed when the names clash), and the {@link MergeBus} keeps ticking and drawing it while it is on.
 * Whether the guest is on follows the host through a {@link Link}.
 */
public final class Merge {
    /** The guest runs while the host is on and {@code want} is true. Only changes are acted on, so a pause by the supervisor holds. */
    private static final class Link {
        final Module host;
        final BooleanSupplier want;
        final Module guest;
        final boolean follow;
        boolean last;
        int offTicks;

        Link(Module host, BooleanSupplier want, Module guest, boolean follow) {
            this.host = host;
            this.want = want;
            this.guest = guest;
            this.follow = follow;
        }
    }

    private static final List<Link> LINKS = new ArrayList<>();
    private static final List<Module> GUESTS = new ArrayList<>();
    private static final List<ConfigMigration.Rule> RULES = new ArrayList<>();

    private Merge() {
    }

    static List<Module> guests() {
        return GUESTS;
    }

    /** Takes the module out of the module list; its name and id still find it. */
    @SuppressWarnings("unchecked")
    static void hide(ModuleManager manager, Module guest) throws ReflectiveOperationException {
        Field f = ModuleManager.class.getDeclaredField("modules");
        f.setAccessible(true);
        ((List<Module>) f.get(manager)).remove(guest);
        if (!GUESTS.contains(guest)) {
            GUESTS.add(guest);
        }
    }

    /** Lets a name or id find this module as well (for the old name of a renamed module). */
    @SuppressWarnings("unchecked")
    static void alias(ModuleManager manager, String name, Module module) throws ReflectiveOperationException {
        Field f = ModuleManager.class.getDeclaredField("byName");
        f.setAccessible(true);
        ((Map<String, Module>) f.get(manager)).put(name.toLowerCase(java.util.Locale.ROOT), module);
    }

    static void rule(ConfigMigration.Rule rule) {
        RULES.add(rule);
    }

    static void link(Module host, BooleanSupplier want, Module guest, boolean follow) {
        LINKS.add(new Link(host, want, guest, follow));
    }

    /** Called before the config is applied (see Patcher.hookConfig). */
    public static void migrate(JsonObject root) {
        try {
            ConfigMigration.apply(root, RULES);
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] config migration failed", t);
        }
    }

    /**
     * Moves all settings of the guest into the host, shown only while {@code gate} is true. A setting whose name is taken is
     * renamed to "label name". Returns old setting id → new id for the config migration.
     */
    static Map<String, String> absorb(Module host, Module guest, String label, BooleanSupplier gate) throws ReflectiveOperationException {
        Method add = Module.class.getDeclaredMethod("add", Setting.class);
        add.setAccessible(true);
        Field name = Setting.class.getDeclaredField("name");
        name.setAccessible(true);
        Field visibility = Setting.class.getDeclaredField("visibility");
        visibility.setAccessible(true);
        Set<String> taken = new HashSet<>();
        for (Setting<?> s : host.settings()) {
            taken.add(s.id());
        }
        Map<String, String> renamed = new HashMap<>();
        for (Setting<?> s : new ArrayList<>(guest.settings())) {
            String oldId = s.id();
            if (taken.contains(oldId)) {
                name.set(s, label + " " + s.name());
            }
            taken.add(s.id());
            BooleanSupplier original = (BooleanSupplier) visibility.get(s);
            s.visibleWhen(() -> gate.getAsBoolean() && original.getAsBoolean());
            add.invoke(host, s);
            renamed.put(oldId, s.id());
        }
        return renamed;
    }

    /** Every setting of the module is shown only while {@code gate} is true. */
    static void gateAll(Module module, BooleanSupplier gate) throws ReflectiveOperationException {
        Field visibility = Setting.class.getDeclaredField("visibility");
        visibility.setAccessible(true);
        for (Setting<?> s : module.settings()) {
            BooleanSupplier original = (BooleanSupplier) visibility.get(s);
            s.visibleWhen(() -> gate.getAsBoolean() && original.getAsBoolean());
        }
    }

    static void addSetting(Module host, Setting<?> setting, boolean first) throws ReflectiveOperationException {
        if (first) {
            host.settings().add(0, setting);
        } else {
            Method add = Module.class.getDeclaredMethod("add", Setting.class);
            add.setAccessible(true);
            add.invoke(host, setting);
        }
    }

    static void tickLinks() {
        for (Link l : LINKS) {
            boolean w = l.host.isEnabled() && l.want.getAsBoolean();
            if (w != l.last) {
                l.last = w;
                if (l.guest.isEnabled() != w) {
                    l.guest.setEnabledSilently(w);
                }
            } else if (l.follow && w && !l.guest.isEnabled() && !AutomationSupervisor.isPaused(l.guest)) {
                // a restart by the supervisor takes about a second; longer than that, the part has finished by itself and
                // the module it lives in goes off with it
                if (++l.offTicks >= 60) {
                    l.offTicks = 0;
                    l.last = false;
                    l.host.setEnabledSilently(false);
                }
            } else {
                l.offTicks = 0;
            }
        }
    }
}
