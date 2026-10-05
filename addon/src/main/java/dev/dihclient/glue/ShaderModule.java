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
        AURORA, RAINBOW, FIRE, ICE, GOLD, TOXIC, GALAXY, CRIMSON, OCEAN, SUNSET, EMERALD, VOID, HOLO, PLASMA, SAKURA, ELECTRIC
    }

    public final EnumSetting<Style> style = this.mode("Style", "Colour and look of the shine.", Style.GALAXY).onChange(this::changed);
    public final DoubleSetting strength = this.dbl("Strength", "How bright the shine is.", 1.0, 0.3, 1.6, 0.1).onChange(this::changed);
    public final BoolSetting tools = this.bool("Tools", "Tools and weapons shine even without enchantments.", true);
    public final BoolSetting armor = this.bool("Armor", "Armor and elytra shine even without enchantments.", true);

    public final BoolSetting hats = this.bool("Hats", "Hats get a moving shine in the colours of the chosen style.", true);

    private static volatile ShaderModule active;
    private boolean dirty;

    public ShaderModule() {
        super("Shader", Category.RENDER, "Custom shine on your swords, tools, armor, hats and enchanted items, in a style you pick.");
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

    public static int hatShine(int base, float t, float seed) {
        ShaderModule m = active;
        if (m == null || !m.hats.get()) {
            return base;
        }
        double time = System.currentTimeMillis() % 1000000L / 1000.0;
        double hue = 0.5 + 0.5 * Math.sin(2 * Math.PI * (t * 0.6 + time * 0.3 + seed));
        double[] c = colour(m.style.get(), hue, (t + time * 0.2) % 1.0);
        double wave = Math.pow(0.5 + 0.5 * Math.sin(2 * Math.PI * (t * 1.5 - time * 0.7)), 6);
        double mixAmt = Math.min(0.92, (0.45 + 0.35 * wave) * m.strength.get());
        double glow = 1.0 + 0.5 * wave * m.strength.get();
        int r = clamp(((base >> 16 & 255) * (1 - mixAmt) + c[0] * mixAmt) * glow);
        int g = clamp(((base >> 8 & 255) * (1 - mixAmt) + c[1] * mixAmt) * glow);
        int b = clamp(((base & 255) * (1 - mixAmt) + c[2] * mixAmt) * glow);
        return base & 0xFF000000 | r << 16 | g << 8 | b;
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
        return PREFIX + "-v2-" + this.style.get().name().toLowerCase(Locale.ROOT) + "-" + Math.round(this.strength.get() * 100) + ".zip";
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

            try (java.io.InputStream in = ShaderModule.class.getResourceAsStream("/dihclient/shine_glint.fsh")) {
                if (in != null) {
                    double[] p = params(this.style.get());
                    String src = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                            .replace("@TWINKLE@", num(p[0])).replace("@LAYER@", num(p[1])).replace("@CHROMA@", num(p[2]))
                            .replace("@PULSE@", num(p[3])).replace("@BOOST@", num(p[4] * (0.8 + 0.2 * this.strength.get())));
                    put(zip, "assets/minecraft/shaders/core/glint.fsh", src.getBytes(StandardCharsets.UTF_8));
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

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static double[] params(Style style) {
        return switch (style) {
            case RAINBOW, HOLO -> new double[]{0.35, 0.6, 1.5, 0.05, 1.2};
            case VOID -> new double[]{0.6, 0.7, 0.0, 0.1, 1.45};
            case ELECTRIC -> new double[]{0.9, 0.5, 0.4, 0.16, 1.3};
            case FIRE -> new double[]{0.5, 0.6, 0.0, 0.14, 1.3};
            case GOLD, SAKURA -> new double[]{0.6, 0.5, 0.0, 0.07, 1.25};
            default -> new double[]{0.35, 0.55, 0.0, 0.06, 1.25};
        };
    }

    private static double fade(double v) {
        return v * v * (3 - 2 * v);
    }

    private static double lattice(int x, int y, int cells, int seed) {
        int ix = ((x % cells) + cells) % cells;
        int iy = ((y % cells) + cells) % cells;
        return hash(ix + seed * 131, iy + seed * 71) / (double) 0x7FFFFFFF;
    }

    private static double noise(double x, double y, int cells, int seed) {
        double gx = x * cells;
        double gy = y * cells;
        int x0 = (int) Math.floor(gx);
        int y0 = (int) Math.floor(gy);
        double fx = fade(gx - x0);
        double fy = fade(gy - y0);
        double a = lattice(x0, y0, cells, seed) * (1 - fx) + lattice(x0 + 1, y0, cells, seed) * fx;
        double b = lattice(x0, y0 + 1, cells, seed) * (1 - fx) + lattice(x0 + 1, y0 + 1, cells, seed) * fx;
        return a * (1 - fy) + b * fy;
    }

    static int[] glintPixels(Style style, double strength) {
        int[] px = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double nx = (double) x / SIZE;
                double ny = (double) y / SIZE;
                double u = (double) (x + y) / SIZE;
                double v = (double) (x - y) / SIZE;
                double fbm = 0.55 * noise(nx, ny, 4, 1) + 0.3 * noise(nx, ny, 8, 2) + 0.15 * noise(nx, ny, 16, 3);
                double band = Math.pow(0.5 + 0.5 * Math.sin(2 * Math.PI * (2 * u + fbm * 0.6)), 3);
                double thin = Math.pow(0.5 + 0.5 * Math.sin(2 * Math.PI * (5 * u + 0.3 + fbm * 0.4)), 12) * 0.6;
                double cloud = Math.pow(fbm, 2.4) * 1.3;
                double hue = 0.5 + 0.5 * Math.sin(2 * Math.PI * (u + v)) * 0.7 + (fbm - 0.5) * 0.6;
                double light = Math.min(1.0, band * 0.85 + thin + cloud);
                int sparkleChance = style == Style.GALAXY || style == Style.VOID ? 36 : (style == Style.ICE || style == Style.ELECTRIC ? 60 : 130);
                int h = hash(x, y);
                if (h % sparkleChance == 0) {
                    light = 1.0;
                }
                double[] c = colour(style, Math.max(0.0, Math.min(1.0, hue)), nx);
                double k = light * strength;
                px[y * SIZE + x] = 0xFF000000 | clamp(c[0] * k) << 16 | clamp(c[1] * k) << 8 | clamp(c[2] * k);
            }
        }
        return px;
    }

    private static double[] grad(double[][] stops, double t) {
        t = Math.max(0.0, Math.min(1.0, t)) * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) Math.floor(t));
        return mix(stops[i], stops[i + 1], t - i);
    }

    private static double[] colour(Style style, double t, double xPos) {
        return switch (style) {
            case AURORA -> grad(new double[][]{{70, 220, 255}, {120, 255, 170}, {190, 90, 255}}, t);
            case RAINBOW -> hsv((t + xPos) % 1.0);
            case FIRE -> grad(new double[][]{{255, 40, 0}, {255, 140, 10}, {255, 230, 90}}, t);
            case ICE -> grad(new double[][]{{90, 170, 255}, {190, 235, 255}, {250, 255, 255}}, t);
            case GOLD -> grad(new double[][]{{255, 150, 10}, {255, 215, 70}, {255, 250, 190}}, t);
            case TOXIC -> grad(new double[][]{{30, 255, 80}, {150, 255, 30}, {230, 255, 100}}, t);
            case GALAXY -> grad(new double[][]{{80, 30, 255}, {190, 60, 255}, {255, 80, 200}}, t);
            case CRIMSON -> grad(new double[][]{{160, 0, 30}, {235, 30, 70}, {255, 110, 150}}, t);
            case OCEAN -> grad(new double[][]{{0, 90, 200}, {0, 200, 220}, {150, 255, 235}}, t);
            case SUNSET -> grad(new double[][]{{120, 50, 190}, {255, 70, 110}, {255, 190, 80}}, t);
            case EMERALD -> grad(new double[][]{{0, 140, 70}, {40, 230, 130}, {190, 255, 210}}, t);
            case VOID -> grad(new double[][]{{20, 0, 60}, {90, 0, 170}, {210, 60, 255}}, t);
            case HOLO -> mix(hsv((t * 0.8 + xPos * 0.5) % 1.0), new double[]{255, 255, 255}, 0.35);
            case PLASMA -> grad(new double[][]{{255, 0, 140}, {120, 60, 255}, {0, 200, 255}}, t);
            case SAKURA -> grad(new double[][]{{255, 150, 190}, {255, 200, 220}, {255, 245, 250}}, t);
            case ELECTRIC -> grad(new double[][]{{0, 120, 255}, {80, 220, 255}, {255, 255, 255}}, t);
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
