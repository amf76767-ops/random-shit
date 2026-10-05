import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class VerifyClasses {
    public static void main(String[] args) throws Exception {
        File jar = new File(args[0]);
        List<URL> urls = new ArrayList<>();
        urls.add(jar.toURI().toURL());
        for (int i = 1; i < args.length; i++) {
            for (String part : args[i].split(File.pathSeparator)) {
                if (!part.isBlank()) {
                    urls.add(new File(part).toURI().toURL());
                }
            }
        }
        int checked = 0, failed = 0, unchecked = 0;
        try (JarFile jf = new JarFile(jar); URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader())) {
            for (Enumeration<JarEntry> en = jf.entries(); en.hasMoreElements(); ) {
                String name = en.nextElement().getName();
                if (!name.endsWith(".class") || !name.startsWith("dev/dihclient/")) {
                    continue;
                }
                String cls = name.substring(0, name.length() - 6).replace('/', '.');
                try {
                    Class<?> c = Class.forName(cls, false, loader);
                    c.getDeclaredMethods();
                    checked++;
                } catch (VerifyError | ClassFormatError e) {
                    failed++;
                    System.out.println("VERIFY FAILED " + cls + ": " + e.getMessage().split("\n")[0]);
                } catch (LinkageError e) {
                    unchecked++;
                    if (System.getProperty("verbose") != null) {
                        System.out.println("not checked: " + cls + " (" + e + ")");
                    }
                }
            }
        }
        System.out.println("VerifyClasses: " + checked + " classes verified, " + failed + " failed, " + unchecked + " could not be checked (missing libraries)");
        System.exit(failed == 0 ? 0 : 1);
    }
}
