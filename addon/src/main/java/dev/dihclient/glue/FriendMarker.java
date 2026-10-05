package dev.dihclient.glue;

import dev.dihclient.render.Render3D;
import dev.dihclient.util.ImageLoader;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1043;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_4588;
import net.minecraft.class_4608;
import net.minecraft.class_4587.class_4665;

public final class FriendMarker {
    private static final class_2960 TEXTURE = class_2960.method_60655("dihclient", "model/friend_photo");

    private static volatile boolean ready;
    private static volatile boolean loading;
    private static volatile boolean failed;

    private FriendMarker() {
    }

    private static BufferedImage read() throws Exception {
        Path custom = FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("friend.png");
        if (Files.isRegularFile(custom)) {
            return ImageIO.read(custom.toFile());
        }
        try (InputStream in = FriendMarker.class.getResourceAsStream("/assets/dihclient/friend/friend.png")) {
            return in == null ? null : ImageIO.read(in);
        }
    }

    private static void load() {
        if (loading || failed || ready) {
            return;
        }
        loading = true;
        Thread t = new Thread(() -> {
            try {
                BufferedImage img = read();
                if (img == null) {
                    failed = true;
                    return;
                }
                class_310 mc = class_310.method_1551();
                mc.execute(() -> {
                    try {
                        mc.method_1531().method_4616(TEXTURE, new class_1043(() -> "dihclient friend photo", ImageLoader.toNative(img)));
                        smooth();
                        ready = true;
                    } catch (Throwable e) {
                        failed = true;
                    }
                });
            } catch (Throwable e) {
                failed = true;
            }
        }, "DIHClient-friend-loader");
        t.setDaemon(true);
        t.start();
    }

    private static void smooth() {
        try {
            class_310 mc = class_310.method_1551();
            Object tex = null;
            for (Method g : mc.method_1531().getClass().getMethods()) {
                if (g.getName().equals("method_4619") && g.getParameterCount() == 1 && g.getParameterTypes()[0] == class_2960.class) {
                    tex = g.invoke(mc.method_1531(), TEXTURE);
                }
            }
            if (tex == null) {
                return;
            }
            for (Method m : tex.getClass().getMethods()) {
                if (m.getName().equals("method_4527") && m.getParameterCount() == 2 && m.getParameterTypes()[0] == boolean.class) {
                    m.invoke(tex, true, false);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean draw(Render3D r, double x, double y, double z, double size) {
        if (!ready) {
            load();
            return false;
        }
        double time = System.currentTimeMillis() / 1000.0;
        double bob = Math.sin(time * 1.6) * 0.3;
        double half = size / 2;
        if (!r.visible(new class_238(x - half, y - 0.5, z - half, x + half, y + size + 1.0, z + half), 0.0)) {
            return true;
        }
        class_243 cam = r.camera();
        double dx = cam.field_1352 - x;
        double dz = cam.field_1350 - z;
        double len = Math.max(0.0001, Math.hypot(dx, dz));
        float rx = (float) (dz / len);
        float rz = (float) (-dx / len);
        float nx = (float) (dx / len);
        float nz = (float) (dz / len);
        float bx = (float) (x - cam.field_1352);
        float by = (float) (y + bob - cam.field_1351);
        float bz = (float) (z - cam.field_1350);
        float hw = (float) half;
        float h = (float) size;
        class_4588 buf = r.texturedBuffer(TEXTURE);
        class_4665 entry = r.matrixEntry();
        int light = 15728880;
        quad(buf, entry, bx - rx * hw, by, bz - rz * hw, bx + rx * hw, by, bz + rz * hw, bx + rx * hw, by + h, bz + rz * hw,
                bx - rx * hw, by + h, bz - rz * hw, nx, nz, light);
        return true;
    }

    private static void quad(class_4588 b, class_4665 e, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2,
                             float z2, float x3, float y3, float z3, float nx, float nz, int light) {
        vertex(b, e, x0, y0, z0, 0f, 1f, nx, nz, light);
        vertex(b, e, x1, y1, z1, 1f, 1f, nx, nz, light);
        vertex(b, e, x2, y2, z2, 1f, 0f, nx, nz, light);
        vertex(b, e, x3, y3, z3, 0f, 0f, nx, nz, light);
    }

    private static void vertex(class_4588 buf, class_4665 entry, float x, float y, float z, float u, float v, float nx, float nz, int light) {
        buf.method_56824(entry, x, y, z)
                .method_39415(-1)
                .method_22913(u, v)
                .method_22922(class_4608.field_21444)
                .method_60803(light)
                .method_60831(entry, nx, 0f, nz);
    }
}
