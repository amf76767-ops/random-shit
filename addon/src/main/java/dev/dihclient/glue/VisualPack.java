package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.Notifications;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1293;
import net.minecraft.class_1294;

public class VisualPack extends Module {
    private static final String RESOURCE = "/dihclient/DIH-Visuals.zip";
    private static final String PREFIX = "DIH-Visuals";
    private static final String MC = "assets/minecraft/";

    private record Effect(BoolSetting on, String... files) {
    }

    public final BoolSetting nightVision = this.bool("Night Vision", "Endless night vision on your screen only: caves and nights are bright, colours stay normal. The server does not know about it.", true);
    private final BoolSetting rain = this.bool("Invisible Rain", "Rain is not drawn. You still hear it.", true).onChange(this::changed);
    private final BoolSetting clouds = this.bool("Clear Clouds", "See-through clouds (shader).", true).onChange(this::changed);
    private final BoolSetting sky = this.bool("Sky Gradient", "Sky with a colour gradient, and at night a sky full of stars with a milky way (shader).", true).onChange(this::changed);
    private final BoolSetting noMoon = this.bool("No Moon", "The moon is not drawn, so the night is only stars.", true).onChange(this::changed);
    private final BoolSetting glint = this.bool("Colour Glint", "Enchant glint that changes its colour (shader and picture).", true).onChange(this::changed);
    private final BoolSetting stone = this.bool("Deepslate Stone", "Stone looks like deepslate.", true).onChange(this::changed);
    private final BoolSetting water = this.bool("Clear Water", "See-through, animated water.", true).onChange(this::changed);
    private final BoolSetting sun = this.bool("Round Sun", "Round sun with a glow.", true).onChange(this::changed);
    private final BoolSetting totem = this.bool("Small Totem", "The totem pop picture is smaller.", true).onChange(this::changed);
    private final BoolSetting lowFire = this.bool("Low Fire", "The flames when you burn only fill the bottom of the screen.", true).onChange(this::changed);
    private final BoolSetting lowShield = this.bool("Low Shield", "Your shield sits lower in first person and covers less.", true).onChange(this::changed);
    private final BoolSetting clearFog = this.bool("Clear Water & Lava", "Much less fog under water and in lava, so you see far (also less blindness fog).", true).onChange(this::changed);
    private final BoolSetting oreBorders = this.bool("Ore Borders", "Every ore gets a border in its own colour, so you spot it at once (only the texture, no x-ray).", true).onChange(this::changed);
    private final BoolSetting smallItems = this.bool("Small Held Items", "Tools, weapons and blocks in your hand are smaller and lower.", true).onChange(this::changed);
    private final BoolSetting crosshair = this.bool("Clean Crosshair", "A thin cross with a dot in the middle.", true).onChange(this::changed);
    private final BoolSetting quiet = this.bool("Quiet Ambience", "No random cave sounds, rain and thunder are quieter, so you hear players better.", true).onChange(this::changed);
    public final DoubleSetting crystalSize = this.dbl("Crystal Size", "Size of end crystals (1.0 = normal). Works while VisualPack is on.", 0.6, 0.3, 1.0, 0.05);

    private static final String[] ORES = {"coal_ore", "iron_ore", "copper_ore", "gold_ore", "redstone_ore", "lapis_ore", "diamond_ore", "emerald_ore",
            "deepslate_coal_ore", "deepslate_iron_ore", "deepslate_copper_ore", "deepslate_gold_ore", "deepslate_redstone_ore", "deepslate_lapis_ore",
            "deepslate_diamond_ore", "deepslate_emerald_ore", "nether_gold_ore", "nether_quartz_ore", "ancient_debris_"};

    private static String[] orePaths() {
        String[] out = new String[ORES.length];
        for (int i = 0; i < ORES.length; i++) {
            out[i] = MC + "textures/block/" + ORES[i];
        }
        return out;
    }

    private final List<Effect> effects = List.of(
            new Effect(this.rain, MC + "textures/environment/rain.png"),
            new Effect(this.clouds, MC + "shaders/core/rendertype_clouds.fsh"),
            new Effect(this.sky, MC + "shaders/core/sky.fsh", MC + "shaders/core/sky.vsh"),
            new Effect(this.glint, MC + "shaders/core/glint.fsh", MC + "textures/misc/enchanted_glint_"),
            new Effect(this.stone, MC + "textures/block/stone.png"),
            new Effect(this.water, MC + "textures/block/water_"),
            new Effect(this.sun, MC + "textures/environment/celestial/sun.png"),
            new Effect(this.noMoon, MC + "textures/environment/celestial/moon/"),
            new Effect(this.totem, MC + "models/item/totem_of_undying.json"),
            new Effect(this.lowFire, MC + "textures/block/fire_"),
            new Effect(this.lowShield, MC + "models/item/shield"),
            new Effect(this.clearFog, MC + "shaders/include/fog.glsl"),
            new Effect(this.oreBorders, orePaths()),
            new Effect(this.smallItems, MC + "models/item/generated.json", MC + "models/item/handheld.json", MC + "models/block/block.json"),
            new Effect(this.crosshair, MC + "textures/gui/sprites/hud/crosshair.png"),
            new Effect(this.quiet, MC + "sounds.json"));

    private boolean dirty;
    private static Look look = Look.NONE;
    private static VisualPack instance;

