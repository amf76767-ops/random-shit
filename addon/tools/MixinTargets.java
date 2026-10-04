import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Static check of what Mixin would do at game start, without starting the game. For every Mixin class it checks against the
 * real target classes (mc-int.jar, the base jar, ...):
 * <ul>
 * <li>the @Mixin targets exist and their kind (interface / class) fits the mixin kind (accessor interface, interface mixin, normal);
 * <li>@Inject/@Redirect/@ModifyVariable/@ModifyArg(s)/@ModifyConstant/@Overwrite (and MixinExtras @WrapOperation etc.): the {@code method}
 * is declared in the target class itself (Mixin does not look at super classes), the {@code @At} INVOKE/FIELD/NEW target occurs in its
 * bytecode, the handler descriptor is one Mixin accepts;
 * <li>@Shadow fields/methods and @Accessor/@Invoker targets are declared in the target class with the exact descriptor;
 * <li>smaller things Mixin rejects: wrong super class, method conflicts, non-interface mixin classes referenced from other code.
 * </ul>
 * Each problem is tagged: {@code [crash]} = Mixin throws and the game does not start, {@code [inert]} = the injection silently does
 * nothing (injectors.defaultRequire is 0), {@code [runtime]} = fails when the handler runs.
 *
 * usage: MixinTargets [-v] [--only text] [--ignore text]... mixinSource [classpath...]
 *   --only text    check only mixin classes whose name contains text
 *   --ignore text  report a problem whose line contains text as a note instead (known, accepted problems)
 *   mixinSource  directory (build/classes) or jar that holds dev/dihclient/mixin/**; also the first class path entry
 *   classpath    more directories/jars searched in order for target classes (build/override, base jar, mc-int.jar ...)
 * Exit code 1 when a problem was found.
 */
public final class MixinTargets {
    private static final String MIXIN_PACKAGE = "dev/dihclient/mixin/";
    private static final String MX = "Lorg/spongepowered/asm/mixin/";
    private static final String INJ = MX + "injection/";
    private static final String MIXIN_EXTRAS = "Lcom/llamalad7/mixinextras/";
    private static final Type CI = Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/CallbackInfo");
    private static final Type CIR = Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable");
    private static final Pattern OBJ_TYPE = Pattern.compile("L([^;\\[()<>]+);");

    private final Cp cp = new Cp();
    private final Set<String> problems = new LinkedHashSet<>();
    private final List<String> notes = new ArrayList<>();
    private final Set<String> mixinClasses = new TreeSet<>();
    private final Set<String> nonInterfaceMixins = new TreeSet<>();
    private final List<String> ignore = new ArrayList<>();
    /** call/field site of a target method (class.method desc#insn) -> handlers that @Redirect it. */
    private final Map<String, List<String>> redirectSites = new LinkedHashMap<>();
    /** target class + member -> the mixin that adds it (two mixins adding the same method clash). */
    private final Map<String, String> addedMembers = new HashMap<>();
    private boolean verbose;
    private int handlers;
    private int checkedAts;

