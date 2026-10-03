package dev.dihclient;

import dev.dihclient.glue.PackFilter;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class PackTests {
    static int failed;
    static int passed;

    static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + what);
        }
    }

    static Set<String> names(byte[] zip) throws Exception {
        Set<String> out = new HashSet<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = z.getNextEntry(); e != null; e = z.getNextEntry()) {
                out.add(e.getName());
            }
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        String path = "../dist/DIH-Visuals-1.21.11.zip";
        byte[] all;
        try (FileInputStream in = new FileInputStream(path)) {
            all = PackFilter.copy(in, n -> false);
        }
        Set<String> full = names(all);
        String mc = "assets/minecraft/";
        check(full.contains(mc + "shaders/core/lightmap.fsh"), "fullbright shader is in the pack");
        check(full.contains(mc + "textures/environment/rain.png"), "invisible rain texture is in the pack");
        check(full.contains("pack.mcmeta"), "pack.mcmeta");

        byte[] noLight;
        try (FileInputStream in = new FileInputStream(path)) {
            noLight = PackFilter.copy(in, n -> n.startsWith(mc + "shaders/core/lightmap.fsh"));
        }
        Set<String> n1 = names(noLight);
        check(!n1.contains(mc + "shaders/core/lightmap.fsh") && n1.size() == full.size() - 1, "one switch off removes exactly that file");

        byte[] noWater;
        try (FileInputStream in = new FileInputStream(path)) {
            noWater = PackFilter.copy(in, n -> n.startsWith(mc + "textures/block/water_"));
        }
        check(names(noWater).size() == full.size() - 4, "water off removes the 4 water files");

        byte[] none;
        try (FileInputStream in = new FileInputStream(path)) {
            none = PackFilter.copy(in, n -> !n.equals("pack.mcmeta"));
        }
        check(names(none).equals(Set.of("pack.mcmeta")), "everything off leaves a valid empty pack");
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
