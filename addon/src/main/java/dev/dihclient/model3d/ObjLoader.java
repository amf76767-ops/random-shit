package dev.dihclient.model3d;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Reads Wavefront .obj files with their .mtl: positions, texture coordinates, normals, vertex colours (v x y z r g b),
 * polygons (split into triangles), material colour (Kd, d) and picture (map_Kd). No animation.
 *
 * Blender's OBJ export looks along -Z by default while glTF and Minecraft look along +Z, so the model is turned by 180°.
 */
public final class ObjLoader {
    private ObjLoader() {
    }

    private static final class Group {
        final List<int[]> corners = new ArrayList<>(); // per face corner: v, vt, vn (0 = none, else index + 1)
        final List<Integer> faceSizes = new ArrayList<>();
    }

    public static Model load(Path file) throws IOException {
        List<float[]> vs = new ArrayList<>(), vts = new ArrayList<>(), vns = new ArrayList<>();
        List<Integer> vcolor = new ArrayList<>();
        Map<String, Group> groups = new LinkedHashMap<>();
        Map<String, ModelBuilder.Mat> mtl = new HashMap<>();
        Path dir = file.toAbsolutePath().getParent();
        String current = "";
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] t = line.split("\\s+");
                switch (t[0]) {
                    case "v" -> {
                        if (t.length < 4) {
                            break;
                        }
                        vs.add(new float[]{num(t[1]), num(t[2]), num(t[3])});
                        vcolor.add(t.length >= 7 ? GltfLoader.pack(num(t[4]), num(t[5]), num(t[6]), 1f) : 0xFFFFFFFF);
                    }
                    case "vt" -> vts.add(new float[]{t.length > 1 ? num(t[1]) : 0, t.length > 2 ? num(t[2]) : 0});
                    case "vn" -> {
                        if (t.length >= 4) {
                            vns.add(new float[]{num(t[1]), num(t[2]), num(t[3])});
                        }
                    }
                    case "usemtl" -> current = line.length() > 7 ? line.substring(7).trim() : "";
                    case "mtllib" -> {
                        if (line.length() > 7) {
                            readMtl(dir, line.substring(7).trim(), mtl);
                        }
                    }
                    case "f" -> {
                        if (t.length < 4) {
                            break;
                        }
                        Group g = groups.computeIfAbsent(current, k -> new Group());
                        for (int i = 1; i < t.length; i++) {
                            String[] p = t[i].split("/", -1);
                            g.corners.add(new int[]{
                                    ref(p.length > 0 ? p[0] : "", vs.size()),
                                    ref(p.length > 1 ? p[1] : "", vts.size()),
                                    ref(p.length > 2 ? p[2] : "", vns.size())});
                        }
                        g.faceSizes.add(t.length - 1);
                    }
                    default -> {
                    }
                }
            }
        }

        // a picture next to the model with the same name is used when the .mtl names none
        BufferedImage fallback = null;
        boolean anyPicture = mtl.values().stream().anyMatch(m -> m.image != null);
        if (!anyPicture) {
            String base = stem(file);
            for (String ext : new String[]{".png", ".jpg", ".jpeg"}) {
                Path p = dir.resolve(base + ext);
                if (Files.isRegularFile(p)) {
                    fallback = image(p);
                    if (fallback != null) {
                        break;
                    }
                }
            }
        }

        ModelBuilder b = new ModelBuilder();
        for (Map.Entry<String, Group> e : groups.entrySet()) {
            ModelBuilder.Mat m = mtl.get(e.getKey());
            if (m == null) {
                m = new ModelBuilder.Mat();
            }
            if (m.image == null && fallback != null) {
                m.image = fallback;
            }
            int mi = b.addMaterial(m);
            Group g = e.getValue();
            List<float[]> pos = new ArrayList<>(), nrm = new ArrayList<>(), uv = new ArrayList<>();
            List<Integer> col = new ArrayList<>(), idx = new ArrayList<>();
            Map<Long, Integer> dedupe = new HashMap<>();
            boolean hasNormals = true, hasUv = true;
            int at = 0;
            for (int size : g.faceSizes) {
                int[] face = new int[size];
                for (int i = 0; i < size; i++) {
                    int[] c = g.corners.get(at + i);
                    long key = ((long) c[0] * 1_000_003L + c[1]) * 1_000_003L + c[2];
                    Integer id = dedupe.get(key);
                    if (id == null) {
                        if (c[0] <= 0) {
                            id = -1;
                        } else {
                            float[] p = vs.get(c[0] - 1);
                            pos.add(new float[]{-p[0], p[1], -p[2]}); // turn 180° around Y
                            if (c[2] > 0) {
                                float[] n = vns.get(c[2] - 1);
                                nrm.add(new float[]{-n[0], n[1], -n[2]});
                            } else {
                                nrm.add(null);
                                hasNormals = false;
                            }
                            if (c[1] > 0) {
                                float[] t = vts.get(c[1] - 1);
                                uv.add(new float[]{t[0], 1f - t[1]});
                            } else {
                                uv.add(new float[]{0, 0});
                                hasUv = false;
                            }
                            col.add(vcolor.get(c[0] - 1));
                            id = pos.size() - 1;
                        }
                        dedupe.put(key, id);
                    }
                    face[i] = id;
                }
                at += size;
                for (int i = 1; i + 1 < size; i++) {
                    if (face[0] >= 0 && face[i] >= 0 && face[i + 1] >= 0) {
                        idx.add(face[0]);
                        idx.add(face[i]);
                        idx.add(face[i + 1]);
                    }
                }
            }
            ModelBuilder.Prim p = new ModelBuilder.Prim();
            int n = pos.size();
            p.pos = new float[n * 3];
            p.color = new int[n];
            for (int i = 0; i < n; i++) {
                p.pos[i * 3] = pos.get(i)[0];
                p.pos[i * 3 + 1] = pos.get(i)[1];
                p.pos[i * 3 + 2] = pos.get(i)[2];
                p.color[i] = col.get(i);
            }
            if (hasNormals) {
                p.nrm = new float[n * 3];
                for (int i = 0; i < n; i++) {
                    System.arraycopy(nrm.get(i), 0, p.nrm, i * 3, 3);
                }
            }
            if (hasUv) {
                p.uv = new float[n * 2];
                for (int i = 0; i < n; i++) {
                    p.uv[i * 2] = uv.get(i)[0];
                    p.uv[i * 2 + 1] = uv.get(i)[1];
                }
            }
            p.idx = idx.stream().mapToInt(Integer::intValue).toArray();
            p.material = mi;
            p.node = 0;
            b.addPrimitive(p);
        }
        return b.finish(stem(file));
    }

    private static void readMtl(Path dir, String name, Map<String, ModelBuilder.Mat> out) {
        try {
            Path f = dir.resolve(name.replace('\\', '/')).normalize();
            if (!Files.isRegularFile(f)) {
                return;
            }
            ModelBuilder.Mat cur = null;
            float[] kd = {1, 1, 1};
            float alpha = 1;
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] t = line.split("\\s+");
                String key = t[0].toLowerCase(Locale.ROOT);
                if (key.equals("newmtl")) {
                    if (cur != null) {
                        cur.color = GltfLoader.pack(kd[0], kd[1], kd[2], alpha);
                    }
                    cur = new ModelBuilder.Mat();
                    kd = new float[]{1, 1, 1};
                    alpha = 1;
                    out.put(line.substring(6).trim(), cur);
                } else if (cur != null) {
                    if (key.equals("kd") && t.length >= 4) {
                        kd = new float[]{num(t[1]), num(t[2]), num(t[3])};
                    } else if (key.equals("d") && t.length >= 2) {
                        alpha = num(t[1]);
                    } else if (key.equals("tr") && t.length >= 2) {
                        alpha = 1f - num(t[1]);
                    } else if (key.equals("map_kd") && t.length >= 2) {
                        Path img = dir.resolve(t[t.length - 1].replace('\\', '/')).normalize();
                        if (Files.isRegularFile(img)) {
                            cur.image = image(img);
                        }
                    }
                }
            }
            if (cur != null) {
                cur.color = GltfLoader.pack(kd[0], kd[1], kd[2], alpha);
            }
        } catch (IOException | RuntimeException ignored) {
            // a missing or broken .mtl only costs the colours
        }
    }

    private static BufferedImage image(Path p) {
        try {
            return ImageIO.read(p.toFile());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** OBJ indices count from 1, negative ones from the end. Returns 0 for "none", else index + 1. */
    private static int ref(String s, int size) {
        if (s.isEmpty()) {
            return 0;
        }
        try {
            int i = Integer.parseInt(s);
            int idx = i > 0 ? i : size + i + 1;
            return idx >= 1 && idx <= size ? idx : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static float num(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String stem(Path file) {
        String n = file.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }
}