    public static void main(String[] args) throws Exception {
        MixinTargets t = new MixinTargets();
        String only = null;
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-v" -> t.verbose = true;
                case "--only" -> only = args[++i];
                case "--ignore" -> t.ignore.add(args[++i]);
                default -> paths.add(args[i]);
            }
        }
        if (paths.isEmpty()) {
            System.err.println("usage: MixinTargets [-v] [--only text] [--ignore text]... mixinSource [classpath...]");
            System.exit(2);
        }
        for (String p : paths) {
            t.cp.add(p);
        }
        t.run(paths.get(0), only);
    }

    // ---------------------------------------------------------------------------------------------------- class path

    /** Class path: directories and jars in order, then the JDK. */
    private static final class Cp {
        private final List<Object> roots = new ArrayList<>();
        private final Map<String, ClassNode> cache = new HashMap<>();
        private final Set<String> missing = new HashSet<>();

        void add(String p) throws IOException {
            Path path = Path.of(p);
            if (Files.isDirectory(path)) {
                roots.add(path);
            } else if (Files.isRegularFile(path)) {
                roots.add(new ZipFile(path.toFile()));
            } else {
                throw new IOException("not found: " + p);
            }
        }

        ClassNode get(String internalName) {
            ClassNode cached = cache.get(internalName);
            if (cached != null || missing.contains(internalName)) {
                return cached;
            }
            byte[] bytes = null;
            try {
                for (Object r : roots) {
                    if (r instanceof Path dir) {
                        Path f = dir.resolve(internalName + ".class");
                        if (Files.isRegularFile(f)) {
                            bytes = Files.readAllBytes(f);
                            break;
                        }
                    } else {
                        ZipFile z = (ZipFile) r;
                        ZipEntry e = z.getEntry(internalName + ".class");
                        if (e != null) {
                            try (InputStream in = z.getInputStream(e)) {
                                bytes = in.readAllBytes();
                            }
                            break;
                        }
                    }
                }
                if (bytes == null) {
                    try (InputStream in = ClassLoader.getSystemResourceAsStream(internalName + ".class")) {
                        if (in != null) {
                            bytes = in.readAllBytes();
                        }
                    }
                }
            } catch (IOException e) {
                bytes = null;
            }
            if (bytes == null) {
                missing.add(internalName);
                return null;
            }
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, ClassReader.SKIP_FRAMES);
            cache.put(internalName, cn);
            return cn;
        }
    }

    // ---------------------------------------------------------------------------------------------------- driver

    private void run(String source, String only) throws Exception {
        List<ClassNode> all = new ArrayList<>();
        Path sp = Path.of(source);
        if (Files.isDirectory(sp)) {
            try (Stream<Path> s = Files.walk(sp)) {
                for (Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".class"))::iterator) {
                    all.add(read(Files.readAllBytes(f)));
                }
            }
        } else {
            try (ZipFile z = new ZipFile(sp.toFile())) {
                for (var en = z.entries(); en.hasMoreElements();) {
                    ZipEntry e = en.nextElement();
                    if (e.getName().endsWith(".class") && e.getName().startsWith("dev/")) {
                        try (InputStream in = z.getInputStream(e)) {
                            all.add(read(in.readAllBytes()));
                        }
                    }
                }
            }
        }
        List<ClassNode> mixins = new ArrayList<>();
        for (ClassNode cn : all) {
            if (cn.name.startsWith(MIXIN_PACKAGE) && annotation(cn.visibleAnnotations, cn.invisibleAnnotations, MX + "Mixin;") != null) {
                mixinClasses.add(cn.name);
                if ((cn.access & Opcodes.ACC_INTERFACE) == 0) {
                    nonInterfaceMixins.add(cn.name);
                }
                if (only == null || cn.name.contains(only)) {
                    mixins.add(cn);
                }
            }
        }
        mixins.sort((a, b) -> a.name.compareTo(b.name));
        for (ClassNode mx : mixins) {
            checkMixin(mx);
        }
        checkReferences(all);
        for (Map.Entry<String, List<String>> e : redirectSites.entrySet()) {
            if (e.getValue().size() > 1) {
                problem(String.join(" and ", e.getValue()), "crash", "more than one @Redirect on the same instruction (" + e.getKey()
                        + "): Mixin 'Redirect conflict', the later one fails or is skipped");
            }
        }
        for (String n : notes) {
            if (verbose) {
                System.out.println("note    " + n);
            }
        }
        for (String p : problems) {
            System.out.println(p);
        }
        System.out.println("MixinTargets: " + mixins.size() + " mixin classes, " + handlers + " annotated members, " + checkedAts
                + " @At target(s) matched against bytecode, " + problems.size() + " problems, " + notes.size() + " notes"
                + (verbose ? "" : " (-v shows the notes)"));
        System.exit(problems.isEmpty() ? 0 : 1);
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, ClassReader.SKIP_FRAMES);
        return cn;
    }

    private void problem(String where, String kind, String msg) {
        String line = "PROBLEM [" + kind + "] " + where + ": " + msg;
        for (String i : ignore) {
            if (line.contains(i)) {
                notes.add("(ignored) " + line);
                return;
            }
        }
        problems.add(line);
    }

    private void note(String where, String msg) {
        notes.add(where + ": " + msg);
    }

    private void ok(String where, String msg) {
        if (verbose) {
            System.out.println("ok      " + where + ": " + msg);
        }
    }

    // ---------------------------------------------------------------------------------------------------- annotations

    private static AnnotationNode annotation(List<AnnotationNode> a, List<AnnotationNode> b, String desc) {
        for (List<AnnotationNode> list : List.of(a == null ? List.<AnnotationNode>of() : a, b == null ? List.<AnnotationNode>of() : b)) {
            for (AnnotationNode an : list) {
                if (an.desc.equals(desc)) {
                    return an;
                }
            }
        }
        return null;
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> a, List<AnnotationNode> b) {
        List<AnnotationNode> r = new ArrayList<>();
        if (a != null) {
            r.addAll(a);
        }
        if (b != null) {
            r.addAll(b);
        }
        return r;
    }

    private static Object val(AnnotationNode an, String key) {
        if (an == null || an.values == null) {
            return null;
        }
        for (int i = 0; i + 1 < an.values.size(); i += 2) {
            if (an.values.get(i).equals(key)) {
                return an.values.get(i + 1);
            }
        }
        return null;
    }

    private static List<Object> list(Object v) {
        if (v == null) {
            return List.of();
        }
        if (v instanceof List<?> l) {
            return new ArrayList<>(l);
        }
        return List.of(v);
    }

    private static List<String> strings(AnnotationNode an, String key) {
        List<String> r = new ArrayList<>();
        for (Object o : list(val(an, key))) {
            r.add(String.valueOf(o));
        }
        return r;
    }

    private static List<AnnotationNode> annotationList(AnnotationNode an, String key) {
        List<AnnotationNode> r = new ArrayList<>();
        for (Object o : list(val(an, key))) {
            if (o instanceof AnnotationNode x) {
                r.add(x);
            }
        }
        return r;
    }

    private static int intVal(AnnotationNode an, String key, int def) {
        Object v = val(an, key);
        return v instanceof Integer i ? i : def;
    }

    private static boolean boolVal(AnnotationNode an, String key, boolean def) {
        Object v = val(an, key);
        return v instanceof Boolean b ? b : def;
    }

    private static String enumVal(AnnotationNode an, String key, String def) {
        Object v = val(an, key);
        return v instanceof String[] e ? e[1] : def;
    }

    // ---------------------------------------------------------------------------------------------------- selectors

    /** A Mixin member selector: [Lowner;]name[(desc)ret | :desc]. */
    private static final class Sel {
        String owner;
        String name;
        String desc;
        boolean field;
        boolean unsupported;

        String show() {
            return (owner == null ? "" : "L" + owner + ";") + name + (desc == null ? "" : (field ? ":" : "") + desc);
        }
    }

    private static Sel parseSel(String s) {
        Sel r = new Sel();
        s = s.trim();
        int semi = s.indexOf(';');
        int firstSpecial = firstOf(s, "(:");
        if (s.startsWith("L") && semi > 0 && (firstSpecial < 0 || semi < firstSpecial)) {
            r.owner = s.substring(1, semi).replace('.', '/');
            s = s.substring(semi + 1);
        } else {
            int fs = firstOf(s, "(:");
            String head = fs < 0 ? s : s.substring(0, fs);
            int dot = head.lastIndexOf('.');
            if (dot > 0) {
                r.owner = head.substring(0, dot).replace('.', '/');
                s = s.substring(dot + 1);
            }
        }
        int fs = firstOf(s, "(:");
        if (fs < 0) {
            r.name = s;
        } else {
            r.name = s.substring(0, fs);
            if (s.charAt(fs) == ':') {
                r.field = true;
                r.desc = s.substring(fs + 1);
            } else {
                r.desc = s.substring(fs);
            }
        }
        if (r.name.contains("*") || r.name.startsWith("/") || (r.desc != null && r.desc.matches(".*[{+*].*"))) {
            r.unsupported = true;
        }
        return r;
    }

    private static int firstOf(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------------------------------------------- mixin class

    private void checkMixin(ClassNode mx) {
        String where0 = shortName(mx.name);
        AnnotationNode mixin = annotation(mx.visibleAnnotations, mx.invisibleAnnotations, MX + "Mixin;");
        List<String> targets = new ArrayList<>();
        for (Object o : list(val(mixin, "value"))) {
            if (o instanceof Type t) {
                targets.add(t.getInternalName());
            }
        }
        for (String s : strings(mixin, "targets")) {
            targets.add(s.replace('.', '/'));
        }
        if (targets.isEmpty()) {
            problem(where0, "crash", "@Mixin without a target");
            return;
        }
        boolean iface = (mx.access & Opcodes.ACC_INTERFACE) != 0;
        boolean accessorOnly = iface;
        if (iface) {
            for (MethodNode m : mx.methods) {
                if ((m.access & Opcodes.ACC_SYNTHETIC) == 0 && !isAccessor(m)) {
                    accessorOnly = false;
                }
            }
        }
        String kind = !iface ? "standard" : accessorOnly ? "accessor" : "interface";
        for (String target : targets) {
            ClassNode tc = cp.get(target);
            if (tc == null) {
                problem(where0, "crash", "@Mixin target " + target + " not found on the class path");
                continue;
            }
            boolean targetIface = (tc.access & Opcodes.ACC_INTERFACE) != 0;
            if (!kind.equals("accessor") && targetIface != kind.equals("interface")) {
                problem(where0, "crash", kind + " mixin target type mismatch: " + target + " is " + (targetIface ? "" : "not ") + "an interface");
            }
            if (kind.equals("standard") && !mx.superName.equals("java/lang/Object") && !hasSuper(tc, mx.superName)) {
                problem(where0, "crash", "super class " + mx.superName + " is not a super class of target " + target);
            }
            if (kind.equals("interface") && !mx.superName.equals("java/lang/Object")) {
                problem(where0, "crash", "interface mixin with super class " + mx.superName);
            }
            ok(where0, kind + " mixin -> " + target);
            checkMembers(mx, tc, kind);
        }
    }

    private boolean isAccessor(MethodNode m) {
        if (m == null) {
            return false;
        }
        return annotation(m.visibleAnnotations, m.invisibleAnnotations, MX + "gen/Accessor;") != null
                || annotation(m.visibleAnnotations, m.invisibleAnnotations, MX + "gen/Invoker;") != null;
    }

    private boolean hasSuper(ClassNode tc, String superName) {
        for (ClassNode c = cp.get(tc.superName == null ? "java/lang/Object" : tc.superName); c != null;
                c = c.superName == null ? null : cp.get(c.superName)) {
            if (c.name.equals(superName)) {
                return true;
            }
        }
        return false;
    }

    private String currentTarget = "";

    private void checkMembers(ClassNode mx, ClassNode tc, String kind) {
        currentTarget = tc.name;
        for (FieldNode f : mx.fields) {
            AnnotationNode shadow = annotation(f.visibleAnnotations, f.invisibleAnnotations, MX + "Shadow;");
            if (kind.equals("standard") && shadow == null && (f.access & Opcodes.ACC_STATIC) != 0
                    && (f.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC)) == 0) {
                problem(shortName(mx.name) + "#" + f.name, "crash", "non-private static field in a mixin class (Mixin: \"contains non-private static field\"), make it private");
            }
            if (shadow != null) {
                handlers++;
                checkShadowField(mx, f, shadow, tc);
            }
        }
        for (MethodNode m : mx.methods) {
            String where = shortName(mx.name) + "#" + m.name;
            boolean special = false;
            for (AnnotationNode an : annotations(m.visibleAnnotations, m.invisibleAnnotations)) {
                String d = an.desc;
                if (d.equals(MX + "Shadow;")) {
                    special = true;
                    handlers++;
                    checkShadowMethod(where, m, an, tc);
                } else if (d.equals(MX + "gen/Accessor;") || d.equals(MX + "gen/Invoker;")) {
                    special = true;
                    handlers++;
                    checkAccessor(where, mx, m, an, tc, d.endsWith("Invoker;"));
                } else if (d.equals(MX + "Overwrite;")) {
                    special = true;
                    handlers++;
                    checkOverwrite(where, m, an, tc);
                } else if (d.equals(INJ + "Inject;")) {
                    special = true;
                    handlers++;
                    checkInject(where, m, an, tc);
                } else if (d.equals(INJ + "Redirect;")) {
                    special = true;
                    handlers++;
                    checkInjector(where, m, an, tc, "Redirect");
                } else if (d.equals(INJ + "ModifyVariable;")) {
                    special = true;
                    handlers++;
                    checkInjector(where, m, an, tc, "ModifyVariable");
                } else if (d.equals(INJ + "ModifyArg;")) {
                    special = true;
                    handlers++;
                    checkInjector(where, m, an, tc, "ModifyArg");
                } else if (d.equals(INJ + "ModifyArgs;")) {
                    special = true;
                    handlers++;
                    checkInjector(where, m, an, tc, "ModifyArgs");
                } else if (d.equals(INJ + "ModifyConstant;")) {
                    special = true;
                    handlers++;
                    checkInjector(where, m, an, tc, "ModifyConstant");
                } else if (d.startsWith(MIXIN_EXTRAS) && (val(an, "method") != null || val(an, "at") != null)) {
                    special = true;
                    handlers++;
                    checkInjector(where, m, an, tc, d.substring(d.lastIndexOf('/') + 1, d.length() - 1));
                } else if (d.equals(MX + "Unique;") || d.equals(MX + "Intrinsic;") || d.equals(MX + "Implements;")) {
                    special = true;
                }
            }
            if (kind.equals("standard") && isStatic(m) && (m.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC)) == 0 && !m.name.startsWith("<")
                    && annotation(m.visibleAnnotations, m.invisibleAnnotations, MX + "Overwrite;") == null) {
                problem(where, "crash", "non-private static method in a mixin class (Mixin: \"contains non-private static method\"), make it private");
            }
            if (!special && (m.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_ABSTRACT)) == 0 && !m.name.startsWith("<")
                    && (m.access & Opcodes.ACC_STATIC) == 0) {
                claim(where, mx, tc, m);
                MethodNode clash = declared(tc, m.name, m.desc);
                if (clash != null) {
                    problem(where, "crash", "non-private mixin method without @Unique/@Overwrite/@Intrinsic has the same name and descriptor as "
                            + tc.name + "." + m.name + m.desc + " (Mixin: method conflict)");
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------- lookups

    private static MethodNode declared(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) {
            if (m.name.equals(name) && (desc == null || m.desc.equals(desc))) {
                return m;
            }
        }
        return null;
    }

    private static FieldNode declaredField(ClassNode c, String name, String desc) {
        for (FieldNode f : c.fields) {
            if (f.name.equals(name) && (desc == null || f.desc.equals(desc))) {
                return f;
            }
        }
        return null;
    }

    /** Where a member with this name sits in the class hierarchy of c (to explain a miss), or null. */
    private String inheritedMethod(ClassNode c, String name, String desc) {
        for (ClassNode s = c.superName == null ? null : cp.get(c.superName); s != null; s = s.superName == null ? null : cp.get(s.superName)) {
            MethodNode m = declared(s, name, desc);
            if (m != null) {
                return s.name + "." + m.name + m.desc;
            }
        }
        for (String i : c.interfaces) {
            ClassNode ic = cp.get(i);
            if (ic != null) {
                MethodNode m = declared(ic, name, desc);
                if (m != null) {
                    return ic.name + "." + m.name + m.desc;
                }
            }
        }
        return null;
    }

    private String inheritedField(ClassNode c, String name, String desc) {
        for (ClassNode s = c.superName == null ? null : cp.get(c.superName); s != null; s = s.superName == null ? null : cp.get(s.superName)) {
            FieldNode f = declaredField(s, name, desc);
            if (f != null) {
                return s.name + "." + f.name + ":" + f.desc;
            }
        }
        return null;
    }

    private static String sameNameMethods(ClassNode c, String name) {
        StringBuilder sb = new StringBuilder();
        for (MethodNode m : c.methods) {
            if (m.name.equals(name)) {
                sb.append(' ').append(m.name).append(m.desc);
            }
        }
        return sb.length() == 0 ? "no method of that name" : "declared:" + sb;
    }

    // ---------------------------------------------------------------------------------------------------- @Shadow

    private void checkShadowField(ClassNode mx, FieldNode f, AnnotationNode shadow, ClassNode tc) {
        String where = shortName(mx.name) + "#" + f.name + " (@Shadow field)";
        String prefix = val(shadow, "prefix") instanceof String ps ? ps : "shadow$";
        if (f.name.startsWith(prefix)) {
            problem(where, "crash", "@Shadow field has the shadow prefix '" + prefix + "', Mixin does not allow that for fields");
            return;
        }
        List<String> names = shadowNames(f.name, shadow);
        FieldNode hit = null;
        for (String n : names) {
            hit = declaredField(tc, n, f.desc);
            if (hit != null) {
                break;
            }
        }
        if (hit == null) {
            StringBuilder why = new StringBuilder();
            for (String n : names) {
                FieldNode byName = declaredField(tc, n, null);
                if (byName != null) {
                    why.append(" target declares ").append(n).append(':').append(byName.desc).append(';');
                }
                String inh = inheritedField(tc, n, null);
                if (inh != null) {
                    why.append(" only inherited: ").append(inh).append(" (Mixin looks in the target class itself);");
                }
            }
            problem(where, "crash", "@Shadow field " + names + ":" + f.desc + " not declared in " + tc.name + "." + why);
            return;
        }
        boolean mStatic = (f.access & Opcodes.ACC_STATIC) != 0;
        boolean tStatic = (hit.access & Opcodes.ACC_STATIC) != 0;
        if (mStatic != tStatic) {
            problem(where, "crash", "STATIC modifier does not match " + tc.name + "." + hit.name);
        }
        boolean finalTarget = (hit.access & Opcodes.ACC_FINAL) != 0;
        boolean finalShadow = annotation(f.visibleAnnotations, f.invisibleAnnotations, MX + "Final;") != null;
        if (finalTarget != finalShadow) {
            note(where, "target is " + (finalTarget ? "" : "not ") + "final, shadow is " + (finalShadow ? "" : "not ") + "@Final (warning only)");
        }
        ok(where, hit.name + ":" + hit.desc);
    }

    private List<String> shadowNames(String name, AnnotationNode shadow) {
        String prefix = val(shadow, "prefix") instanceof String s ? s : "shadow$";
        List<String> names = new ArrayList<>();
        names.add(name.startsWith(prefix) ? name.substring(prefix.length()) : name);
        names.addAll(strings(shadow, "aliases"));
        return names;
    }

    private void checkShadowMethod(String where, MethodNode m, AnnotationNode shadow, ClassNode tc) {
        List<String> names = shadowNames(m.name, shadow);
        MethodNode hit = null;
        for (String n : names) {
            hit = declared(tc, n, m.desc);
            if (hit != null) {
                break;
            }
        }
        if (hit == null) {
            StringBuilder why = new StringBuilder();
            for (String n : names) {
                why.append(' ').append(sameNameMethods(tc, n)).append(';');
                String inh = inheritedMethod(tc, n, m.desc);
                if (inh != null) {
                    why.append(" only inherited: ").append(inh).append(" (Mixin looks in the target class itself);");
                }
            }
            problem(where, "crash", "@Shadow method " + names + m.desc + " not declared in " + tc.name + "." + why);
            return;
        }
        if (((m.access ^ hit.access) & Opcodes.ACC_STATIC) != 0) {
            problem(where, "crash", "STATIC modifier does not match " + tc.name + "." + hit.name + hit.desc);
        }
        ok(where, "@Shadow " + hit.name + hit.desc);
    }

    // ---------------------------------------------------------------------------------------------------- accessors

    private void claim(String where, ClassNode mx, ClassNode tc, MethodNode m) {
        String key = tc.name + "." + m.name + m.desc;
        String prev = addedMembers.putIfAbsent(key, mx.name);
        if (prev != null && !prev.equals(mx.name)) {
            problem(where, "crash", "method " + m.name + m.desc + " is also added to " + tc.name + " by " + shortName(prev) + " (Mixin: method conflict)");
        }
    }

    private void checkAccessor(String where, ClassNode mx, MethodNode m, AnnotationNode an, ClassNode tc, boolean invoker) {
        claim(where, mx, tc, m);
        Type[] params = Type.getArgumentTypes(m.desc);
        Type ret = Type.getReturnType(m.desc);
        boolean mStatic = (m.access & Opcodes.ACC_STATIC) != 0;
        String value = val(an, "value") instanceof String s && !s.isEmpty() ? s : null;
        if (invoker) {
            String name = value != null ? value : inflect(m.name, new String[]{"call", "invoke"});
            if (name == null) {
                problem(where, "crash", "@Invoker: cannot inflect a target name from '" + m.name + "' (prefix call/invoke) and no value given");
                return;
            }
            if (name.equals("<init>")) {
                ok(where, "@Invoker factory <init> (not checked in detail)");
                return;
            }
            MethodNode hit = declared(tc, name, m.desc);
            if (hit == null) {
                String inh = inheritedMethod(tc, name, m.desc);
                problem(where, "crash", "@Invoker " + name + m.desc + " not declared in " + tc.name + ": " + sameNameMethods(tc, name)
                        + (inh != null ? "; only inherited: " + inh + " (Mixin looks in the target class itself)" : ""));
                return;
            }
            boolean tStatic = (hit.access & Opcodes.ACC_STATIC) != 0;
            if (mStatic && !tStatic) {
                problem(where, "crash", "@Invoker method is static but target " + hit.name + " is not");
            } else if (!mStatic && tStatic) {
                note(where, "target " + hit.name + " is static, the invoker should be static too (Mixin only warns)");
            }
            ok(where, "@Invoker " + hit.name + hit.desc);
            return;
        }
        boolean setter = params.length == 1 && ret.getSort() == Type.VOID;
        boolean getter = params.length == 0 && ret.getSort() != Type.VOID;
        if (!setter && !getter) {
            problem(where, "crash", "@Accessor must be a getter (no args, returns the field type) or a setter (one arg, void): " + m.desc);
            return;
        }
        Type fieldType = setter ? params[0] : ret;
        String name = value != null ? value : inflect(m.name, new String[]{"get", "is", "set"});
        if (name == null) {
            problem(where, "crash", "@Accessor: cannot inflect a target name from '" + m.name + "' (prefix get/is/set) and no value given");
            return;
        }
        FieldNode hit = declaredField(tc, name, fieldType.getDescriptor());
        if (hit == null) {
            FieldNode byName = declaredField(tc, name, null);
            String inh = inheritedField(tc, name, null);
            problem(where, "crash", "@Accessor field " + name + ":" + fieldType.getDescriptor() + " not declared in " + tc.name
                    + (byName != null ? "; the field there has type " + byName.desc : "")
                    + (inh != null ? "; only inherited: " + inh + " (Mixin looks in the target class itself)" : ""));
            return;
        }
        boolean tStatic = (hit.access & Opcodes.ACC_STATIC) != 0;
        if (mStatic && !tStatic) {
            problem(where, "crash", "accessor method is static but field " + hit.name + " is not");
        } else if (!mStatic && tStatic) {
            note(where, "field " + hit.name + " is static, the accessor should be static too (Mixin only warns)");
        }
        if (setter && (hit.access & Opcodes.ACC_FINAL) != 0 && annotation(m.visibleAnnotations, m.invisibleAnnotations, MX + "Mutable;") == null) {
            note(where, "setter for final field " + hit.name + " without @Mutable (Mixin only warns, the JVM may still reject writes in some cases)");
        }
        ok(where, (setter ? "setter " : "getter ") + hit.name + ":" + hit.desc);
    }

    private static String inflect(String method, String[] prefixes) {
        for (String p : prefixes) {
            if (method.startsWith(p) && method.length() > p.length() && Character.isUpperCase(method.charAt(p.length()))) {
                String rest = method.substring(p.length());
                return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
            }
        }
        return null;
    }

    private void checkOverwrite(String where, MethodNode m, AnnotationNode an, ClassNode tc) {
        List<String> names = new ArrayList<>();
        names.add(m.name);
        names.addAll(strings(an, "aliases"));
        for (String n : names) {
            MethodNode hit = declared(tc, n, m.desc);
            if (hit != null) {
                if (((hit.access ^ m.access) & Opcodes.ACC_STATIC) != 0) {
                    problem(where, "crash", "@Overwrite static modifier differs from target " + hit.name + hit.desc);
                }
                ok(where, "@Overwrite " + hit.name + hit.desc);
                return;
            }
        }
        problem(where, "crash", "@Overwrite " + names + m.desc + " not declared in " + tc.name + ": " + sameNameMethods(tc, m.name));
    }

    // ---------------------------------------------------------------------------------------------------- injectors

    /** The target methods named by {@code method = ...}; null when a selector is not understood. */
    private List<MethodNode> targetMethods(String where, AnnotationNode an, ClassNode tc) {
        List<String> sels = strings(an, "method");
        if (sels.isEmpty()) {
            problem(where, "crash", "injector without a method");
            return null;
        }
        List<MethodNode> found = new ArrayList<>();
        boolean skipped = false;
        for (String s : sels) {
            Sel sel = parseSel(s);
            if (sel.unsupported) {
                note(where, "method selector '" + s + "' uses wildcards/regex, not checked");
                skipped = true;
                continue;
            }
            if (sel.owner != null && !sel.owner.equals(tc.name)) {
                problem(where, "inert", "method selector '" + s + "' names owner " + sel.owner + " but the target is " + tc.name);
                continue;
            }
            List<MethodNode> hits = new ArrayList<>();
            for (MethodNode tm : tc.methods) {
                if (tm.name.equals(sel.name) && (sel.desc == null || tm.desc.equals(sel.desc))) {
                    hits.add(tm);
                }
            }
            if (hits.isEmpty()) {
                String inh = inheritedMethod(tc, sel.name, sel.desc);
                problem(where, "inert", "method '" + s + "' not declared in " + tc.name + " (" + sameNameMethods(tc, sel.name) + ")"
                        + (inh != null ? "; only inherited: " + inh + " (Mixin does not look in super classes)" : ""));
            }
            for (MethodNode h : hits) {
                if (!found.contains(h)) {
                    found.add(h);
                }
            }
        }
        if (found.isEmpty() && !skipped) {
            return null;
        }
        return found;
    }

    private void checkInject(String where, MethodNode h, AnnotationNode an, ClassNode tc) {
        List<MethodNode> targets = targetMethods(where, an, tc);
        if (targets == null) {
            return;
        }
        boolean cancellable = boolVal(an, "cancellable", false);
        String capture = enumVal(an, "locals", "NO_CAPTURE");
        boolean canCapture = !capture.equals("NO_CAPTURE");
        List<AnnotationNode> ats = atList(an);
        Type[] hp = Type.getArgumentTypes(h.desc);
        for (MethodNode tm : targets) {
            String w = where + " -> " + tm.name + tm.desc;
            if ((tm.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                problem(w, "inert", "target method is abstract/native, it has no code to inject into");
                continue;
            }
            if (!isStatic(h) && isStatic(tm)) {
                problem(w, "crash", "non-static handler targets a static method");
            }
            for (AnnotationNode at : ats) {
                matchAt(w, tm, at, an);
            }
            if (tm.name.equals("<init>") && cancellable) {
                problem(w, "crash", "cancellable @Inject targeting a constructor");
            }
            Type tret = Type.getReturnType(tm.desc);
            Type ci = tret.getSort() == Type.VOID ? CI : CIR;
            List<Type> full = new ArrayList<>(List.of(Type.getArgumentTypes(tm.desc)));
            full.add(ci);
            boolean okDesc = Type.getReturnType(h.desc).getSort() == Type.VOID
                    && (sameTypes(hp, full.toArray(new Type[0])) || (hp.length == 1 && hp[0].equals(ci) && !canCapture)
                            || (canCapture && startsWith(hp, full)));
            if (!okDesc) {
                String hint = "";
                if (tret.getSort() != Type.VOID && hp.length > 0 && hp[hp.length - 1].equals(CI)) {
                    hint = " (target returns a value: use CallbackInfoReturnable)";
                } else if (tret.getSort() == Type.VOID && hp.length > 0 && hp[hp.length - 1].equals(CIR)) {
                    hint = " (target returns void: use CallbackInfo)";
                }
                problem(w, "crash", "handler descriptor " + h.desc + " does not match: expected (" + joinDesc(full) + ")V" + (canCapture ? " plus captured locals" : "")
                        + (full.size() > 1 ? " or (" + ci.getDescriptor() + ")V" : "") + hint);
            }
            if (okDesc) {
                ok(w, "@Inject " + h.desc + " fits" + (canCapture ? " (locals captured, not verified)" : ""));
            }
            if (!cancellable && usesCancel(h)) {
                problem(w, "runtime", "handler calls CallbackInfo.cancel()/setReturnValue() but the @Inject is not cancellable = true");
            }
        }
    }

    private static boolean usesCancel(MethodNode h) {
        for (AbstractInsnNode in : h.instructions) {
            if (in instanceof MethodInsnNode mi && (mi.owner.equals(CI.getInternalName()) || mi.owner.equals(CIR.getInternalName()))
                    && (mi.name.equals("cancel") || mi.name.equals("setReturnValue"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(Type[] have, List<Type> prefix) {
        if (have.length < prefix.size()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (!have[i].equals(prefix.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameTypes(Type[] a, Type[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!a[i].equals(b[i])) {
                return false;
            }
        }
        return true;
    }

    private static String joinDesc(List<Type> l) {
        StringBuilder sb = new StringBuilder();
        for (Type t : l) {
            sb.append(t.getDescriptor());
        }
        return sb.toString();
    }

    private static boolean isStatic(MethodNode m) {
        return (m.access & Opcodes.ACC_STATIC) != 0;
    }

    private static List<AnnotationNode> atList(AnnotationNode an) {
        return annotationList(an, "at");
    }

    /** Redirect, ModifyVariable, ModifyArg, ModifyArgs, ModifyConstant and the MixinExtras injectors. */
    private void checkInjector(String where, MethodNode h, AnnotationNode an, ClassNode tc, String type) {
        List<MethodNode> targets = targetMethods(where, an, tc);
        if (targets == null) {
            return;
        }
        for (MethodNode tm : targets) {
            String w = where + " -> " + tm.name + tm.desc;
            if ((tm.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                problem(w, "inert", "target method is abstract/native");
                continue;
            }
            boolean strictStatic = type.equals("ModifyArg") || type.equals("ModifyArgs");
            if (strictStatic ? isStatic(h) != isStatic(tm) : (!isStatic(h) && isStatic(tm))) {
                problem(w, "crash", (strictStatic ? "'static' modifier of handler does not match target" : "non-static handler targets a static method"));
            }
            switch (type) {
                case "Redirect" -> checkRedirect(w, h, an, tm);
                case "ModifyVariable" -> checkModifyVariable(w, h, an, tm);
                case "ModifyArg" -> checkModifyArg(w, h, an, tm);
                case "ModifyConstant" -> checkModifyConstant(w, h, an, tm);
                case "ModifyArgs" -> {
                    for (AnnotationNode at : atList(an)) {
                        matchAt(w, tm, at, an);
                    }
                    if (!h.desc.equals("(Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;)V")
                            && !h.desc.startsWith("(Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;")) {
                        problem(w, "crash", "@ModifyArgs handler must take Args first: " + h.desc);
                    }
                }
                default -> {
                    // MixinExtras and friends: the target must exist, the descriptor rules are theirs
                    for (AnnotationNode at : atList(an)) {
                        matchAt(w, tm, at, an);
                    }
                    if (type.equals("WrapOperation")) {
                        checkWrapOperation(w, h, an, tm);
                    } else {
                        note(w, "@" + type + ": method/@At checked, handler descriptor not checked");
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------- @At

    /**
     * Finds the instructions an @At selects. Reports a problem when a target is given and does not occur; returns null when the
     * kind of @At cannot be checked statically (then nothing is reported but a note).
     */
    private List<AbstractInsnNode> matchAt(String where, MethodNode tm, AnnotationNode at, AnnotationNode injector) {
        String value = String.valueOf(val(at, "value"));
        String target = val(at, "target") instanceof String s && !s.isEmpty() ? s : null;
        int ordinal = intVal(at, "ordinal", -1);
        int opcode = intVal(at, "opcode", -1);
        boolean slice = val(injector, "slice") != null;
        List<AbstractInsnNode> out = new ArrayList<>();
        switch (value) {
            case "HEAD", "RETURN", "TAIL" -> {
                return out;
            }
            case "INVOKE", "INVOKE_ASSIGN" -> {
                if (target == null) {
                    problem(where, "crash", "@At(" + value + ") without target");
                    return null;
                }
                Sel sel = parseSel(target);
                if (sel.unsupported) {
                    note(where, "@At target '" + target + "' uses wildcards, not checked");
                    return null;
                }
                for (AbstractInsnNode in : tm.instructions) {
                    if (in instanceof MethodInsnNode mi && (sel.owner == null || sel.owner.equals(mi.owner)) && mi.name.equals(sel.name)
                            && (sel.desc == null || sel.desc.equals(mi.desc))) {
                        out.add(in);
                    }
                }
                if (out.isEmpty()) {
                    problem(where, "inert", "@At(" + value + ") target " + sel.show() + " is not called in " + tm.name + "; calls of '" + sel.name + "' there: "
                            + describeCalls(tm, sel.name));
                    return out;
                }
            }
            case "FIELD" -> {
                if (target == null) {
                    problem(where, "crash", "@At(FIELD) without target");
                    return null;
                }
                Sel sel = parseSel(target);
                if (sel.unsupported) {
                    note(where, "@At target '" + target + "' uses wildcards, not checked");
                    return null;
                }
                for (AbstractInsnNode in : tm.instructions) {
                    if (in instanceof FieldInsnNode fi && (sel.owner == null || sel.owner.equals(fi.owner)) && fi.name.equals(sel.name)
                            && (sel.desc == null || sel.desc.equals(fi.desc)) && (opcode < 0 || opcode == fi.getOpcode())) {
                        out.add(in);
                    }
                }
                if (out.isEmpty()) {
                    problem(where, "inert", "@At(FIELD) target " + sel.show() + (opcode >= 0 ? " opcode " + opcode : "") + " is not accessed in " + tm.name
                            + "; field accesses of '" + sel.name + "' there: " + describeFields(tm, sel.name));
                    return out;
                }
            }
            case "NEW" -> {
                if (target == null) {
                    note(where, "@At(NEW) without target, not checked");
                    return null;
                }
                if (target.contains("(") || target.contains("<init>")) {
                    Sel sel = parseSel(target);
                    for (AbstractInsnNode in : tm.instructions) {
                        if (in instanceof MethodInsnNode mi && mi.name.equals("<init>") && (sel.owner == null || sel.owner.equals(mi.owner))
                                && (sel.desc == null || sel.desc.equals(mi.desc))) {
                            out.add(in);
                        }
                    }
                } else {
                    String cls = target.startsWith("L") && target.endsWith(";") ? target.substring(1, target.length() - 1) : target;
                    cls = cls.replace('.', '/');
                    for (AbstractInsnNode in : tm.instructions) {
                        if (in instanceof TypeInsnNode ti && ti.getOpcode() == Opcodes.NEW && ti.desc.equals(cls)) {
                            out.add(in);
                        }
                    }
                }
                if (out.isEmpty()) {
                    problem(where, "inert", "@At(NEW) target " + target + " is not instantiated in " + tm.name);
                    return out;
                }
            }
            default -> {
                note(where, "@At(" + value + ") not checked statically");
                return null;
            }
        }
        checkedAts++;
        if (ordinal >= 0 && !slice) {
            if (out.size() <= ordinal) {
                problem(where, "inert", "@At(" + value + ") ordinal " + ordinal + " but only " + out.size() + " match(es) in " + tm.name);
                return new ArrayList<>();
            }
            return new ArrayList<>(List.of(out.get(ordinal)));
        }
        return out;
    }

    private static String describeCalls(MethodNode tm, String name) {
        Set<String> s = new LinkedHashSet<>();
        for (AbstractInsnNode in : tm.instructions) {
            if (in instanceof MethodInsnNode mi && mi.name.equals(name)) {
                s.add("L" + mi.owner + ";" + mi.name + mi.desc);
            }
        }
        return s.isEmpty() ? "none" : String.join(", ", s);
    }

    private static String describeFields(MethodNode tm, String name) {
        Set<String> s = new LinkedHashSet<>();
        for (AbstractInsnNode in : tm.instructions) {
            if (in instanceof FieldInsnNode fi && fi.name.equals(name)) {
                s.add("L" + fi.owner + ";" + fi.name + ":" + fi.desc + " (op " + fi.getOpcode() + ")");
            }
        }
        return s.isEmpty() ? "none" : String.join(", ", s);
    }

    // ---------------------------------------------------------------------------------------------------- @Redirect

    private void checkRedirect(String w, MethodNode h, AnnotationNode an, MethodNode tm) {
        List<AnnotationNode> ats = atList(an);
        if (ats.size() != 1) {
            problem(w, "crash", "@Redirect needs exactly one @At, found " + ats.size());
            return;
        }
        AnnotationNode at = ats.get(0);
        List<AbstractInsnNode> hits = matchAt(w, tm, at, an);
        if (hits == null) {
            return;
        }
        Type[] hp = Type.getArgumentTypes(h.desc);
        Type hr = Type.getReturnType(h.desc);
        Set<String> seen = new HashSet<>();
        for (AbstractInsnNode in : hits) {
            redirectSites.computeIfAbsent(tm.name + tm.desc + "#" + tm.instructions.indexOf(in) + "@" + currentTarget, k -> new ArrayList<>()).add(w);
            Type recv = null;
            Type[] params;
            Type ret;
            String what;
            if (in instanceof MethodInsnNode mi) {
                if (mi.name.equals("<init>")) {
                    problem(w, "crash", "@Redirect of a constructor call " + mi.owner + ".<init>: use @At(NEW)");
                    continue;
                }
                params = Type.getArgumentTypes(mi.desc);
                ret = Type.getReturnType(mi.desc);
                recv = mi.getOpcode() == Opcodes.INVOKESTATIC ? null : Type.getObjectType(mi.owner);
                what = "call " + mi.owner + "." + mi.name + mi.desc;
            } else if (in instanceof FieldInsnNode fi) {
                Type ft = Type.getType(fi.desc);
                boolean isStatic = fi.getOpcode() == Opcodes.GETSTATIC || fi.getOpcode() == Opcodes.PUTSTATIC;
                boolean get = fi.getOpcode() == Opcodes.GETSTATIC || fi.getOpcode() == Opcodes.GETFIELD;
                recv = isStatic ? null : Type.getObjectType(fi.owner);
                params = get ? new Type[0] : new Type[]{ft};
                ret = get ? ft : Type.VOID_TYPE;
                what = (get ? "read of " : "write of ") + fi.owner + "." + fi.name + ":" + fi.desc;
            } else if (in instanceof TypeInsnNode ti && ti.getOpcode() == Opcodes.NEW) {
                // constructor redirect: handler takes the constructor arguments and returns the new object
                MethodInsnNode ctor = findCtor(tm, ti);
                if (ctor == null) {
                    problem(w, "crash", "NEW " + ti.desc + " without a matching <init> call");
                    continue;
                }
                params = Type.getArgumentTypes(ctor.desc);
                ret = Type.getObjectType(ti.desc);
                what = "new " + ti.desc + ctor.desc;
            } else {
                note(w, "@Redirect on instruction kind not checked");
                continue;
            }
            if (!seen.add(what)) {
                continue;
            }
            List<Type> expect = new ArrayList<>();
            if (recv != null) {
                expect.add(recv);
            }
            expect.addAll(List.of(params));
            boolean okParams = sameTypes(hp, expect.toArray(new Type[0])) || coerceMatch(h, hp, expect)
                    || extraTargetArgs(hp, expect, Type.getArgumentTypes(tm.desc));
            boolean okRet = hr.equals(ret) || (hasCoerceReturn(h) && hr.getSort() == Type.OBJECT && ret.getSort() == Type.OBJECT);
            if (!okParams || !okRet) {
                problem(w, "crash", "@Redirect handler " + h.desc + " does not fit the " + what + ": expected (" + joinDesc(expect) + ")" + ret.getDescriptor()
                        + (recv != null ? " (receiver first)" : ""));
            } else {
                ok(w, "@Redirect fits " + what);
            }
        }
    }

    private static MethodInsnNode findCtor(MethodNode tm, TypeInsnNode ti) {
        boolean seenNew = false;
        for (AbstractInsnNode in : tm.instructions) {
            if (in == ti) {
                seenNew = true;
            } else if (seenNew && in instanceof MethodInsnNode mi && mi.name.equals("<init>") && mi.owner.equals(ti.desc)) {
                return mi;
            }
        }
        return null;
    }

    /** Handler parameters that differ from the expected ones are allowed for @Coerce-d parameters (Mixin: canCoerce). */
    private boolean coerceMatch(MethodNode h, Type[] hp, List<Type> expect) {
        if (hp.length != expect.size()) {
            return false;
        }
        for (int i = 0; i < hp.length; i++) {
            if (!hp[i].equals(expect.get(i))) {
                if (!hasCoerceParam(h, i) || hp[i].getSort() != Type.OBJECT || expect.get(i).getSort() != Type.OBJECT) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasCoerceParam(MethodNode h, int i) {
        return coerceIn(h.visibleParameterAnnotations, i) || coerceIn(h.invisibleParameterAnnotations, i);
    }

    private static boolean coerceIn(List<AnnotationNode>[] arr, int i) {
        if (arr != null && i < arr.length && arr[i] != null) {
            for (AnnotationNode a : arr[i]) {
                if (a.desc.equals(INJ + "Coerce;")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCoerceReturn(MethodNode h) {
        return annotation(h.visibleAnnotations, h.invisibleAnnotations, INJ + "Coerce;") != null;
    }

    private static boolean fits(Type[] hp, List<Type> expect, Type[] targetArgs) {
        return sameTypes(hp, expect.toArray(new Type[0])) || extraTargetArgs(hp, expect, targetArgs);
    }

    /** Handler = expected parameters followed by (a prefix of) the target method's own arguments. */
    private static boolean extraTargetArgs(Type[] hp, List<Type> expect, Type[] targetArgs) {
        if (hp.length <= expect.size() || hp.length > expect.size() + targetArgs.length) {
            return false;
        }
        for (int i = 0; i < hp.length; i++) {
            Type want = i < expect.size() ? expect.get(i) : targetArgs[i - expect.size()];
            if (!hp[i].equals(want)) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------------- @ModifyVariable

    private void checkModifyVariable(String w, MethodNode h, AnnotationNode an, MethodNode tm) {
        List<AnnotationNode> ats = atList(an);
        if (ats.size() != 1) {
            problem(w, "crash", "@ModifyVariable needs exactly one @At, found " + ats.size());
            return;
        }
        matchAt(w, tm, ats.get(0), an);
        Type[] hp = Type.getArgumentTypes(h.desc);
        Type hr = Type.getReturnType(h.desc);
        if (hp.length == 0 || !hp[0].equals(hr)) {
            problem(w, "crash", "@ModifyVariable handler must take the variable as first parameter and return the same type: " + h.desc);
            return;
        }
        Type[] targs = Type.getArgumentTypes(tm.desc);
        boolean argsOnly = boolVal(an, "argsOnly", false);
        int ordinal = intVal(an, "ordinal", -1);
        int index = intVal(an, "index", -1);
        List<String> names = strings(an, "name");
        // after the variable Mixin accepts the first n arguments of the target method (Injector.validateParams), nothing else
        Type[] rest = java.util.Arrays.copyOfRange(hp, 1, hp.length);
        if (rest.length > targs.length || !startsWith(rest, List.of(java.util.Arrays.copyOf(targs, rest.length)))) {
            problem(w, "crash", "@ModifyVariable handler " + h.desc + ": parameters after the variable must be the first arguments of the target method ("
                    + joinDesc(List.of(targs)) + ")");
        }
        if (!argsOnly) {
            note(w, "@ModifyVariable without argsOnly selects a local variable: type/ordinal cannot be verified without the local variable table");
            return;
        }
        List<Integer> cands = new ArrayList<>();
        int slot = isStatic(tm) ? 0 : 1;
        Map<Integer, Integer> slotToArg = new LinkedHashMap<>();
        for (int i = 0; i < targs.length; i++) {
            slotToArg.put(slot, i);
            if (targs[i].equals(hr)) {
                cands.add(i);
            }
            slot += targs[i].getSize();
        }
        if (index >= 0) {
            Integer a = slotToArg.get(index);
            if (a == null || !targs[a].equals(hr)) {
                problem(w, "inert", "@ModifyVariable index " + index + " is not an argument of type " + hr.getDescriptor() + " (argument slots " + slotToArg + ")");
            }
        } else if (ordinal >= 0) {
            if (cands.size() <= ordinal) {
                problem(w, "inert", "@ModifyVariable ordinal " + ordinal + " but the target method has " + cands.size() + " argument(s) of type " + hr.getDescriptor());
            }
        } else if (!names.isEmpty()) {
            note(w, "@ModifyVariable by name " + names + " not checked (needs debug names)");
        } else if (cands.size() != 1) {
            problem(w, "inert", "@ModifyVariable(argsOnly) implicit selection needs exactly one argument of type " + hr.getDescriptor() + ", target has " + cands.size()
                    + " (" + tm.desc + "): add ordinal/index");
        } else {
            ok(w, "@ModifyVariable selects argument " + cands.get(0) + " (" + hr.getDescriptor() + ")");
        }
    }

    // ---------------------------------------------------------------------------------------------------- @ModifyArg

    private void checkModifyArg(String w, MethodNode h, AnnotationNode an, MethodNode tm) {
        List<AnnotationNode> ats = atList(an);
        if (ats.size() != 1) {
            problem(w, "crash", "@ModifyArg needs exactly one @At, found " + ats.size());
            return;
        }
        List<AbstractInsnNode> hits = matchAt(w, tm, ats.get(0), an);
        if (hits == null) {
            return;
        }
        int index = intVal(an, "index", -1);
        Type[] hp = Type.getArgumentTypes(h.desc);
        Type hr = Type.getReturnType(h.desc);
        for (AbstractInsnNode in : hits) {
            if (!(in instanceof MethodInsnNode mi)) {
                problem(w, "crash", "@ModifyArg must target a method invocation");
                continue;
            }
            Type[] cargs = Type.getArgumentTypes(mi.desc);
            int idx = index;
            if (idx < 0) {
                int found = -1;
                int count = 0;
                for (int i = 0; i < cargs.length; i++) {
                    if (cargs[i].equals(hr)) {
                        found = i;
                        count++;
                    }
                }
                if (count != 1) {
                    problem(w, "crash", "@ModifyArg without index: " + count + " arguments of type " + hr.getDescriptor() + " in " + mi.name + mi.desc);
                    continue;
                }
                idx = found;
            } else if (idx >= cargs.length) {
                problem(w, "crash", "@ModifyArg index " + idx + " is out of range for " + mi.name + mi.desc);
                continue;
            }
            if (!cargs[idx].equals(hr)) {
                problem(w, "crash", "@ModifyArg return type " + hr.getDescriptor() + " must match the parameter type " + cargs[idx].getDescriptor());
            }
            Type[] targs = Type.getArgumentTypes(tm.desc);
            if (!(fits(hp, List.of(cargs[idx]), targs) || fits(hp, List.of(cargs), targs))) {
                problem(w, "crash", "@ModifyArg handler " + h.desc + " must take (" + cargs[idx].getDescriptor() + ") or all arguments (" + joinDesc(List.of(cargs)) + ")");
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------- @ModifyConstant

    private void checkModifyConstant(String w, MethodNode h, AnnotationNode an, MethodNode tm) {
        Type[] hp = Type.getArgumentTypes(h.desc);
        Type hr = Type.getReturnType(h.desc);
        if (hp.length == 0 || !hp[0].equals(hr)) {
            problem(w, "crash", "@ModifyConstant handler must take the constant as first parameter and return the same type: " + h.desc);
        }
        List<AnnotationNode> consts = annotationList(an, "constant");
        if (consts.isEmpty()) {
            note(w, "@ModifyConstant without @Constant, not checked");
            return;
        }
        for (AnnotationNode c : consts) {
            Object want = null;
            String kind = null;
            for (String k : new String[]{"intValue", "floatValue", "doubleValue", "longValue", "stringValue", "classValue"}) {
                if (val(c, k) != null) {
                    kind = k;
                    want = val(c, k);
                }
            }
            if (kind == null) {
                note(w, "@Constant without a value (nullValue/expandZeroConditions), not checked");
                continue;
            }
            boolean found = false;
            for (AbstractInsnNode in : tm.instructions) {
                found |= constantOf(in, kind, want);
            }
            if (!found) {
                problem(w, "inert", "@Constant(" + kind + " = " + want + ") does not occur in " + tm.name);
            } else {
                checkedAts++;
            }
        }
    }

    private static boolean constantOf(AbstractInsnNode in, String kind, Object want) {
        int op = in.getOpcode();
        switch (kind) {
            case "intValue" -> {
                int v = (Integer) want;
                if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) {
                    return op - Opcodes.ICONST_0 == v;
                }
                if (in instanceof org.objectweb.asm.tree.IntInsnNode ii && (op == Opcodes.BIPUSH || op == Opcodes.SIPUSH)) {
                    return ii.operand == v;
                }
                return in instanceof LdcInsnNode l && l.cst instanceof Integer i && i == v;
            }
            case "floatValue" -> {
                float v = (Float) want;
                if (op >= Opcodes.FCONST_0 && op <= Opcodes.FCONST_2) {
                    return op - Opcodes.FCONST_0 == v;
                }
                return in instanceof LdcInsnNode l && l.cst instanceof Float f && f == v;
            }
            case "doubleValue" -> {
                double v = (Double) want;
                if (op == Opcodes.DCONST_0 || op == Opcodes.DCONST_1) {
                    return op - Opcodes.DCONST_0 == v;
                }
                return in instanceof LdcInsnNode l && l.cst instanceof Double d && d == v;
            }
            case "longValue" -> {
                long v = (Long) want;
                if (op == Opcodes.LCONST_0 || op == Opcodes.LCONST_1) {
                    return op - Opcodes.LCONST_0 == v;
                }
                return in instanceof LdcInsnNode l && l.cst instanceof Long x && x == v;
            }
            case "stringValue" -> {
                return in instanceof LdcInsnNode l && want.equals(l.cst);
            }
            default -> {
                return in instanceof LdcInsnNode l && l.cst instanceof Type t && want instanceof Type w && t.equals(w);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------- MixinExtras

    private void checkWrapOperation(String w, MethodNode h, AnnotationNode an, MethodNode tm) {
        Type[] hp = Type.getArgumentTypes(h.desc);
        if (hp.length == 0 || !hp[hp.length - 1].getInternalName().equals("com/llamalad7/mixinextras/injector/wrapoperation/Operation")) {
            problem(w, "crash", "@WrapOperation handler must take Operation as last parameter: " + h.desc);
            return;
        }
        for (AnnotationNode at : atList(an)) {
            List<AbstractInsnNode> hits = matchAt(w, tm, at, an);
            if (hits == null) {
                continue;
            }
            for (AbstractInsnNode in : hits) {
                if (in instanceof MethodInsnNode mi) {
                    List<Type> expect = new ArrayList<>();
                    if (mi.getOpcode() != Opcodes.INVOKESTATIC) {
                        expect.add(Type.getObjectType(mi.owner));
                    }
                    expect.addAll(List.of(Type.getArgumentTypes(mi.desc)));
                    Type[] mid = java.util.Arrays.copyOf(hp, hp.length - 1);
                    if (!sameTypes(mid, expect.toArray(new Type[0])) || !Type.getReturnType(h.desc).equals(Type.getReturnType(mi.desc))) {
                        problem(w, "crash", "@WrapOperation handler " + h.desc + " does not fit call " + mi.owner + "." + mi.name + mi.desc + ": expected ("
                                + joinDesc(expect) + "Operation)" + Type.getReturnType(mi.desc).getDescriptor());
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------- references

    /** Mixin refuses to load a (non-accessor) mixin class that other code refers to ("cannot be referenced directly"). */
    private void checkReferences(List<ClassNode> all) {
        for (ClassNode cn : all) {
            Set<String> refs = new TreeSet<>();
            collectRefs(cn, refs);
            for (String r : refs) {
                if (nonInterfaceMixins.contains(r) && !cn.name.equals(r) && !cn.name.startsWith(r + "$")) {
                    problem(shortName(cn.name), "crash", "refers to the mixin class " + r + " directly (Mixin: IllegalClassLoadError)");
                }
            }
        }
    }

    private static void collectRefs(ClassNode cn, Set<String> refs) {
        addType(cn.superName, refs);
        for (String i : cn.interfaces) {
            addType(i, refs);
        }
        for (FieldNode f : cn.fields) {
            addDesc(f.desc, refs);
        }
        for (MethodNode m : cn.methods) {
            addDesc(m.desc, refs);
            for (AbstractInsnNode in : m.instructions) {
                if (in instanceof TypeInsnNode t) {
                    addType(t.desc, refs);
                } else if (in instanceof MethodInsnNode mi) {
                    addType(mi.owner, refs);
                    addDesc(mi.desc, refs);
                } else if (in instanceof FieldInsnNode fi) {
                    addType(fi.owner, refs);
                    addDesc(fi.desc, refs);
                } else if (in instanceof LdcInsnNode l && l.cst instanceof Type t) {
                    addDesc(t.getDescriptor(), refs);
                } else if (in instanceof InvokeDynamicInsnNode d) {
                    for (Object o : d.bsmArgs) {
                        if (o instanceof org.objectweb.asm.Handle hd) {
                            addType(hd.getOwner(), refs);
                            addDesc(hd.getDesc(), refs);
                        }
                    }
                }
            }
        }
    }

    private static void addType(String internal, Set<String> refs) {
        if (internal != null) {
            refs.add(internal.startsWith("[") ? internal.replaceAll("^\\[+L?|;$", "") : internal);
        }
    }

    private static void addDesc(String desc, Set<String> refs) {
        Matcher m = OBJ_TYPE.matcher(desc);
        while (m.find()) {
            refs.add(m.group(1));
        }
    }

    private static String shortName(String internal) {
        return internal.startsWith(MIXIN_PACKAGE) ? internal.substring(MIXIN_PACKAGE.length()) : internal;
    }
}
