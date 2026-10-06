package dev.dihclient.preview;

import dev.dihclient.DIHClient;
import dev.dihclient.gui.MeteorGuiScreen;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.ClickGui;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.class_310;
import net.minecraft.class_332;

/** Renders the real MeteorGuiScreen with Java2D (no Minecraft) and writes PNG files. */
public final class Preview {
    static final int W = 520;
    static final int H = 300;
    static final int SCALE = 3;

    static final class Fake extends Module {
        Fake(String name, Category c, String desc, boolean rich) {
            super(name, c, desc);
            if (rich) {
                bool("Smooth Rotation", "Turns smoothly.", true);
                bool("Only Players", "Ignore mobs.", false);
                dbl("Range", "How far.", 4.2, 1.0, 8.0, 0.1);
                dbl("Speed", "How fast.", 1.0, 0.1, 4.0, 0.1);
                mode("Priority", "Who first.", Category.COMBAT);
                color("Colour", "Colour.", 0xFF8FA2B6);
                text("Name", "Text.", "hello", 16);
                action("Reset", "Resets.", () -> { });
            }
        }
    }

    static Object get(Class<?> c, Object o, String f) throws Exception {
        Field x = c.getDeclaredField(f);
        x.setAccessible(true);
        return x.get(o);
    }

    static void set(Class<?> c, Object o, String f, Object v) throws Exception {
        Field x = c.getDeclaredField(f);
        x.setAccessible(true);
        x.set(o, v);
    }

    static void frame(MeteorGuiScreen s, class_332 g, int mx, int my, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            java.awt.Graphics2D bg = g.image().createGraphics();
            bg.setComposite(java.awt.AlphaComposite.Src);
            bg.drawImage(BASE, 0, 0, null);
            bg.dispose();
            class_332.fills = 0;
            class_332.texts = 0;
            s.method_25420(g, mx, my, 0);
            s.method_25394(g, mx, my, 0);
            last = class_332.fills + " fills, " + class_332.texts + " texts";
            Thread.sleep(8);
        }
    }

    static String last = "";
    static BufferedImage BASE;

    static BufferedImage canvas() {
        BufferedImage img = new BufferedImage(W * SCALE, H * SCALE, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        // stand-in for the blurred game: dark dirt-ish gradient with some noise blobs
        g.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(0x4A5A3C), 0, img.getHeight(), new java.awt.Color(0x2B3340)));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 160; i++) {
            g.setColor(new java.awt.Color(r.nextInt(0x1000000) | 0x40000000, true));
            g.fillOval(r.nextInt(img.getWidth()), r.nextInt(img.getHeight()), 20 + r.nextInt(120), 20 + r.nextInt(80));
        }
        g.dispose();
        if (BASE == null) {
            BASE = img;
            img = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        }
        return img;
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "preview");
        out.mkdirs();
        String only = args.length > 1 ? args[1] : null;
        ModuleManager mm = new ModuleManager();
        ClickGui gui = new ClickGui();
        List<Module> modules = (List<Module>) get(ModuleManager.class, mm, "modules");
        Map<Class<?>, Module> byClass = (Map<Class<?>, Module>) get(ModuleManager.class, mm, "byClass");
        modules.add(gui);
        byClass.put(ClickGui.class, gui);
        String[][] names = {
            {"Killaura", "Attacks entities around you."}, {"Reach", "Hit further away."}, {"Velocity", "Take less knockback."},
            {"Criticals", "Always crit."}, {"Auto Totem", "Keeps a totem in your offhand."}, {"Flight", "Fly like a bird."},
            {"Sprint", "Always sprinting."}, {"Step", "Step up whole blocks."}, {"Speed", "Move faster."},
            {"ESP", "See players through walls."}, {"Tracers", "Lines to targets."}, {"Fullbright", "See in the dark."},
            {"Nuker", "Break blocks around you."}, {"Scaffold", "Places blocks below you."}, {"Auto Build", "Builds schematics."},
        };
        Category[] cats = Category.values();
        int i = 0;
        for (String[] n : names) {
            Category c = cats[i % 7];
            Fake m = new Fake(n[0], c, n[1], i == 0);
            if (i % 3 == 0) {
                set(Module.class, m, "enabled", true);
            }
            modules.add(m);
            i++;
        }
        for (Category c : new Category[] {Category.CLIENT, Category.FUN, Category.MISC}) {
            modules.add(new Fake("Example " + c.title, c, "A module of " + c.title + ".", false));
        }
        modules.add(gui);
        set(DIHClient.class, null, "modules", mm);

        class_310 mc = class_310.method_1551();
        Set<Module> expanded = (Set<Module>) get(MeteorGuiScreen.class, null, "EXPANDED");

        for (ClickGui.Look look : ClickGui.Look.values()) {
            if (only != null && !look.name().equalsIgnoreCase(only)) {
                continue;
            }
            gui.look.set(look);
            MeteorGuiScreen screen = new MeteorGuiScreen();
            screen.field_22789 = W;
            screen.field_22790 = H;
            screen.field_22787 = mc;
            BufferedImage img = canvas();
            class_332 g = new class_332(img, SCALE);
            if (look == ClickGui.Look.OBSIDIAN) {
                expanded.clear();
                frame(screen, g, 400, 200, 110);
                save(img, new File(out, "intro-110ms.png"));
                img = canvas();
                g = new class_332(img, SCALE);
                frame(screen, g, 400, 200, 120);
                save(img, new File(out, "intro-230ms.png"));
            }
            expanded.clear();
            expanded.add(modules.get(1));
            img = canvas();
            g = new class_332(img, SCALE);
            frame(screen, g, 24, 78, 900);
            save(img, new File(out, "theme-" + look.name().toLowerCase() + ".png"));
            System.out.println(look + ": " + last);
            if (look == ClickGui.Look.OBSIDIAN) {
                System.out.println("hits after intro: " + ((List<?>) get(MeteorGuiScreen.class, screen, "hits")).size());
                screen.method_25419();
                System.out.println("closed right after close(): " + screen.closed + " (expected false while animating)");
                img = canvas();
                g = new class_332(img, SCALE);
                frame(screen, g, 24, 78, 70);
                save(img, new File(out, "closing-70ms.png"));
                try {
                    frame(screen, g, 24, 78, 200);
                } catch (NullPointerException e) {
                    System.out.println("outro finished, finishClose() reached (config is not available in the preview)");
                }
            }
        }
        System.exit(0);
    }

    static void save(BufferedImage img, File f) throws Exception {
        ImageIO.write(img, "png", f);
        System.out.println("wrote " + f);
    }
}
