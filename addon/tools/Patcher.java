import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Builds the new DIHClient jar from the old one: hooks three existing methods and adds the compiled add-on classes.
 *
 * usage: Patcher base.jar addonClassesDir out.jar newVersion [bundledPack.zip [overrideClassesDir [resourcesDir]]]
 */
public final class Patcher {
    private static final String HOOKS = "dev/dihclient/glue/AddonHooks";

    public static void main(String[] args) throws Exception {
        Path base = Path.of(args[0]);
        Path addon = Path.of(args[1]);
        Path out = Path.of(args[2]);
        String version = args[3];

        java.util.Map<String, byte[]> over = new java.util.TreeMap<>();
        if (args.length > 5) {
            Path od = Path.of(args[5]);
            try (Stream<Path> s = Files.walk(od)) {
                for (Path p : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".class"))::iterator) {
                    over.put(od.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
                }
            }
        }
        java.util.Map<String, byte[]> resources = new java.util.TreeMap<>();
        if (args.length > 6) {
            Path rd = Path.of(args[6]);
            try (Stream<Path> s = Files.walk(rd)) {
                for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                    resources.put(rd.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
                }
            }
        }
        List<String> added = new ArrayList<>();
        try (Stream<Path> s = Files.walk(addon)) {
            s.filter(p -> p.toString().endsWith(".class")).forEach(p -> added.add(addon.relativize(p).toString().replace('\\', '/')));
        }
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (ZipFile zin = new ZipFile(base.toFile()); ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(out))) {
            boolean dih = false, mm = false, mod = false;
            Enumeration<? extends ZipEntry> en = zin.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (added.contains(e.getName())) {
                    throw new IllegalStateException("add-on class would overwrite " + e.getName());
                }
                byte[] data;
                try (InputStream in = zin.getInputStream(e)) {
                    data = in.readAllBytes();
                }
                if (over.containsKey(e.getName())) {
                    data = over.remove(e.getName());
                    System.out.println("replaced: " + e.getName());
                    ZipEntry oe = new ZipEntry(e.getName());
                    oe.setTime(e.getTime());
                    zout.putNextEntry(oe);
                    zout.write(data);
                    zout.closeEntry();
                    continue;
                }
                switch (e.getName()) {
                    case "dev/dihclient/DIHClient.class" -> {
                        data = patchDihClient(data);
                        dih = true;
                    }
                    case "dev/dihclient/module/ModuleManager.class" -> {
                        data = patchModuleManager(data);
                        mm = true;
                    }
                    case "dihclient.mixins.json" -> {
                        String cfg = new String(data, StandardCharsets.UTF_8);
                        for (String gone : new String[]{"PauseMenuMixin", "PauseMouseMixin", "PauseKeyboardMixin"}) {
                            String before = cfg;
                            cfg = cfg.replaceAll("\\s*\"" + gone + "\",?", "");
                            if (cfg.equals(before)) {
                                throw new IllegalStateException(gone + " not found in dihclient.mixins.json");
                            }
                        }
                        String withFallback = cfg.replace("\"KeyboardInputMixin\",", "\"KeyboardInputMixin\",\n    \"InputFallbackMixin\",\n    \"KeyBindingSoftMixin\",\n    \"DummyHitMixin\",\n    \"CustomModelMixin\",");
                        if (withFallback.equals(cfg)) {
                            throw new IllegalStateException("KeyboardInputMixin not found in dihclient.mixins.json");
                        }
                        data = withFallback.getBytes(StandardCharsets.UTF_8);
                    }
                    case "fabric.mod.json" -> {
                        String json = new String(data, StandardCharsets.UTF_8);
                        String patched = json.replaceFirst("\"version\"\\s*:\\s*\"[^\"]*\"", "\"version\": \"" + version + "\"");
                        if (patched.equals(json)) {
                            throw new IllegalStateException("version not found in fabric.mod.json");
                        }
                        data = patched.getBytes(StandardCharsets.UTF_8);
                        mod = true;
                    }
                    default -> {
                        if (e.getName().startsWith("dev/dihclient/mixin/") && e.getName().endsWith(".class")) {
                            data = fixMixinAnnotations(data);
                        }
                        if (e.getName().startsWith("dev/dihclient/autobuild/Worker") || e.getName().startsWith("dev/dihclient/autobuild/BuildRuntime")) {
                            data = routeInteract(data, e.getName());
                        }
                        if (e.getName().equals("dev/dihclient/autobuild/BuildRuntime.class")) {
                            data = redirectWalk(data);
                        }
                        if (e.getName().equals("dev/dihclient/modules/client/Profiles.class")) {
                            data = renameModule(data, "Profiles", "Configs");
                        }
                        if (e.getName().equals("dev/dihclient/gui/MeteorGuiScreen.class") || e.getName().equals("dev/dihclient/gui/ClickGuiScreen.class")) {
                            data = routeClickSound(data, e.getName());
                        }
                        if (e.getName().equals("dev/dihclient/util/Notifications.class")) {
                            data = toggleSoundInNotifications(data);
                        }
                        if (e.getName().equals("dev/dihclient/modules/world/AutoBuild.class")) {
                            data = enforceBeforeTick(data);
                        }
                        if (e.getName().equals("dev/dihclient/mixin/PlayerEntityRendererMixin.class")) {
                            data = raiseMixinPriority(data, 2000);
                        }
                    }
                }
                ZipEntry ne = new ZipEntry(e.getName());
                ne.setTime(e.getTime());
                zout.putNextEntry(ne);
                zout.write(data);
                zout.closeEntry();
            }
            if (routed < 4) {
                throw new IllegalStateException("fewer block clicks routed than expected: " + routed);
            }
            if (!(dih && mm && mod)) {
                throw new IllegalStateException("base jar is missing an expected file");
            }
            if (args.length > 4) {
                ZipEntry pe = new ZipEntry("dihclient/DIH-Visuals.zip");
                pe.setTime(zin.getEntry("fabric.mod.json").getTime());
                zout.putNextEntry(pe);
                zout.write(Files.readAllBytes(Path.of(args[4])));
                zout.closeEntry();
            }
            for (var re : resources.entrySet()) {
                if (zin.getEntry(re.getKey()) != null) {
                    throw new IllegalStateException("resource would overwrite " + re.getKey());
                }
                System.out.println("resource: " + re.getKey());
                ZipEntry ne = new ZipEntry(re.getKey());
                ne.setTime(zin.getEntry("fabric.mod.json").getTime());
                zout.putNextEntry(ne);
                zout.write(re.getValue());
                zout.closeEntry();
            }
            for (var oe : over.entrySet()) {
                System.out.println("new class from override: " + oe.getKey());
                ZipEntry ne = new ZipEntry(oe.getKey());
                ne.setTime(zin.getEntry("fabric.mod.json").getTime());
                zout.putNextEntry(ne);
                zout.write(oe.getValue());
                zout.closeEntry();
            }
            for (String name : added) {
                ZipEntry ne = new ZipEntry(name);
                ne.setTime(zin.getEntry("fabric.mod.json").getTime());
                zout.putNextEntry(ne);
                zout.write(Files.readAllBytes(addon.resolve(name)));
                zout.closeEntry();
            }
        }
        System.out.println("wrote " + out + " (" + added.size() + " add-on classes)");
    }

    private static byte[] patchDihClient(byte[] data) {
        ClassNode cn = read(data);
        MethodNode init = method(cn, "onInitializeClient", "()V");
        MethodNode tick = method(cn, "onEndTick", "(Lnet/minecraft/class_310;)V");
        requireFresh(init);
        requireFresh(tick);
        // init: call the hook just before every return
        for (AbstractInsnNode n : init.instructions.toArray()) {
            if (n.getOpcode() == Opcodes.RETURN) {
                init.instructions.insertBefore(n, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "init", "()V", false));
            }
        }
        // tick: call the hook first thing, with the client argument
        InsnList pre = new InsnList();
        pre.add(new VarInsnNode(Opcodes.ALOAD, 0));
        pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "tick", "(Lnet/minecraft/class_310;)V", false));
        tick.instructions.insert(pre);
        return write(cn);
    }

    private static byte[] patchModuleManager(byte[] data) {
        ClassNode cn = read(data);
        MethodNode reg = method(cn, "registerAll", "()V");
        requireFresh(reg);
        for (AbstractInsnNode n : reg.instructions.toArray()) {
            if (n.getOpcode() == Opcodes.RETURN) {
                InsnList call = new InsnList();
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "registerModules", "(Ldev/dihclient/module/ModuleManager;)V", false));
                reg.instructions.insertBefore(n, call);
            }
        }
        return write(cn);
    }

    /** Mixin annotations that Mixin only reads when they are RuntimeVisible. */
    private static final java.util.Set<String> RUNTIME_MIXIN_ANNOTATIONS = java.util.Set.of(
            "Lorg/spongepowered/asm/mixin/gen/Accessor;", "Lorg/spongepowered/asm/mixin/gen/Invoker;",
            "Lorg/spongepowered/asm/mixin/injection/Inject;", "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;", "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;", "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
            "Lorg/spongepowered/asm/mixin/Overwrite;", "Lorg/spongepowered/asm/mixin/Shadow;", "Lorg/spongepowered/asm/mixin/Unique;",
            "Lorg/spongepowered/asm/mixin/Final;", "Lorg/spongepowered/asm/mixin/Mutable;");

    /**
     * 5.6 shipped ProfilerAccessor and SpawnerPieMixin with their @Accessor / @ModifyVariable annotations marked
     * RuntimeInvisible. Mixin does not see those: it takes the accessor interface for a normal interface mixin and
     * aborts the whole game start ("@Mixin target type mismatch: class_310 is not an interface").
     * Moves such annotations to the visible list, which is what the compiler produces for these annotations.
     */
    static byte[] fixMixinAnnotations(byte[] data) {
        ClassNode cn = read(data);
        boolean changed = false;
        for (MethodNode m : cn.methods) {
            if (m.invisibleAnnotations == null) {
                continue;
            }
            java.util.Iterator<AnnotationNode> it = m.invisibleAnnotations.iterator();
            while (it.hasNext()) {
                AnnotationNode an = it.next();
                if (RUNTIME_MIXIN_ANNOTATIONS.contains(an.desc)) {
                    it.remove();
                    if (m.visibleAnnotations == null) {
                        m.visibleAnnotations = new ArrayList<>();
                    }
                    m.visibleAnnotations.add(an);
                    changed = true;
                    System.out.println("fixed " + cn.name + "." + m.name + ": " + an.desc + " is now visible");
                }
            }
            if (m.invisibleAnnotations.isEmpty()) {
                m.invisibleAnnotations = null;
            }
        }
        if (!changed) {
            return data;
        }
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /**
     * Cape mods (Better Capes, WaveyCapes) hook the same render-state method. Mixin applies higher priorities later, so a
     * higher number lets the DIHClient cape be the last word when it is switched on.
     */
    static byte[] raiseMixinPriority(byte[] data, int priority) {
        ClassNode cn = read(data);
        List<AnnotationNode> all = new ArrayList<>();
        if (cn.invisibleAnnotations != null) {
            all.addAll(cn.invisibleAnnotations);
        }
        if (cn.visibleAnnotations != null) {
            all.addAll(cn.visibleAnnotations);
        }
        for (AnnotationNode an : all) {
            if (an.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                if (an.values == null) {
                    an.values = new ArrayList<>();
                }
                for (int i = 0; i < an.values.size(); i += 2) {
                    if ("priority".equals(an.values.get(i))) {
                        throw new IllegalStateException("priority already set");
                    }
                }
                an.values.add("priority");
                an.values.add(priority);
                System.out.println("priority " + priority + " for " + cn.name);
                ClassWriter cw = new ClassWriter(0);
                cn.accept(cw);
                return cw.toByteArray();
            }
        }
        throw new IllegalStateException("@Mixin not found in " + cn.name);
    }

    /** Replaces the module name given to super(...) in the constructor. */
    static byte[] renameModule(byte[] data, String from, String to) {
        ClassNode cn = read(data);
        int n = 0;
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("<init>")) {
                continue;
            }
            for (AbstractInsnNode in : m.instructions.toArray()) {
                if (in instanceof org.objectweb.asm.tree.LdcInsnNode ldc && from.equals(ldc.cst)) {
                    ldc.cst = to;
                    n++;
                }
            }
        }
        if (n != 1) {
            throw new IllegalStateException("expected one \"" + from + "\" in constructor of " + cn.name + ", found " + n);
        }
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /** The GUI click goes through GuiSounds, which plays the mod's own click sound. */
    static byte[] routeClickSound(byte[] data, String name) {
        ClassNode cn = read(data);
        int n = 0;
        for (MethodNode m : cn.methods) {
            for (AbstractInsnNode in : m.instructions.toArray()) {
                if (in instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKESTATIC && mi.owner.equals("net/minecraft/class_1109")
                        && mi.name.equals("method_47978") && mi.desc.equals("(Lnet/minecraft/class_6880;F)Lnet/minecraft/class_1109;")) {
                    m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/dihclient/glue/GuiSounds", "click",
                            "(Lnet/minecraft/class_6880;F)Lnet/minecraft/class_1109;", false));
                    n++;
                }
            }
        }
        if (n != 1) {
            throw new IllegalStateException("expected one click sound in " + name + ", found " + n);
        }
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /** Notifications.toggle(Module) is called whenever a module is switched on or off by the player. */
    static byte[] toggleSoundInNotifications(byte[] data) {
        ClassNode cn = read(data);
        MethodNode m = method(cn, "toggle", "(Ldev/dihclient/module/Module;)V");
        InsnList pre = new InsnList();
        pre.add(new VarInsnNode(Opcodes.ALOAD, 0));
        pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/dihclient/glue/GuiSounds", "toggle", "(Ldev/dihclient/module/Module;)V", false));
        m.instructions.insert(pre);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static final String LEGAL = "dev/dihclient/glue/LegalPlace";
    private static int routed;

    /** Every block click of the build code goes through LegalPlace.interact, which only lets legal clicks pass. */
    static byte[] routeInteract(byte[] data, String name) {
        ClassNode cn = read(data);
        int n = 0;
        for (MethodNode m : cn.methods) {
            for (AbstractInsnNode in : m.instructions.toArray()) {
                if (in instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && mi.owner.equals("net/minecraft/class_636") && mi.name.equals("method_2896")) {
                    m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, LEGAL, "interact",
                            "(Lnet/minecraft/class_636;Lnet/minecraft/class_746;Lnet/minecraft/class_1268;Lnet/minecraft/class_3965;)Lnet/minecraft/class_1269;", false));
                    n++;
                }
            }
        }
        if (n == 0) {
            return data;
        }
        routed += n;
        System.out.println("legal placement gate: " + n + " click(s) in " + name);
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static final String PILOT = "dev/dihclient/autobuild/BuildPilot";
    private static final String WALK_DESC = "(Ldev/dihclient/autobuild/BuildRuntime;Lnet/minecraft/class_243;D)V";

    /**
     * The walk to the next layer (second walkTo call in tick) and the walk to a re-position spot (tickReposition) go through
     * BuildPilot, which plans a real path and falls back to the old walkTo by itself. The walk to a restock chest and the
     * walk out of the cleanup stay as they were.
     */
    static byte[] redirectWalk(byte[] data) {
        ClassNode cn = read(data);
        int layer = 0, point = 0;
        for (MethodNode m : cn.methods) {
            boolean tick = m.name.equals("tick") && m.desc.equals("(Ldev/dihclient/autobuild/BuildRuntime$Settings;)V");
            boolean reposition = m.name.equals("tickReposition");
            if (!tick && !reposition) {
                continue;
            }
            int seen = 0;
            for (AbstractInsnNode in : m.instructions.toArray()) {
                if (in instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKEVIRTUAL && mi.name.equals("walkTo")
                        && mi.owner.equals("dev/dihclient/autobuild/BuildRuntime") && mi.desc.equals("(Lnet/minecraft/class_243;D)V")) {
                    seen++;
                    if (tick && seen == 2) {
                        m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, PILOT, "walkLayer", WALK_DESC, false));
                        layer++;
                    } else if (reposition) {
                        m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, PILOT, "walkPoint", WALK_DESC, false));
                        point++;
                    }
                }
            }
        }
        if (layer != 1 || point != 1) {
            throw new IllegalStateException("BuildRuntime walkTo call sites not as expected: layer " + layer + ", point " + point);
        }
        System.out.println("smart path: walk calls redirected in BuildRuntime");
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /** AutoBuild takes back its illegal options right before the build runtime ticks. */
    static byte[] enforceBeforeTick(byte[] data) {
        ClassNode cn = read(data);
        int n = 0;
        for (MethodNode m : cn.methods) {
            for (AbstractInsnNode in : m.instructions.toArray()) {
                if (in instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && mi.owner.equals("dev/dihclient/autobuild/BuildRuntime") && mi.name.equals("tick")
                        && mi.desc.equals("(Ldev/dihclient/autobuild/BuildRuntime$Settings;)V")) {
                    InsnList call = new InsnList();
                    call.add(new InsnNode(Opcodes.DUP));   // stack: runtime, settings -> runtime, settings, settings
                    call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LEGAL, "enforce", "(Ldev/dihclient/autobuild/BuildRuntime$Settings;)V", false));
                    m.instructions.insertBefore(mi, call);
                    n++;
                }
            }
        }
        if (n != 1) {
            throw new IllegalStateException("expected exactly one BuildRuntime.tick call in AutoBuild, found " + n);
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static void requireFresh(MethodNode m) {
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof MethodInsnNode mi && mi.owner.equals(HOOKS)) {
                throw new IllegalStateException(m.name + " is already patched");
            }
        }
    }

    private static MethodNode method(ClassNode cn, String name, String desc) {
        for (MethodNode m : cn.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return m;
            }
        }
        throw new IllegalStateException("method not found: " + cn.name + "." + name + desc);
    }

    private static ClassNode read(byte[] data) {
        ClassNode cn = new ClassNode();
        new ClassReader(data).accept(cn, 0);
        return cn;
    }

    private static byte[] write(ClassNode cn) {
        // the inserted code has no branches, so existing frames stay valid and only the stack size has to be recomputed
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }
}