    public VisualPack() {
        super("VisualPack", Category.RENDER,
                "Resource pack and visuals: night vision, invisible rain, clear clouds and water, low fire and shield, ore borders, sky gradient, colour glint and more. Every part has a switch.");
        instance = this;
    }

    public void setLook(Look next) {
        if (look != next) {
            look = next;
            this.dirty = true;
        }
    }

    public Look look() {
        return look;
    }

    public static VisualPack instance() {
        return instance;
    }

    private void changed() {
        this.dirty = true;
    }

    @Override
    protected void onEnable() {
        this.dirty = false;
        this.apply(true);
    }

    @Override
    protected void onDisable() {
        this.dirty = false;
        this.apply(false);
        removeNightVision();
    }

    public static float crystalScale() {
        VisualPack v = instance;
        return v != null && v.isEnabled() ? v.crystalSize.get().floatValue() : 1.0F;
    }

    private static boolean ours(class_1293 e) {
        return e != null && e.method_5584() == -1 && !e.method_5581() && !e.method_5592();
    }

    private static void removeNightVision() {
        if (mc.field_1724 != null && ours(mc.field_1724.method_6112(class_1294.field_5925))) {
            mc.field_1724.method_6111(class_1294.field_5925);
        }
    }

    private void tickNightVision() {
        if (mc.field_1724 == null) {
            return;
        }
        if (!this.nightVision.get()) {
            removeNightVision();
        } else if (!mc.field_1724.method_6059(class_1294.field_5925)) {
            mc.field_1724.method_6092(new class_1293(class_1294.field_5925, -1, 0, false, false, false));
        }
    }

    @Override
    public void onTick() {
        this.tickNightVision();
        if (this.dirty && mc.field_1724 != null) {
            this.dirty = false;
            this.apply(true);
        }
    }

    @Override
    public String getInfo() {
        long n = this.effects.stream().filter(e -> e.on().get()).count();
        return n + "/" + this.effects.size();
    }

    private String packName() {
        int bits = 0;
        for (int i = 0; i < this.effects.size(); i++) {
            if (this.effects.get(i).on().get()) {
                bits |= 1 << i;
            }
        }
        String mood = look == Look.NONE ? "" : "-" + look.id().replaceAll("[^a-z0-9]", "");
        return PREFIX + "-" + Integer.toHexString(0x1000 | bits) + mood + ".zip";
    }

    private void apply(boolean on) {
        try {
            String name = this.packName();
            if (on) {
                this.install(name);
            }
            boolean reloaded = switchPack(PREFIX, on, "file/" + name);
            this.cleanOld(on ? name : null);
            if (reloaded) {
                Notifications.info(this.name(), on ? "Pack on (" + this.getInfo() + "), reloading resources" : "Pack off, reloading resources");
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] VisualPack failed", t);
            Notifications.warn(this.name(), "Failed: " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    private boolean skip(String entry) {
        if (entry.startsWith(MC + "shaders/core/lightmap.fsh")) {
            return look == Look.NONE;
        }
        for (Effect e : this.effects) {
            if (!e.on().get()) {
                for (String f : e.files()) {
                    if (entry.startsWith(f)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void install(String name) throws IOException {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        if (Files.isRegularFile(target)) {
            return;
        }
        byte[] bytes;
        try (InputStream in = VisualPack.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("Pack is missing from the jar");
            }
            bytes = PackFilter.copy(in, this::skip, look::patch);
        }
        Path tmp = dir.resolve(name + ".tmp");
        Files.write(tmp, bytes);
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void cleanOld(String keep) {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, PREFIX + "*.zip")) {
            for (Path p : ds) {
                if (keep == null || !p.getFileName().toString().equals(keep)) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {

                    }
                }
            }
        } catch (IOException ignored) {

        }
    }

    @SuppressWarnings("unchecked")
    static boolean switchPack(String prefix, boolean on, String id) throws Exception {
        Object manager = null;
        for (Method m : mc.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType().getName().equals("net.minecraft.class_3283")) {
                manager = m.invoke(mc);
                break;
            }
        }
        if (manager == null) {
            throw new IllegalStateException("ResourcePackManager not found");
        }
        Class<?> mgr = manager.getClass();
        invoke(mgr, manager, "method_14445");
        Collection<String> enabled = (Collection<String>) invoke(mgr, manager, "method_29210");
        List<String> next = new ArrayList<>(enabled);
        next.removeIf(e -> e.startsWith("file/" + prefix));
        if (on) {
            next.add(id);
        }

        List<String> shaders = new ArrayList<>();
        next.removeIf(e -> e.startsWith("file/" + ShaderModule.PREFIX) && shaders.add(e));
        next.addAll(shaders);
        if (next.equals(new ArrayList<>(enabled))) {
            return false;
        }
        Method set = null;
        for (Method m : mgr.getMethods()) {
            if (m.getName().equals("method_14447") && m.getParameterCount() == 1) {
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
                if (reload == null || m.getName().equals("method_1521")) {
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

    @SuppressWarnings("unchecked")
    private static void remember(List<String> ids) {
        try {
            Object options = mc.field_1690;
            Field f = options.getClass().getField("field_1887");
            List<String> list = (List<String>) f.get(options);
            list.clear();
            list.addAll(ids);
            options.getClass().getMethod("method_1640").invoke(options);
        } catch (Throwable ignored) {

        }
    }

    private static Object invoke(Class<?> c, Object o, String name) throws Exception {
        return c.getMethod(name).invoke(o);
    }
}
