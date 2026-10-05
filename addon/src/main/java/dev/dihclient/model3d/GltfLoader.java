package dev.dihclient.model3d;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

public final class GltfLoader {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;

    private record Acc(float[] data, int comps, int count) {
    }

    private final JsonObject root;
    private final Path dir;
    private final List<byte[]> buffers = new ArrayList<>();
    private final Map<Integer, BufferedImage> images = new HashMap<>();
    private final ModelBuilder builder = new ModelBuilder();

    private GltfLoader(JsonObject root, byte[] glbBin, Path dir) throws IOException {
        this.root = root;
        this.dir = dir;
        JsonArray bufs = array(root, "buffers");
        for (int i = 0; i < bufs.size(); i++) {
            JsonObject b = bufs.get(i).getAsJsonObject();
            if (!b.has("uri")) {
                if (glbBin == null) {
                    throw new IOException("Buffer " + i + " has no data");
                }
                buffers.add(glbBin);
            } else {
                buffers.add(readUri(b.get("uri").getAsString()));
            }
        }
    }

    public static Model load(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        String json;
        byte[] bin = null;
        if (data.length >= 12 && ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(0) == GLB_MAGIC) {
            ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int pos = 12;
            String j = null;
            while (pos + 8 <= data.length) {
                int len = b.getInt(pos);
                int type = b.getInt(pos + 4);
                pos += 8;
                if (len < 0 || pos + len > data.length) {
                    throw new IOException("Damaged .glb file");
                }
                if (type == CHUNK_JSON && j == null) {
                    j = new String(data, pos, len, StandardCharsets.UTF_8);
                } else if (type == CHUNK_BIN && bin == null) {
                    bin = java.util.Arrays.copyOfRange(data, pos, pos + len);
                }
                pos += (len + 3) & ~3;
            }
            if (j == null) {
                throw new IOException(".glb file has no JSON part");
            }
            json = j;
        } else {
            json = new String(data, StandardCharsets.UTF_8);
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Not a glTF file", e);
        }
        Path parent = file.toAbsolutePath().getParent();
        try {
            return new GltfLoader(root, bin, parent).build(stem(file));
        } catch (RuntimeException e) {
            throw new IOException("Damaged glTF file: " + e, e);
        }
    }

