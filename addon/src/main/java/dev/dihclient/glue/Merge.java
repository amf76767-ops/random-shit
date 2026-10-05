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

public final class Merge {

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

    @SuppressWarnings("unchecked")
    static void hide(ModuleManager manager, Module guest) throws ReflectiveOperationException {
        Field f = ModuleManager.class.getDeclaredField("modules");
        f.setAccessible(true);
        ((List<Module>) f.get(manager)).remove(guest);
        if (!GUESTS.contains(guest)) {
            GUESTS.add(guest);
        }
    }

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

    public static void migrate(JsonObject root) {
        try {
            ConfigMigration.apply(root, RULES);
            styleMigration(root);
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] config migration failed", t);
        }
    }

    private static void styleMigration(JsonObject root) {
        if (root == null || !root.has("modules") || !root.get("modules").isJsonObject()) {
            return;
        }
        JsonObject modules = root.getAsJsonObject("modules");
        if (!modules.has("clickgui") || !modules.get("clickgui").isJsonObject()) {
            return;
        }
        JsonObject gui = modules.getAsJsonObject("clickgui");
        if (!gui.has("settings") || !gui.get("settings").isJsonObject()) {
            return;
        }
        JsonObject settings = gui.getAsJsonObject("settings");
        if (settings.has("style")) {
            return;
        }
        String theme = settings.has("theme") && settings.get("theme").isJsonPrimitive() ? settings.get("theme").getAsString() : "";
        String layout = settings.has("layout") && settings.get("layout").isJsonPrimitive() ? settings.get("layout").getAsString() : "";
        if (theme.equalsIgnoreCase("GLASS")) {
            settings.addProperty("style", "GLASS");
        } else if (layout.equalsIgnoreCase("METEOR") || layout.equalsIgnoreCase("MODERN")) {
            settings.addProperty("style", layout.toUpperCase(java.util.Locale.ROOT));
        }
        settings.remove("theme");
        settings.remove("layout");
    }

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
