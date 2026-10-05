import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class MixinCheck {
    public static void main(String[] args) throws Exception {
        int problems = 0;
        int accessors = 0;
        try (ZipFile z = new ZipFile(args[0])) {
            for (var en = z.entries(); en.hasMoreElements();) {
                ZipEntry e = en.nextElement();
                if (!e.getName().startsWith("dev/dihclient/mixin/") || !e.getName().endsWith(".class")) {
                    continue;
                }
                ClassNode cn = new ClassNode();
                new ClassReader(z.getInputStream(e)).accept(cn, 0);
                boolean isInterface = (cn.access & Opcodes.ACC_INTERFACE) != 0;
                for (MethodNode m : cn.methods) {
                    boolean synthetic = (m.access & Opcodes.ACC_SYNTHETIC) != 0;
                    if (isInterface && !synthetic && !has(m.visibleAnnotations, "gen/Accessor;", "gen/Invoker;")) {
                        System.out.println("PROBLEM " + cn.name + "." + m.name + " is not a visible @Accessor/@Invoker, Mixin would treat "
                                + "the interface as a normal interface mixin");
                        problems++;
                    }
                    if (m.invisibleAnnotations != null) {
                        for (AnnotationNode an : m.invisibleAnnotations) {
                            if (an.desc.startsWith("Lorg/spongepowered/asm/mixin/") && !an.desc.contains("Intrinsic") && !an.desc.contains("Implements")
                                    && !an.desc.contains("Pseudo") && !an.desc.contains("Dynamic") && !an.desc.contains("Debug")) {
                                System.out.println("PROBLEM " + cn.name + "." + m.name + " has " + an.desc + " as RuntimeInvisible, Mixin ignores it");
                                problems++;
                            }
                        }
                    }
                }
                if (isInterface) {
                    accessors++;
                }
            }
        }
        System.out.println("MixinCheck: " + accessors + " accessor interfaces, " + problems + " problems");
        System.exit(problems == 0 ? 0 : 1);
    }

    private static boolean has(List<AnnotationNode> list, String... endings) {
        if (list == null) {
            return false;
        }
        for (AnnotationNode an : list) {
            for (String s : endings) {
                if (an.desc.startsWith("Lorg/spongepowered/asm/mixin/") && an.desc.endsWith(s)) {
                    return true;
                }
            }
        }
        return false;
    }
}
