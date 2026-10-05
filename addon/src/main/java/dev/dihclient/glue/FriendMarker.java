package dev.dihclient.glue;

import dev.dihclient.model3d.Model;
import dev.dihclient.model3d.ModelLoader;
import dev.dihclient.render.Render3D;
import dev.dihclient.util.ImageLoader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.class_1043;
import net.minecraft.class_1920;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_4588;
import net.minecraft.class_4608;
import net.minecraft.class_761;
import net.minecraft.class_4587.class_4665;

public final class FriendMarker {
    private static final class_2960 TEXTURE = class_2960.method_60655("dihclient", "model/friend");
    private static final String FILE = "tung_tung_tung_sahur.obj";

    private static volatile Model model;
    private static volatile boolean loading;
    private static volatile boolean failed;
    private static float[] outPos = new float[0];
    private static float[] outNrm = new float[0];

    private FriendMarker() {
    }

    public static boolean ready() {
        return model != null;
    }

    private static void load() {
        if (loading || failed || model != null) {
            return;
        }
        loading = true;
        Thread t = new Thread(() -> {
            try {
                Path dir = modelDir();
                Path f = dir.resolve(FILE);
                if (!Files.isRegularFile(f)) {
                    failed = true;
                    return;
                }
                Model m = ModelLoader.load(f);
                class_310 mc = class_310.method_1551();
                mc.execute(() -> {
                    try {
                        mc.method_1531().method_4616(TEXTURE, new class_1043(() -> "dihclient friend", ImageLoader.toNative(m.texture)));
                        smooth();
                        model = m;
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

    private static Path modelDir() throws java.io.IOException {
        Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("models");
        Files.createDirectories(dir);
        for (String f : new String[] {"tung_tung_tung_sahur.obj", "tung_tung_tung_sahur.mtl", "tung_tung_tung_sahur.png"}) {
            Path to = dir.resolve(f);
            if (!Files.exists(to)) {
                try (java.io.InputStream in = FriendMarker.class.getResourceAsStream("/assets/dihclient/models/" + f)) {
                    if (in != null) {
                        Files.copy(in, to);
                    }
                }
            }
        }
        return dir;
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

    public static boolean draw(Render3D r, double x, double y, double z, double height) {
        Model m = model;
        if (m == null) {
            load();
            return false;
        }
        class_310 mc = class_310.method_1551();
        float scale = (float) (height / Math.max(0.001f, m.height()));
        float rad = Math.max(Math.max(m.maxX - m.minX, m.maxZ - m.minZ), m.maxY - m.minY) * scale;
        double time = System.currentTimeMillis() / 1000.0;
        double bob = Math.sin(time * 1.6) * 0.25;
        if (!r.visible(new net.minecraft.class_238(x - rad, y - 0.5, z - rad, x + rad, y + rad + 1.5, z + rad), 0.0)) {
            return true;
        }
        int n = m.vertexCount;
        float[] p = m.restPos;
        float[] nr = m.restNrm;
        if (outPos.length < n * 3) {
            outPos = new float[n * 3];
            outNrm = new float[n * 3];
        }
        class_243 cam = r.camera();
        double face = Math.atan2(cam.field_1352 - x, cam.field_1350 - z) + Math.sin(time * 0.9) * 0.12;
        float c = (float) Math.cos(face);
        float s = (float) Math.sin(face);
        double bx = x - cam.field_1352;
        double by = y + bob - cam.field_1351 - m.minY * scale;
        double bz = z - cam.field_1350;
        float[] tp = outPos;
        float[] tn = outNrm;
        for (int v = 0; v < n; v++) {
            int o = v * 3;
            float px = p[o] * scale;
            float py = p[o + 1] * scale;
            float pz = p[o + 2] * scale;
            tp[o] = (float) (bx + px * c + pz * s);
            tp[o + 1] = (float) (by + py);
            tp[o + 2] = (float) (bz - px * s + pz * c);
            tn[o] = nr[o] * c + nr[o + 2] * s;
            tn[o + 1] = nr[o + 1];
            tn[o + 2] = -nr[o] * s + nr[o + 2] * c;
        }
        int light = 15728880;
        class_4588 buf = r.texturedBuffer(TEXTURE);
        class_4665 entry = r.matrixEntry();
        int[] idx = m.idx;
        int tris = m.triCount;
        int limit = 6000;
        int stride = tris > limit ? (tris + limit - 1) / limit : 1;
        for (int t = 0; t < tris; t += stride) {
            int a = idx[t * 3];
            int b = idx[t * 3 + 1];
            int d = idx[t * 3 + 2];
            int ao = a * 3;
            int bo = b * 3;
            int dofs = d * 3;
            float ux = tp[bo] - tp[ao];
            float uy = tp[bo + 1] - tp[ao + 1];
            float uz = tp[bo + 2] - tp[ao + 2];
            float wx = tp[dofs] - tp[ao];
            float wy = tp[dofs + 1] - tp[ao + 1];
            float wz = tp[dofs + 2] - tp[ao + 2];
            float gx = uy * wz - uz * wy;
            float gy = uz * wx - ux * wz;
            float gz = ux * wy - uy * wx;
            boolean front = gx * tp[ao] + gy * tp[ao + 1] + gz * tp[ao + 2] < 0;
            float sign = front ? 1f : -1f;
            int second = front ? b : d;
            int third = front ? d : b;
            vertex(buf, entry, m, tp, tn, a, sign, light);
            vertex(buf, entry, m, tp, tn, second, sign, light);
            vertex(buf, entry, m, tp, tn, third, sign, light);
            vertex(buf, entry, m, tp, tn, third, sign, light);
        }
        return true;
    }

    private static void vertex(class_4588 buf, class_4665 entry, Model m, float[] tp, float[] tn, int v, float sign, int light) {
        int o = v * 3;
        buf.method_56824(entry, tp[o], tp[o + 1], tp[o + 2])
                .method_39415(m.color[v])
                .method_22913(m.uv[v * 2], m.uv[v * 2 + 1])
                .method_22922(class_4608.field_21444)
                .method_60803(light)
                .method_60831(entry, tn[o] * sign, tn[o + 1] * sign, tn[o + 2] * sign);
    }
}
