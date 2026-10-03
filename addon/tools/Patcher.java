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
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Builds the new DIHClient jar from the old one: hooks three existing methods and adds the compiled add-on classes.
 *
 * usage: Patcher base.jar addonClassesDir out.jar newVersion
 */
public final class Patcher {
    private static final String HOOKS = "dev/dihclient/glue/AddonHooks";

    public static void main(String[] args) throws Exception {
        Path base = Path.of(args[0]);
        Path addon = Path.of(args[1]);
        Path out = Path.of(args[2]);
        String version = args[3];

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
                switch (e.getName()) {
                    case "dev/dihclient/DIHClient.class" -> {
                        data = patchDihClient(data);
                        dih = true;
                    }
                    case "dev/dihclient/module/ModuleManager.class" -> {
                        data = patchModuleManager(data);
                        mm = true;
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
                    default -> { }
                }
                ZipEntry ne = new ZipEntry(e.getName());
                ne.setTime(e.getTime());
                zout.putNextEntry(ne);
                zout.write(data);
                zout.closeEntry();
            }
            if (!(dih && mm && mod)) {
                throw new IllegalStateException("base jar is missing an expected file");
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
