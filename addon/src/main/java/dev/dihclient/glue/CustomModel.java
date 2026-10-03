package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.emote.EmotePose;
import dev.dihclient.model3d.Model;
import dev.dihclient.model3d.ModelLoader;
import dev.dihclient.model3d.Rig;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.modules.render.Freecam;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.ImageLoader;
import dev.dihclient.util.Notifications;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1043;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_156;
import net.minecraft.class_1657;
import net.minecraft.class_1920;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2960;
import net.minecraft.class_4588;
import net.minecraft.class_4608;
import net.minecraft.class_5498;
import net.minecraft.class_761;
import net.minecraft.class_4587.class_4665;

/**
 * Shows your own 3D models (made in Blender, exported as glTF/.glb or .obj) in the game instead of the player model:
 * only on you, on everyone near you, or as statues. Models with animations play "idle" when standing and "walk" when moving.
 * Everything is drawn on the client; the server and other players see nothing.
 *
 * The vanilla player is hidden by shrinking it (see CustomModelMixin) and the model is drawn in the world render pass
 * with the same textured entity layer the Pet module uses for its picture.
 */
public class CustomModel extends Module {
    public enum Target { SELF, EVERYONE, NOBODY }

    private static final class_2960 TEXTURE = class_2960.method_60655("dihclient", "model/custom");
    private static final String EXAMPLE = "example_robot.glb";
    private static final String DEFAULT = "tung_tung_tung_sahur";
    private static final String[] DEFAULT_FILES = {DEFAULT + ".obj", DEFAULT + ".mtl", DEFAULT + ".png"};
    private static final int MAX_STATUES = 16;

    private static CustomModel instance;

    public final StringSetting file = this.text("Model", "File in .minecraft/dihclient/models (.glb .gltf .obj). Empty = Tung Tung Tung Sahur.", DEFAULT + ".obj", 200)
            .onChange(this::reload);
    public final EnumSetting<Target> target = this.mode("Replace",
            "Self: only your player · Everyone: all players near you · Nobody: only the statues.", Target.SELF);
    public final DoubleSetting height = this.dbl("Height", "Height of the model in blocks (a player is 1.8). 0 = keep the size from the file.",
            1.8, 0.0, 8.0, 0.05);
    public final DoubleSetting turn = this.dbl("Rotation", "Turn the model if it looks the wrong way (degrees).", 0.0, -180.0, 180.0, 5.0);
    public final BoolSetting glow = this.bool("Glow", "The model ignores darkness.", false);
    public final BoolSetting animate = this.bool("Animations", "Play the idle and walk animations from the file.", true);
    public final StringSetting idleName = this.text("Idle Animation",
            "Name (or a part of it) of the animation while standing. Empty = find one called idle/stand.", "", 40).onChange(this::pickAnimations)
            .visibleWhen(() -> this.animate.get());
    public final StringSetting walkName = this.text("Walk Animation",
            "Name (or a part of it) of the animation while moving. Empty = find one called walk/run.", "", 40).onChange(this::pickAnimations)
            .visibleWhen(() -> this.animate.get());
    public final DoubleSetting animSpeed = this.dbl("Animation Speed", "Playback speed of the animations.", 1.0, 0.1, 3.0, 0.1)
            .visibleWhen(() -> this.animate.get());
    public final IntSetting range = this.integer("Range", "Everyone: how far away other players get the model (blocks).", 48, 8, 128)
            .visibleWhen(() -> this.target.get() == Target.EVERYONE);
    public final IntSetting maxTris = this.integer("Max Triangles", "Huge models draw only a part of their triangles so the game stays smooth.",
            60000, 2000, 300000);

    private static volatile Model model;
    private static volatile boolean showing;
    private static volatile Target shownTarget = Target.SELF;
    private static volatile int shownRange = 48;
    private volatile int loadToken;
    private Model.Animation idleAnim, walkAnim;
    private final List<double[]> statues = new ArrayList<>();
    private final Map<Integer, Clock> clocks = new HashMap<>();
    private long lastClean;
    private float[] animPos = new float[0], animNrm = new float[0], outPos = new float[0], outNrm = new float[0], globals = new float[0];

