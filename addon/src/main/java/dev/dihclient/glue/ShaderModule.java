package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.util.Notifications;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1799;
import net.minecraft.class_9334;

public class ShaderModule extends Module {
    static final String PREFIX = "DIH-Shader";
    private static final int SIZE = 128;
    private static final int FORMAT = 75;

    public enum Style {
        AURORA, RAINBOW, FIRE, ICE, GOLD, TOXIC, GALAXY, CRIMSON
    }

    public final EnumSetting<Style> style = this.mode("Style", "Colour and look of the shine.", Style.GALAXY).onChange(this::changed);
    public final DoubleSetting strength = this.dbl("Strength", "How bright the shine is.", 1.0, 0.3, 1.6, 0.1).onChange(this::changed);
    public final BoolSetting tools = this.bool("Tools", "Tools and weapons shine even without enchantments.", true);
    public final BoolSetting armor = this.bool("Armor", "Armor and elytra shine even without enchantments.", true);

    private static volatile ShaderModule active;
    private boolean dirty;

    public ShaderModule() {
        super("Shader", Category.RENDER, "Custom shine on your tools, weapons, armor and enchanted items, in a style you pick.");
    }

    private void changed() {
        this.dirty = true;
    }

    public static boolean forceGlint(class_1799 stack) {
        ShaderModule m = active;
        if (m == null || stack.method_7960()) {
            return false;
        }
        if (m.tools.get() && (stack.method_57826(class_9334.field_50077) || stack.method_57826(class_9334.field_55878)
                || stack.method_57826(class_9334.field_63631) || stack.method_57826(class_9334.field_63632))) {
            return true;
        }
        return m.armor.get() && stack.method_57826(class_9334.field_54196) && stack.method_57826(class_9334.field_53695);
    }

    @Override
    protected void onEnable() {
        active = this;
        this.dirty = false;
        this.apply(true);
    }

    @Override
    protected void onDisable() {
        active = null;
        this.dirty = false;
        this.apply(false);
    }

    @Override
    public void onTick() {
        if (this.dirty && mc.field_1724 != null) {
            this.dirty = false;
            this.apply(true);
        }
    }

    @Override
    public String getInfo() {
        return this.style.displayValue();
    }

    private String packName() {
        return PREFIX + "-" + this.style.get().name().toLowerCase(Locale.ROOT) + "-" + Math.round(this.strength.get() * 100) + ".zip";
    }

