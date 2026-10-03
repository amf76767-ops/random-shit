package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.util.Notifications;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Switches the bundled "DIH Visuals" resource pack (Tiefenschiefer-Stone, klares Wasser, bunter Glanz, kleines Totem) on and off.
 * The pack ships inside this jar and is copied to {@code resourcepacks/DIH-Visuals.zip} when the module is turned on.
 *
 * The Minecraft calls are found by signature through reflection, so a wrong guess shows up as a message in the game
 * instead of a crash. Turning the module on or off reloads the resources once.
 */
public class VisualPack extends Module {
    private static final String RESOURCE = "/dihclient/DIH-Visuals.zip";
    private static final String FILE = "DIH-Visuals.zip";
    private static final String PACK_ID = "file/" + FILE;

    public VisualPack() {
        super("VisualPack", Category.RENDER,
                "Aktiviert das mitgelieferte Resourcepack: Stone als Tiefenschiefer, klares Wasser, bunter Glanz, kleines Totem.");
    }

    @Override
    protected void onEnable() {
        this.apply(true);
    }

    @Override
    protected void onDisable() {
        this.apply(false);
    }

    private void apply(boolean on) {
        try {
            if (on) {
                this.install();
            }
            if (!this.switchPack(on)) {
                return; // already in the wanted state, nothing to reload
            }
            Notifications.info(this.name(), on ? "Resourcepack an, Ressourcen laden neu" : "Resourcepack aus, Ressourcen laden neu");
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] VisualPack failed", t);
            Notifications.warn(this.name(), "Fehlgeschlagen: " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    private void install() throws IOException {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
        Files.createDirectories(dir);
        try (InputStream in = VisualPack.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("Pack fehlt in der JAR");
            }
            Files.copy(in, dir.resolve(FILE), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** @return true when the list of enabled packs changed and the resources were reloaded */
    @SuppressWarnings("unchecked")
    private boolean switchPack(boolean on) throws Exception {
        Object manager = null;
        for (Method m : mc.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType().getName().equals("net.minecraft.class_3283")) {
                manager = m.invoke(mc);
                break;
            }
        }
        if (manager == null) {
            throw new IllegalStateException("ResourcePackManager nicht gefunden");
        }
        Class<?> mgr = manager.getClass();
        invoke(mgr, manager, "method_14445"); // scanPacks, findet die neu kopierte Datei
        Collection<String> enabled = (Collection<String>) invoke(mgr, manager, "method_29210"); // getEnabledIds
        List<String> next = new ArrayList<>(enabled);
        if (on == next.contains(PACK_ID)) {
            return false;
        }
        if (on) {
            next.add(PACK_ID); // ganz oben: spätere Einträge gewinnen
        } else {
            next.remove(PACK_ID);
        }
        Method set = null;
        for (Method m : mgr.getMethods()) {
            if (m.getName().equals("method_14447") && m.getParameterCount() == 1) { // setEnabledProfiles
                set = m;
            }
        }
        if (set == null) {
            throw new NoSuchMethodException("setEnabledProfiles");
        }
        set.invoke(manager, next);
        remember(next);
        Method reload = null;
        for (Method m : mc.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType().getName().equals("java.util.concurrent.CompletableFuture")) {
                if (reload == null || m.getName().equals("method_1521")) { // reloadResources
                    reload = m;
                }
            }
        }
        if (reload == null) {
            throw new NoSuchMethodException("reloadResources");
        }
        reload.invoke(mc);
        return true;
    }

    /** Writes the list into options.txt as well, so the pack is already active at the next start. Best effort. */
    @SuppressWarnings("unchecked")
    private static void remember(List<String> ids) {
        try {
            Object options = mc.field_1690;
            Field f = options.getClass().getField("field_1887"); // resourcePacks
            List<String> list = (List<String>) f.get(options);
            list.clear();
            list.addAll(ids);
            options.getClass().getMethod("method_1640").invoke(options); // write
        } catch (Throwable ignored) {
            // not critical: the module switches the pack on again when it starts
        }
    }

    private static Object invoke(Class<?> c, Object o, String name) throws Exception {
        return c.getMethod(name).invoke(o);
    }
}