    /** Animation clocks of one entity. */
    private static final class Clock {
        double idle, walk;
        long last;
        long seen;
    }

    public CustomModel() {
        super("CustomModel", Category.FUN,
                "Your own 3D model instead of the player: export from Blender as .glb (File > Export > glTF 2.0) or .obj and put it into .minecraft/dihclient/models.");
        instance = this;
        this.action("Open Models Folder", "Opens .minecraft/dihclient/models.", () -> {
            modelDir();
            class_156.method_668().method_672(modelDir().toFile());
        });
        this.action("Next Model", "Switches to the next model in the folder.", this::nextModel);
        this.action("Reload", "Loads the file again after you changed it.", this::reload);
        this.action("Place Statue", "Puts a copy of the model where you stand (it stays until you remove it).", this::placeStatue);
        this.action("Remove Statues", "Removes all statues.", () -> this.statues.clear());
    }

    public static Path modelDir() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("models");
        try {
            boolean fresh = !Files.exists(dir);
            Files.createDirectories(dir);
            if (fresh) {
                copy("/assets/dihclient/models/" + EXAMPLE, dir.resolve(EXAMPLE));
                copy("/assets/dihclient/models/README.txt", dir.resolve("README.txt"));
            }
            for (String f : DEFAULT_FILES) { // the default model is always there, also in folders made by older versions
                copy("/assets/dihclient/models/" + f, dir.resolve(f));
            }
        } catch (IOException ignored) {
            // the folder is only a convenience; loading reports what is missing
        }
        return dir;
    }

    private static void copy(String resource, Path to) throws IOException {
        try (InputStream in = CustomModel.class.getResourceAsStream(resource)) {
            if (in != null && !Files.exists(to)) {
                Files.copy(in, to);
            }
        }
    }

    private static List<Path> files() {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(modelDir())) {
            s.filter(p -> Files.isRegularFile(p) && ModelLoader.supported(p)).sorted().forEach(out::add);
        } catch (IOException ignored) {
            // empty list
        }
        return out;
    }

    private Path chosen() {
        List<Path> all = files();
        String want = this.file.get().trim();
        for (Path p : all) {
            if (p.getFileName().toString().equalsIgnoreCase(want)) {
                return p;
            }
        }
        for (Path p : all) {
            if (p.getFileName().toString().equalsIgnoreCase(DEFAULT + ".obj")) {
                return p;
            }
        }
        return all.isEmpty() ? null : all.get(0);
    }

    private void nextModel() {
        List<Path> all = files();
        if (all.isEmpty()) {
            Notifications.warn(this.name(), "No model in .minecraft/dihclient/models (.glb .gltf .obj)");
            return;
        }
        int at = -1;
        Path cur = this.chosen();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).equals(cur)) {
                at = i;
            }
        }
        this.file.set(all.get((at + 1) % all.size()).getFileName().toString());
    }

    private void placeStatue() {
        if (mc.field_1724 == null) {
            return;
        }
        if (this.statues.size() >= MAX_STATUES) {
            this.statues.remove(0);
        }
        this.statues.add(new double[]{mc.field_1724.method_23317(), mc.field_1724.method_23318(), mc.field_1724.method_23321(),
                mc.field_1724.field_6283});
    }

    @Override
    protected void onEnable() {
        this.reload();
    }

    @Override
    protected void onDisable() {
        this.loadToken++;
        showing = false;
        model = null;
        this.clocks.clear();
    }

    @Override
    public void onWorldChange() {
        this.clocks.clear();
    }

    @Override
    public String getInfo() {
        Model m = model;
        return m == null ? null : m.name;
    }

    private void reload() {
        if (!this.isEnabled() || mc.method_1531() == null) {
            return;
        }
        Path f = this.chosen();
        if (f == null) {
            showing = false;
            model = null;
            Notifications.warn(this.name(), "No model yet. Put a .glb / .gltf / .obj into .minecraft/dihclient/models");
            return;
        }
        int token = ++this.loadToken;
        Thread t = new Thread(() -> {
            try {
                Model m = ModelLoader.load(f);
                mc.execute(() -> this.loaded(token, f, m));
            } catch (Throwable e) {
                mc.execute(() -> this.failed(token, f, e));
            }
        }, "DIHClient-model-loader");
        t.setDaemon(true);
        t.start();
    }

    private void loaded(int token, Path f, Model m) {
        if (token != this.loadToken || !this.isEnabled()) {
            return;
        }
        try {
            mc.method_1531().method_4616(TEXTURE, new class_1043(() -> "dihclient model " + m.name, ImageLoader.toNative(m.texture)));
            smooth(TEXTURE);
            this.clocks.clear();
            model = m;
            this.pickAnimations();
            showing = true;
            String anim = m.animations.isEmpty() ? "" : ", " + m.animations.size() + (m.animations.size() == 1 ? " animation" : " animations");
            Notifications.info(this.name(), "Loaded " + f.getFileName() + " (" + m.triCount + " triangles" + anim + ")");
        } catch (Throwable e) {
            this.failed(token, f, e);
        }
    }

    private void failed(int token, Path f, Throwable e) {
        if (token != this.loadToken) {
            return;
        }
        showing = false;
        model = null;
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        Notifications.error(this.name(), "Could not load " + f.getFileName() + ": " + msg);
        DIHClient.LOG.warn("[DIHClient] model failed: " + f, e);
    }

    /** Asks the texture for linear filtering if this Minecraft version offers it; otherwise it stays pixelated. */
    private static void smooth(class_2960 id) {
        try {
            Object tex = null;
            for (Method g : mc.method_1531().getClass().getMethods()) {
                if (g.getName().equals("method_4619") && g.getParameterCount() == 1 && g.getParameterTypes()[0] == class_2960.class) {
                    tex = g.invoke(mc.method_1531(), id);
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
            // pixelated is fine
        }
    }

    private void pickAnimations() {
        Model m = model;
        this.idleAnim = this.walkAnim = null;
        if (m == null || m.animations.isEmpty()) {
            return;
        }
        Model.Animation idle = m.findAnimation(this.idleName.get());
        Model.Animation walk = m.findAnimation(this.walkName.get());
        if (idle == null) {
            idle = m.guess("idle", "stand", "rest", "breath");
        }
        if (walk == null) {
            walk = m.guess("walk", "run", "move", "step");
        }
        if (m.animations.size() == 1) {
            if (idle == null) {
                idle = m.animations.get(0);
            }
            if (walk == null) {
                walk = m.animations.get(0);
            }
        } else if (walk == null && idle == null) {
            walk = m.animations.get(0);
        } else if (walk == null) {
            walk = idle;
        }
        this.idleAnim = idle;
        this.walkAnim = walk;
    }

    // ---- who gets the model

    private static boolean thirdPerson() {
        return mc.field_1690.method_31044() != class_5498.field_26664 || Freecam.active() != null;
    }

    /** True for the entities that are drawn as the model; the vanilla body is hidden for exactly these. */
    public static boolean replaces(class_1297 e) {
        if (!showing || model == null || mc.field_1724 == null || mc.field_1687 == null || !(e instanceof class_1657 p)) {
            return false;
        }
        Target t = shownTarget;
        if (t == Target.NOBODY) {
            return false;
        }
        if (p == mc.field_1724) {
            return mc.field_1755 == null && thirdPerson();
        }
        if (t != Target.EVERYONE || p.method_7325()) {
            return false;
        }
        double r = shownRange;
        return p.method_5858(mc.field_1724) <= r * r && mc.field_1687.method_18456().contains(p);
    }

    /** Called by the mixin for every living entity that is about to be drawn. */
    public static boolean hides(class_1309 e) {
        try {
            return replaces(e);
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- drawing

    @Override
    public void onTick() {
        shownTarget = this.target.get();
        shownRange = this.range.get();
    }

    @Override
    public void onRender3D(Render3D r) {
        Model m = model;
        if (m == null || !showing || mc.field_1724 == null || mc.field_1687 == null) {
            return;
        }
        shownTarget = this.target.get();
        shownRange = this.range.get();
        long now = System.nanoTime();
        float td = r.tickDelta();
        try {
            if (shownTarget != Target.NOBODY) {
                for (class_1657 p : mc.field_1687.method_18456()) {
                    if (replaces(p)) {
                        this.drawEntity(r, m, p, td, now);
                    }
                }
            }
            for (double[] s : this.statues) {
                this.drawModel(r, m, this.idleAnim, this.statueTime(now), s[0], s[1], s[2], (float) s[3], false, null);
            }
        } catch (Throwable e) {
            DIHClient.LOG.warn("[DIHClient] model draw failed", e);
            showing = false;
            Notifications.error(this.name(), "Drawing failed, model switched off (see log)");
        }
        if (now - this.lastClean > 5_000_000_000L) {
            this.lastClean = now;
            Iterator<Clock> it = this.clocks.values().iterator();
            while (it.hasNext()) {
                if (now - it.next().seen > 5_000_000_000L) {
                    it.remove();
                }
            }
        }
    }

    private double statueTime(long now) {
        return now / 1e9 * this.animSpeed.get();
    }

    private void drawEntity(Render3D r, Model m, class_1657 p, float td, long now) {
        double x = lerp(td, p.field_6038, p.method_23317());
        double y = lerp(td, p.field_5971, p.method_23318());
        double z = lerp(td, p.field_5989, p.method_23321());
        float yaw = lerpAngle(p.field_6220, p.field_6283, td);
        float speed = (float) Math.hypot(p.method_23317() - p.field_6038, p.method_23321() - p.field_5989);
        Model.Animation anim = null;
        double time = 0;
        if (this.animate.get() && m.hasAnimations()) {
            Clock c = this.clocks.computeIfAbsent(p.method_5628(), k -> new Clock());
            double dt = c.last == 0 ? 0 : Math.min(0.1, (now - c.last) / 1e9);
            c.last = now;
            c.seen = now;
            boolean moving = speed > 0.02f;
            double f = this.animSpeed.get() * (moving ? Math.max(0.5, Math.min(2.2, speed / 0.215)) : 1.0);
            if (moving) {
                c.walk += dt * f;
                time = c.walk;
                anim = this.walkAnim;
            } else {
                c.idle += dt * f;
                time = c.idle;
                anim = this.idleAnim;
            }
        }
        this.drawModel(r, m, anim, time, x, y, z, yaw, p.method_5715(), Emotes.poseOf(p));
    }

    private void drawModel(Render3D r, Model m, Model.Animation anim, double time, double x, double y, double z, float yaw,
                           boolean sneaking, EmotePose pose) {
        if (!this.animate.get() || !m.hasAnimations()) {
            anim = null;
        }
        float scale = this.height.get() > 0 ? (float) (this.height.get() / Math.max(0.001f, m.height())) : 1f;
        float rad = Math.max(Math.max(m.maxX - m.minX, m.maxZ - m.minZ), m.maxY - m.minY) * scale;
        if (!r.visible(new class_238(x - rad, y - 0.5, z - rad, x + rad, y + rad + 0.5, z + rad), 0.0)) {
            return;
        }
        int n = m.vertexCount;
        float[] p = m.restPos, nr = m.restNrm;
        if (anim != null) {
            if (this.animPos.length < n * 3) {
                this.animPos = new float[n * 3];
                this.animNrm = new float[n * 3];
            }
            if (this.globals.length < m.nodeCount * 16) {
                this.globals = new float[m.nodeCount * 16];
            }
            Rig.pose(m, anim, time, this.globals);
            Rig.skin(m, this.globals, this.animPos, this.animNrm);
            p = this.animPos;
            nr = this.animNrm;
        }
        if (this.outPos.length < n * 3) {
            this.outPos = new float[n * 3];
            this.outNrm = new float[n * 3];
        }
        class_243 cam = r.camera();
        EmotePose ep = pose == null ? EmotePose.NONE : pose;
        double theta = -Math.toRadians(yaw + this.turn.get() + ep.yaw());
        float c = (float) Math.cos(theta), s = (float) Math.sin(theta);
        float sy = sneaking ? scale * 0.9f : scale;
        double bx = x - cam.field_1352, by = y - cam.field_1351 - m.minY * sy + ep.dy(), bz = z - cam.field_1350;
        float centre = (m.minY + m.maxY) * 0.5f * sy;
        float cr = (float) Math.cos(Math.toRadians(ep.roll())), sr = (float) Math.sin(Math.toRadians(ep.roll()));
        float cp = (float) Math.cos(Math.toRadians(ep.pitch())), sp = (float) Math.sin(Math.toRadians(ep.pitch()));
        boolean tilt = ep.roll() != 0 || ep.pitch() != 0;
        float[] tp = this.outPos, tn = this.outNrm;
        for (int v = 0; v < n; v++) {
            int o = v * 3;
            float px = p[o] * scale, py = p[o + 1] * sy, pz = p[o + 2] * scale;
            float nx0 = nr[o], ny0 = nr[o + 1], nz0 = nr[o + 2];
            if (tilt) { // emote: roll around Z, then pitch around X, both around the middle of the body
                float ly = py - centre;
                float x1 = px * cr - ly * sr, y1 = px * sr + ly * cr;
                float y2 = y1 * cp - pz * sp, z2 = y1 * sp + pz * cp;
                px = x1;
                py = y2 + centre;
                pz = z2;
                float nx1 = nx0 * cr - ny0 * sr, ny1 = nx0 * sr + ny0 * cr;
                float ny2 = ny1 * cp - nz0 * sp, nz2 = ny1 * sp + nz0 * cp;
                nx0 = nx1;
                ny0 = ny2;
                nz0 = nz2;
            }
            tp[o] = (float) (bx + px * c + pz * s);
            tp[o + 1] = (float) (by + py);
            tp[o + 2] = (float) (bz - px * s + pz * c);
            tn[o] = nx0 * c + nz0 * s;
            tn[o + 1] = ny0;
            tn[o + 2] = -nx0 * s + nz0 * c;
        }
        int light = this.glow.get() ? 15728880 : class_761.method_23794((class_1920) mc.field_1687, class_2338.method_49637(x, y + 0.5, z));
        int tris = m.triCount;
        int limit = this.maxTris.get();
        int stride = tris > limit ? (tris + limit - 1) / limit : 1;
        class_4588 buf = r.texturedBuffer(TEXTURE);
        class_4665 entry = r.matrixEntry();
        int[] idx = m.idx;
        for (int t = 0; t < tris; t += stride) {
            int a = idx[t * 3], b = idx[t * 3 + 1], d = idx[t * 3 + 2];
            int ao = a * 3, bo = b * 3, dofs = d * 3;
            float ux = tp[bo] - tp[ao], uy = tp[bo + 1] - tp[ao + 1], uz = tp[bo + 2] - tp[ao + 2];
            float wx = tp[dofs] - tp[ao], wy = tp[dofs + 1] - tp[ao + 1], wz = tp[dofs + 2] - tp[ao + 2];
            float gx = uy * wz - uz * wy, gy = uz * wx - ux * wz, gz = ux * wy - uy * wx;
            boolean front = gx * tp[ao] + gy * tp[ao + 1] + gz * tp[ao + 2] < 0;
            float sign = front ? 1f : -1f;
            int second = front ? b : d, third = front ? d : b;
            this.vertex(buf, entry, m, tp, tn, a, sign, light);
            this.vertex(buf, entry, m, tp, tn, second, sign, light);
            this.vertex(buf, entry, m, tp, tn, third, sign, light);
            this.vertex(buf, entry, m, tp, tn, third, sign, light);
        }
    }

    private void vertex(class_4588 buf, class_4665 entry, Model m, float[] tp, float[] tn, int v, float sign, int light) {
        int o = v * 3;
        buf.method_56824(entry, tp[o], tp[o + 1], tp[o + 2])
                .method_39415(m.color[v])
                .method_22913(m.uv[v * 2], m.uv[v * 2 + 1])
                .method_22922(class_4608.field_21444)
                .method_60803(light)
                .method_60831(entry, tn[o] * sign, tn[o + 1] * sign, tn[o + 2] * sign);
    }

    private static double lerp(float t, double a, double b) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        float d = (((b - a) % 360f) + 540f) % 360f - 180f;
        return a + d * t;
    }

    static CustomModel instance() {
        return instance;
    }
}