    private void apply(boolean on) {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
            Files.createDirectories(dir);
            String name = this.packName();
            if (on) {
                Path target = dir.resolve(name);
                if (!Files.isRegularFile(target)) {
                    Path tmp = dir.resolve(name + ".tmp");
                    Files.write(tmp, this.buildPack());
                    Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            boolean reloaded = VisualPack.switchPack(PREFIX, on, "file/" + name);
            cleanOld(dir, on ? name : null);
            if (reloaded) {
                Notifications.info(this.name(), on ? "Shine: " + this.style.displayValue() + ", reloading resources" : "Shine off, reloading resources");
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Shader failed", t);
            Notifications.warn(this.name(), "Failed: " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    private static void cleanOld(Path dir, String keep) {
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

    private byte[] buildPack() throws IOException {
        byte[] glint = png(SIZE, SIZE, glintPixels(this.style.get(), this.strength.get()));
        byte[] meta = "{\n  \"texture\": {\n    \"blur\": true\n  }\n}\n".getBytes(StandardCharsets.UTF_8);
        String mcmeta = "{\"pack\": {\"pack_format\": " + FORMAT + ", \"min_format\": " + FORMAT + ", \"max_format\": " + FORMAT
                + ", \"description\": \"DIH Shader: " + this.style.displayValue() + " shine\"}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8));

            try (java.io.InputStream in = ShaderModule.class.getResourceAsStream("/assets/minecraft/shaders/core/glint.fsh")) {
                if (in != null) {
                    put(zip, "assets/minecraft/shaders/core/glint.fsh", in.readAllBytes());
                }
            }
            for (String kind : new String[]{"item", "armor"}) {
                put(zip, "assets/minecraft/textures/misc/enchanted_glint_" + kind + ".png", glint);
                put(zip, "assets/minecraft/textures/misc/enchanted_glint_" + kind + ".png.mcmeta", meta);
            }
        }
        return out.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    static int[] glintPixels(Style style, double strength) {
        int[] px = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double u = (double) (x + y) / SIZE;
                double v = (double) (x - y) / SIZE;
                double band = Math.pow(0.5 + 0.5 * Math.sin(2 * Math.PI * 2 * u), 3);
                double thin = Math.pow(0.5 + 0.5 * Math.sin(2 * Math.PI * (5 * u + 0.3)), 12) * 0.6;
                double hue = 0.5 + 0.5 * Math.sin(2 * Math.PI * (u + v));
                double light = Math.min(1.0, band + thin);
                int sparkleChance = style == Style.GALAXY ? 40 : (style == Style.ICE ? 70 : 140);
                int h = hash(x, y);
                if (h % sparkleChance == 0) {
                    light = 1.0;
                }
                double[] c = colour(style, hue, (double) x / SIZE);
                double k = light * strength;
                int r = clamp(c[0] * k);
                int g = clamp(c[1] * k);
                int b = clamp(c[2] * k);
                px[y * SIZE + x] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
        return px;
    }

    private static double[] colour(Style style, double t, double xPos) {
        return switch (style) {
            case AURORA -> mix(new double[]{70, 220, 255}, new double[]{190, 90, 255}, t);
            case RAINBOW -> hsv((t + xPos) % 1.0);
            case FIRE -> mix(new double[]{255, 50, 0}, new double[]{255, 200, 40}, t);
            case ICE -> mix(new double[]{120, 200, 255}, new double[]{240, 250, 255}, t);
            case GOLD -> mix(new double[]{255, 160, 10}, new double[]{255, 240, 140}, t);
            case TOXIC -> mix(new double[]{40, 255, 70}, new double[]{200, 255, 30}, t);
            case GALAXY -> mix(new double[]{110, 40, 255}, new double[]{255, 80, 210}, t);
            case CRIMSON -> mix(new double[]{190, 0, 30}, new double[]{255, 80, 120}, t);
        };
    }

    private static double[] mix(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static double[] hsv(double h) {
        double r = Math.abs(h * 6 - 3) - 1;
        double g = 2 - Math.abs(h * 6 - 2);
        double b = 2 - Math.abs(h * 6 - 4);
        return new double[]{255 * Math.max(0, Math.min(1, r)), 255 * Math.max(0, Math.min(1, g)), 255 * Math.max(0, Math.min(1, b))};
    }

    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    static byte[] png(int w, int h, int[] argb) throws IOException {
        byte[] raw = new byte[h * (w * 4 + 1)];
        int i = 0;
        for (int y = 0; y < h; y++) {
            raw[i++] = 0;
            for (int x = 0; x < w; x++) {
                int p = argb[y * w + x];
                raw[i++] = (byte) (p >> 16);
                raw[i++] = (byte) (p >> 8);
                raw[i++] = (byte) p;
                raw[i++] = (byte) (p >>> 24);
            }
        }
        Deflater deflater = new Deflater(9);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!deflater.finished()) {
            z.write(buf, 0, deflater.deflate(buf));
        }
        deflater.end();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(out);
        d.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream ih = new DataOutputStream(ihdr);
        ih.writeInt(w);
        ih.writeInt(h);
        ih.write(new byte[]{8, 6, 0, 0, 0});
        chunk(d, "IHDR", ihdr.toByteArray());
        chunk(d, "IDAT", z.toByteArray());
        chunk(d, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(DataOutputStream d, String type, byte[] data) throws IOException {
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        d.writeInt(data.length);
        d.write(t);
        d.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        d.writeInt((int) crc.getValue());
    }
}