    private static String stem(Path file) {
        String n = file.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private Model build(String name) throws IOException {
        for (JsonElement e : array(root, "extensionsRequired")) {
            String x = e.getAsString();
            if (x.equals("KHR_draco_mesh_compression") || x.equals("EXT_meshopt_compression")) {
                throw new IOException("Compressed model (" + x + "). Export again without mesh compression.");
            }
        }

        JsonArray mats = array(root, "materials");
        for (JsonElement e : mats) {
            ModelBuilder.Mat m = new ModelBuilder.Mat();
            JsonObject pbr = e.getAsJsonObject().has("pbrMetallicRoughness") ? e.getAsJsonObject().getAsJsonObject("pbrMetallicRoughness") : null;
            if (pbr != null) {
                if (pbr.has("baseColorFactor")) {
                    JsonArray f = pbr.getAsJsonArray("baseColorFactor");
                    m.color = pack(f.get(0).getAsFloat(), f.get(1).getAsFloat(), f.get(2).getAsFloat(), f.size() > 3 ? f.get(3).getAsFloat() : 1f);
                }
                if (pbr.has("baseColorTexture")) {
                    m.image = textureImage(pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt());
                }
            }
            builder.addMaterial(m);
        }

        JsonArray nodes = array(root, "nodes");
        int n = Math.max(1, nodes.size());
        int[] parent = new int[n];
        java.util.Arrays.fill(parent, -1);
        float[] t = new float[n * 3], r = new float[n * 4], s = new float[n * 3];
        for (int i = 0; i < n; i++) {
            r[i * 4 + 3] = 1;
            s[i * 3] = s[i * 3 + 1] = s[i * 3 + 2] = 1;
        }
        for (int i = 0; i < nodes.size(); i++) {
            JsonObject o = nodes.get(i).getAsJsonObject();
            for (JsonElement c : array(o, "children")) {
                int ci = c.getAsInt();
                if (ci >= 0 && ci < n && ci != i) {
                    parent[ci] = i;
                }
            }
            if (o.has("matrix")) {
                float[] m = floats(o.getAsJsonArray("matrix"), 16);
                float[] tt = new float[3], qq = new float[4], ss = new float[3];
                Mat4.decompose(m, tt, qq, ss);
                System.arraycopy(tt, 0, t, i * 3, 3);
                System.arraycopy(qq, 0, r, i * 4, 4);
                System.arraycopy(ss, 0, s, i * 3, 3);
            } else {
                if (o.has("translation")) {
                    System.arraycopy(floats(o.getAsJsonArray("translation"), 3), 0, t, i * 3, 3);
                }
                if (o.has("rotation")) {
                    System.arraycopy(floats(o.getAsJsonArray("rotation"), 4), 0, r, i * 4, 4);
                }
                if (o.has("scale")) {
                    System.arraycopy(floats(o.getAsJsonArray("scale"), 3), 0, s, i * 3, 3);
                }
            }
        }
        builder.skeleton(n, parent, t, r, s);

        JsonArray skins = array(root, "skins");
        int[] skinBase = new int[skins.size()];
        List<Integer> jointNodes = new ArrayList<>();
        List<float[]> inverse = new ArrayList<>();
        for (int i = 0; i < skins.size(); i++) {
            JsonObject sk = skins.get(i).getAsJsonObject();
            skinBase[i] = jointNodes.size();
            JsonArray js = array(sk, "joints");
            Acc ibm = sk.has("inverseBindMatrices") ? read(sk.get("inverseBindMatrices").getAsInt()) : null;
            for (int j = 0; j < js.size(); j++) {
                int node = js.get(j).getAsInt();
                jointNodes.add(node >= 0 && node < n ? node : 0);
                float[] m = Mat4.identity();
                if (ibm != null && ibm.comps() == 16 && (j + 1) * 16 <= ibm.data().length) {
                    System.arraycopy(ibm.data(), j * 16, m, 0, 16);
                }
                inverse.add(m);
            }
        }
        Model model0 = builder.model();
        model0.jointNode = jointNodes.stream().mapToInt(Integer::intValue).toArray();
        model0.invBind = new float[inverse.size() * 16];
        for (int i = 0; i < inverse.size(); i++) {
            System.arraycopy(inverse.get(i), 0, model0.invBind, i * 16, 16);
        }

        Set<Integer> reachable = reachableNodes(nodes, parent);
        JsonArray meshes = array(root, "meshes");
        for (int ni = 0; ni < nodes.size(); ni++) {
            JsonObject o = nodes.get(ni).getAsJsonObject();
            if (!o.has("mesh") || !reachable.contains(ni)) {
                continue;
            }
            int mi = o.get("mesh").getAsInt();
            if (mi < 0 || mi >= meshes.size()) {
                continue;
            }
            int skin = o.has("skin") ? o.get("skin").getAsInt() : -1;
            for (JsonElement pe : array(meshes.get(mi).getAsJsonObject(), "primitives")) {
                primitive(pe.getAsJsonObject(), ni, skin >= 0 && skin < skinBase.length ? skinBase[skin] : -1);
            }
        }

        animations(model0, n);
        return builder.finish(name);
    }

    private Set<Integer> reachableNodes(JsonArray nodes, int[] parent) {
        List<Integer> start = new ArrayList<>();
        JsonArray scenes = array(root, "scenes");
        int sc = root.has("scene") ? root.get("scene").getAsInt() : 0;
        if (sc >= 0 && sc < scenes.size()) {
            for (JsonElement e : array(scenes.get(sc).getAsJsonObject(), "nodes")) {
                start.add(e.getAsInt());
            }
        } else {
            for (int i = 0; i < nodes.size(); i++) {
                if (parent[i] < 0) {
                    start.add(i);
                }
            }
        }
        Set<Integer> out = new HashSet<>();
        List<Integer> stack = new ArrayList<>(start);
        while (!stack.isEmpty()) {
            int i = stack.remove(stack.size() - 1);
            if (i < 0 || i >= nodes.size() || !out.add(i)) {
                continue;
            }
            for (JsonElement c : array(nodes.get(i).getAsJsonObject(), "children")) {
                stack.add(c.getAsInt());
            }
        }
        return out;
    }

    private void primitive(JsonObject prim, int node, int skinBase) throws IOException {
        int mode = prim.has("mode") ? prim.get("mode").getAsInt() : 4;
        if (mode < 4 || mode > 6) {
            return;
        }
        if (prim.has("extensions") && prim.getAsJsonObject("extensions").has("KHR_draco_mesh_compression")) {
            throw new IOException("Compressed model (Draco). Export again without mesh compression.");
        }
        JsonObject at = prim.getAsJsonObject("attributes");
        if (at == null || !at.has("POSITION")) {
            return;
        }
        Acc p = read(at.get("POSITION").getAsInt());
        if (p.comps() != 3) {
            return;
        }
        int n = p.count();
        ModelBuilder.Prim out = new ModelBuilder.Prim();
        out.pos = p.data();
        out.node = node;
        out.material = prim.has("material") ? prim.get("material").getAsInt() : -1;
        if (at.has("NORMAL")) {
            Acc a = read(at.get("NORMAL").getAsInt());
            if (a.comps() == 3 && a.count() == n) {
                out.nrm = a.data();
            }
        }
        if (at.has("TEXCOORD_0")) {
            Acc a = read(at.get("TEXCOORD_0").getAsInt());
            if (a.comps() == 2 && a.count() == n) {
                out.uv = a.data();
            }
        }
        if (at.has("COLOR_0")) {
            Acc a = read(at.get("COLOR_0").getAsInt());
            if ((a.comps() == 3 || a.comps() == 4) && a.count() == n) {
                out.color = new int[n];
                for (int i = 0; i < n; i++) {
                    float al = a.comps() == 4 ? a.data()[i * 4 + 3] : 1f;
                    out.color[i] = pack(a.data()[i * a.comps()], a.data()[i * a.comps() + 1], a.data()[i * a.comps() + 2], al);
                }
            }
        }
        if (skinBase >= 0 && at.has("JOINTS_0") && at.has("WEIGHTS_0")) {
            Acc j = read(at.get("JOINTS_0").getAsInt());
            Acc w = read(at.get("WEIGHTS_0").getAsInt());
            if (j.comps() == 4 && w.comps() == 4 && j.count() == n && w.count() == n) {
                out.joints = new int[n * 4];
                out.weights = w.data();
                for (int i = 0; i < n * 4; i++) {
                    out.joints[i] = skinBase + (int) j.data()[i];
                }
            }
        }
        int[] raw;
        if (prim.has("indices")) {
            float[] f = read(prim.get("indices").getAsInt()).data();
            raw = new int[f.length];
            for (int i = 0; i < f.length; i++) {
                raw[i] = (int) f[i];
            }
        } else {
            raw = new int[n];
            for (int i = 0; i < n; i++) {
                raw[i] = i;
            }
        }
        out.idx = triangles(raw, mode);
        builder.addPrimitive(out);
    }

    private static int[] triangles(int[] raw, int mode) {
        if (mode == 4) {
            return raw;
        }
        int tris = Math.max(0, raw.length - 2);
        int[] out = new int[tris * 3];
        for (int i = 0; i < tris; i++) {
            if (mode == 5) {
                out[i * 3] = raw[i + (i & 1)];
                out[i * 3 + 1] = raw[i + 1 - (i & 1)];
                out[i * 3 + 2] = raw[i + 2];
            } else {
                out[i * 3] = raw[0];
                out[i * 3 + 1] = raw[i + 1];
                out[i * 3 + 2] = raw[i + 2];
            }
        }
        return out;
    }

    private void animations(Model model, int nodeCount) throws IOException {
        for (JsonElement e : array(root, "animations")) {
            JsonObject ao = e.getAsJsonObject();
            Model.Animation anim = new Model.Animation();
            anim.name = ao.has("name") ? ao.get("name").getAsString() : "Animation " + (model.animations.size() + 1);
            JsonArray samplers = array(ao, "samplers");
            for (JsonElement ce : array(ao, "channels")) {
                JsonObject c = ce.getAsJsonObject();
                JsonObject target = c.getAsJsonObject("target");
                if (target == null || !target.has("node") || !target.has("path")) {
                    continue;
                }
                String path = target.get("path").getAsString();
                int pi = switch (path) {
                    case "translation" -> 0;
                    case "rotation" -> 1;
                    case "scale" -> 2;
                    default -> -1;
                };
                int node = target.get("node").getAsInt();
                int si = c.get("sampler").getAsInt();
                if (pi < 0 || node < 0 || node >= nodeCount || si < 0 || si >= samplers.size()) {
                    continue;
                }
                JsonObject smp = samplers.get(si).getAsJsonObject();
                Acc in = read(smp.get("input").getAsInt());
                Acc outv = read(smp.get("output").getAsInt());
                String interp = smp.has("interpolation") ? smp.get("interpolation").getAsString() : "LINEAR";
                Model.Channel ch = new Model.Channel();
                ch.node = node;
                ch.path = pi;
                ch.comps = pi == 1 ? 4 : 3;
                ch.interpolation = interp.equals("STEP") ? 1 : interp.equals("CUBICSPLINE") ? 2 : 0;
                ch.times = in.data();
                ch.values = outv.data();
                int need = in.count() * ch.comps * (ch.interpolation == 2 ? 3 : 1);
                if (in.count() == 0 || outv.comps() != ch.comps || ch.values.length < need) {
                    continue;
                }
                anim.channels.add(ch);
                for (float t : ch.times) {
                    anim.duration = Math.max(anim.duration, t);
                }
            }
            if (!anim.channels.isEmpty() && anim.duration > 0) {
                model.animations.add(anim);
            }
        }
    }

    private Acc read(int index) throws IOException {
        JsonArray accessors = array(root, "accessors");
        if (index < 0 || index >= accessors.size()) {
            throw new IOException("Accessor " + index + " is missing");
        }
        JsonObject a = accessors.get(index).getAsJsonObject();
        int count = a.get("count").getAsInt();
        int comps = switch (a.get("type").getAsString()) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4", "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> throw new IOException("Unknown accessor type");
        };
        int ct = a.get("componentType").getAsInt();
        int size = switch (ct) {
            case 5120, 5121 -> 1;
            case 5122, 5123 -> 2;
            case 5125, 5126 -> 4;
            default -> throw new IOException("Unknown component type " + ct);
        };
        boolean normalized = a.has("normalized") && a.get("normalized").getAsBoolean();
        float[] out = new float[count * comps];
        if (!a.has("bufferView")) {
            return new Acc(out, comps, count);
        }
        JsonArray views = array(root, "bufferViews");
        JsonObject bv = views.get(a.get("bufferView").getAsInt()).getAsJsonObject();
        byte[] buf = buffers.get(bv.get("buffer").getAsInt());
        int start = (bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0) + (a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0);
        int elem = size * comps;
        int stride = bv.has("byteStride") && bv.get("byteStride").getAsInt() > 0 ? bv.get("byteStride").getAsInt() : elem;
        if (count > 0 && (start < 0 || (long) start + (long) (count - 1) * stride + elem > buf.length)) {
            throw new IOException("Accessor " + index + " reads outside its buffer");
        }
        ByteBuffer b = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            int o = start + i * stride;
            for (int c = 0; c < comps; c++) {
                int p = o + c * size;
                float v;
                switch (ct) {
                    case 5126 -> v = b.getFloat(p);
                    case 5121 -> v = (b.get(p) & 255) / (normalized ? 255f : 1f);
                    case 5123 -> v = (b.getShort(p) & 0xFFFF) / (normalized ? 65535f : 1f);
                    case 5120 -> v = normalized ? Math.max(b.get(p) / 127f, -1f) : b.get(p);
                    case 5122 -> v = normalized ? Math.max(b.getShort(p) / 32767f, -1f) : b.getShort(p);
                    default -> v = (float) (b.getInt(p) & 0xFFFFFFFFL);
                }
                out[i * comps + c] = v;
            }
        }
        return new Acc(out, comps, count);
    }

    private BufferedImage textureImage(int textureIndex) {
        try {
            JsonArray textures = array(root, "textures");
            if (textureIndex < 0 || textureIndex >= textures.size()) {
                return null;
            }
            JsonObject t = textures.get(textureIndex).getAsJsonObject();
            if (!t.has("source")) {
                return null;
            }
            int src = t.get("source").getAsInt();
            if (images.containsKey(src)) {
                return images.get(src);
            }
            JsonObject im = array(root, "images").get(src).getAsJsonObject();
            byte[] bytes;
            if (im.has("uri")) {
                bytes = readUri(im.get("uri").getAsString());
            } else {
                JsonObject bv = array(root, "bufferViews").get(im.get("bufferView").getAsInt()).getAsJsonObject();
                byte[] buf = buffers.get(bv.get("buffer").getAsInt());
                int off = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
                bytes = java.util.Arrays.copyOfRange(buf, off, off + bv.get("byteLength").getAsInt());
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            images.put(src, img);
            return img;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private byte[] readUri(String uri) throws IOException {
        if (uri.startsWith("data:")) {
            int comma = uri.indexOf(',');
            if (comma < 0) {
                throw new IOException("Bad data address");
            }
            String head = uri.substring(0, comma);
            String body = uri.substring(comma + 1);
            return head.endsWith(";base64") ? Base64.getDecoder().decode(body.replaceAll("\\s", ""))
                    : URLDecoder.decode(body, StandardCharsets.ISO_8859_1).getBytes(StandardCharsets.ISO_8859_1);
        }
        if (uri.contains("://")) {
            throw new IOException("The model loads data from the internet; not allowed");
        }
        String rel = URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8);
        Path p = dir.resolve(rel).normalize();
        if (!Files.isRegularFile(p)) {
            throw new IOException("Missing file next to the model: " + rel);
        }
        return Files.readAllBytes(p);
    }

    private static JsonArray array(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static float[] floats(JsonArray a, int n) {
        float[] f = new float[n];
        for (int i = 0; i < n && i < a.size(); i++) {
            f[i] = a.get(i).getAsFloat();
        }
        return f;
    }

    static int pack(float r, float g, float b, float a) {
        return clamp(a) << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    private static int clamp(float f) {
        return Math.max(0, Math.min(255, Math.round(f * 255f)));
    }
}
