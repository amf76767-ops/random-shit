import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.commons.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class Remap2 {
    static Map<String,ClassNode> classes = new HashMap<>();
    static Map<String,String> m = new HashMap<>();
    static String resolve(String owner, String name, String desc, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return null;
        String k = owner + "." + name + desc;
        if (m.containsKey(k)) return m.get(k);
        ClassNode c = classes.get(owner);
        if (c == null) return null;
        String r = resolve(c.superName, name, desc, seen);
        if (r != null) return r;
        for (String i : c.interfaces) { r = resolve(i, name, desc, seen); if (r != null) return r; }
        return null;
    }
    public static void main(String[] a) throws Exception {
        List<String> lines = Files.readAllLines(Path.of(a[0]));
        for (String l : lines) { String[] t = l.split("\t"); if (t[0].equals("CLASS")) m.put(t[1], t[2]); }
        for (String l : lines) {
            String[] t = l.split("\t");
            if (t[0].equals("FIELD")) m.put(t[1] + "." + t[3], t[4]);
            else if (t[0].equals("METHOD")) m.put(t[1] + "." + t[3] + t[2], t[4]);
        }
        try (ZipFile in = new ZipFile(a[1])) {
            for (Enumeration<? extends ZipEntry> e = in.entries(); e.hasMoreElements();) {
                ZipEntry z = e.nextElement();
                if (!z.getName().endsWith(".class")) continue;
                ClassNode cn = new ClassNode();
                new ClassReader(in.getInputStream(z)).accept(cn, ClassReader.SKIP_CODE);
                classes.put(cn.name, cn);
            }
            Map<String,String> full = new HashMap<>(m);
            for (ClassNode c : classes.values()) {
                for (MethodNode mn : c.methods) {
                    if (mn.name.startsWith("<")) continue;
                    String r = resolve(c.name, mn.name, mn.desc, new HashSet<>());
                    if (r != null) full.put(c.name + "." + mn.name + mn.desc, r);
                }
            }
            Remapper r = new SimpleRemapper(full);
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(a[2]))) {
                for (Enumeration<? extends ZipEntry> e = in.entries(); e.hasMoreElements();) {
                    ZipEntry z = e.nextElement();
                    if (!z.getName().endsWith(".class")) continue;
                    ClassReader cr = new ClassReader(in.getInputStream(z));
                    ClassWriter cw = new ClassWriter(0);
                    cr.accept(new ClassRemapper(cw, r), 0);
                    String cn = full.getOrDefault(z.getName().replace(".class", ""), z.getName().replace(".class", ""));
                    out.putNextEntry(new ZipEntry(cn + ".class"));
                    out.write(cw.toByteArray());
                    out.closeEntry();
                }
            }
        }
    }
}
