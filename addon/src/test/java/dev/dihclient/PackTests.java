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
        // a look rewrites the shader constants
        dev.dihclient.glue.Look look = new dev.dihclient.glue.Look("t", dev.dihclient.glue.Look.rgb(0.5f, 0.6f, 0.7f), dev.dihclient.glue.Look.rgb(1, 1, 1),
                dev.dihclient.glue.Look.rgb(1, 1, 1), dev.dihclient.glue.Look.rgb(0.62f, 0.05f, 0.72f), dev.dihclient.glue.Look.rgb(0, 0, 0),
                dev.dihclient.glue.Look.rgb(2, 2, 2), dev.dihclient.glue.Look.rgb(0.1f, 0.2f, 0.3f));
        byte[] looked;
        try (FileInputStream in = new FileInputStream(path)) {
            looked = PackFilter.copy(in, n -> false, look::patch);
        }
        java.util.Map<String, String> text = new java.util.HashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(looked))) {
            for (ZipEntry e = z.getNextEntry(); e != null; e = z.getNextEntry()) {
                if (e.getName().endsWith(".fsh")) {
                    text.put(e.getName().substring(e.getName().lastIndexOf('/') + 1), new String(z.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        check(text.get("lightmap.fsh").contains("LIGHT_TINT = vec3(0.500, 0.600, 0.700);"), "light tint written");
        check(text.get("sky.fsh").contains("SKY_ADD_H = vec3(0.620, 0.050, 0.720);"), "sky colour written");
        check(text.get("rendertype_clouds.fsh").contains("CLOUD_MUL = vec3(2.000, 2.000, 2.000);"), "cloud colour written");
        check(text.get("glint.fsh").contains("GLINT_SPEED = 60.0"), "other shaders untouched");
        java.nio.file.Path dump = java.nio.file.Path.of("build/looked");
        java.nio.file.Files.createDirectories(dump.resolve("assets/minecraft/shaders/core"));
        for (var en : text.entrySet()) {
            java.nio.file.Files.writeString(dump.resolve("assets/minecraft/shaders/core").resolve(en.getKey()), en.getValue());
        }
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
