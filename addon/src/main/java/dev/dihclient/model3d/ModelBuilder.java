package dev.dihclient.model3d;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Collects the geometry of a model from a loader and turns it into a {@link Model}: texture atlas, normals, bounds, rest pose. */
final class ModelBuilder {
    /** A material: colour (ARGB) and an optional picture. */
    static final class Mat {
        int color = 0xFFFFFFFF;
        BufferedImage image;
    }

    /** One piece of geometry. Arrays are per vertex; optional ones may be null. */
    static final class Prim {
        float[] pos;
        float[] nrm;
        float[] uv;
        int[] color;
        int[] joints;
        float[] weights;
        int[] idx;
        int material = -1;
        /** node a static primitive hangs on */
        int node = -1;
    }

    private static final int MAX_ATLAS_WIDTH = 4096;
    private static final int MAX_IMAGE = 2048;

    private final List<Mat> mats = new ArrayList<>();
    private final Model model = new Model();
    private float[] pos = new float[0], nrm = new float[0], uv = new float[0];
    private int[] color = new int[0], rigid = new int[0], matOf = new int[0], idx = new int[0];
    private int[] joints = new int[0];
    private float[] weights = new float[0];
    private boolean skinned;
    private int vertices, indices;

    int addMaterial(Mat m) {
        mats.add(m);
        return mats.size() - 1;
    }

    Model model() {
        return model;
    }

    void addPrimitive(Prim p) {
        int n = p.pos.length / 3;
        if (n == 0 || p.idx.length < 3) {
            return;
        }
        p.idx = validTriangles(p.idx, n);
        if (p.idx.length < 3) {
            return;
        }
        ensure(vertices + n, indices + p.idx.length);
        System.arraycopy(p.pos, 0, pos, vertices * 3, n * 3);
        Mat mat = p.material >= 0 && p.material < mats.size() ? mats.get(p.material) : null;
        int base = mat == null ? 0xFFFFFFFF : mat.color;
        for (int i = 0; i < n; i++) {
            int o = (vertices + i) * 3;
            if (p.nrm != null) {
                nrm[o] = p.nrm[i * 3];
                nrm[o + 1] = p.nrm[i * 3 + 1];
                nrm[o + 2] = p.nrm[i * 3 + 2];
            } else {
                nrm[o] = Float.NaN;
            }
            if (p.uv != null) {
                uv[(vertices + i) * 2] = p.uv[i * 2];
                uv[(vertices + i) * 2 + 1] = p.uv[i * 2 + 1];
            }
            int c = p.color == null ? 0xFFFFFFFF : p.color[i];
            color[vertices + i] = mul(base, c);
            matOf[vertices + i] = p.uv != null ? p.material : -1;
            boolean skin = p.joints != null && p.weights != null && weightSum(p.weights, i) > 1e-6f;
            if (skin) {
                skinned = true;
                float sum = weightSum(p.weights, i);
                for (int k = 0; k < 4; k++) {
                    joints[(vertices + i) * 4 + k] = p.joints[i * 4 + k];
                    weights[(vertices + i) * 4 + k] = p.weights[i * 4 + k] / sum;
                }
                rigid[vertices + i] = -1;
            } else {
                rigid[vertices + i] = Math.max(0, p.node);
            }
        }
        for (int i = 0; i < p.idx.length; i++) {
            idx[indices + i] = p.idx[i] + vertices;
        }
        vertices += n;
        indices += p.idx.length - p.idx.length % 3;
    }

    /** Drops triangles that point outside the vertex list (broken files must not crash the renderer). */
    private static int[] validTriangles(int[] idx, int vertexCount) {
        int[] out = new int[idx.length - idx.length % 3];
        int k = 0;
        for (int t = 0; t + 2 < idx.length; t += 3) {
            int a = idx[t], b = idx[t + 1], c = idx[t + 2];
            if (a >= 0 && b >= 0 && c >= 0 && a < vertexCount && b < vertexCount && c < vertexCount) {
                out[k++] = a;
                out[k++] = b;
                out[k++] = c;
            }
        }
        return Arrays.copyOf(out, k);
    }

    private static float weightSum(float[] w, int v) {
        return w[v * 4] + w[v * 4 + 1] + w[v * 4 + 2] + w[v * 4 + 3];
    }

    private void ensure(int v, int i) {
        if (v * 3 > pos.length) {
            int cap = Math.max(v, vertices * 2 + 64);
            pos = Arrays.copyOf(pos, cap * 3);
            nrm = Arrays.copyOf(nrm, cap * 3);
            uv = Arrays.copyOf(uv, cap * 2);
            color = Arrays.copyOf(color, cap);
            rigid = Arrays.copyOf(rigid, cap);
            matOf = Arrays.copyOf(matOf, cap);
            joints = Arrays.copyOf(joints, cap * 4);
            weights = Arrays.copyOf(weights, cap * 4);
        }
        if (i > idx.length) {
            idx = Arrays.copyOf(idx, Math.max(i, indices * 2 + 192));
        }
    }

    static int mul(int a, int b) {
        int al = (a >>> 24) * (b >>> 24) / 255;
        int r = (a >> 16 & 255) * (b >> 16 & 255) / 255;
        int g = (a >> 8 & 255) * (b >> 8 & 255) / 255;
        int bl = (a & 255) * (b & 255) / 255;
        return al << 24 | r << 16 | g << 8 | bl;
    }

    /** Skeleton data comes from the loader; plain static models get one root node. */
    void skeleton(int count, int[] parent, float[] t, float[] r, float[] s) {
        model.nodeCount = count;
        model.parent = parent;
        model.restT = t;
        model.restR = r;
        model.restS = s;
    }

    Model finish(String name) throws IOException {
        if (vertices == 0 || indices < 3) {
            throw new IOException("The model has no triangles");
        }
        if (model.nodeCount == 0) {
            skeleton(1, new int[]{-1}, new float[3], new float[]{0, 0, 0, 1}, new float[]{1, 1, 1});
        }
        model.order = sortNodes(model.parent);
        model.name = name;
        model.vertexCount = vertices;
        model.triCount = indices / 3;
        model.pos = Arrays.copyOf(pos, vertices * 3);
        model.idx = Arrays.copyOf(idx, indices);
        model.color = Arrays.copyOf(color, vertices);
        model.rigidNode = Arrays.copyOf(rigid, vertices);
        if (skinned) {
            model.joints = Arrays.copyOf(joints, vertices * 4);
            model.weights = Arrays.copyOf(weights, vertices * 4);
        }
        model.nrm = normals();
        buildAtlas();
        float[] globals = new float[model.nodeCount * 16];
        Rig.pose(model, null, 0, globals);
        model.restPos = new float[vertices * 3];
        model.restNrm = new float[vertices * 3];
        Rig.skin(model, globals, model.restPos, model.restNrm);
        bounds();
        return model;
    }

    private static int[] sortNodes(int[] parent) {
        int n = parent.length;
        int[] order = new int[n];
        boolean[] done = new boolean[n];
        int k = 0;
        for (int pass = 0; pass < n && k < n; pass++) {
            for (int i = 0; i < n; i++) {
                if (!done[i] && (parent[i] < 0 || done[parent[i]])) {
                    done[i] = true;
                    order[k++] = i;
                }
            }
        }
        if (k < n) { // a loop in the file: whatever is left hangs on the root
            for (int i = 0; i < n; i++) {
                if (!done[i]) {
                    parent[i] = -1;
                    order[k++] = i;
                }
            }
        }
        return order;
    }

    private record Key(int x, int y, int z) {
    }

    /** Normals from the file where there are any, smooth ones (welded by position) for the rest. */
    private float[] normals() {
        float[] out = Arrays.copyOf(nrm, vertices * 3);
        boolean missing = false;
        for (int v = 0; v < vertices; v++) {
            float x = out[v * 3], y = out[v * 3 + 1], z = out[v * 3 + 2];
            if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z) || x * x + y * y + z * z < 1e-12f) {
                out[v * 3] = Float.NaN;
                missing = true;
            } else {
                float l = (float) Math.sqrt(x * x + y * y + z * z);
                out[v * 3] = x / l;
                out[v * 3 + 1] = y / l;
                out[v * 3 + 2] = z / l;
            }
        }
        if (!missing) {
            return out;
        }
        Map<Key, float[]> acc = new HashMap<>();
        for (int t = 0; t + 2 < indices; t += 3) {
            int a = idx[t], b = idx[t + 1], c = idx[t + 2];
            if (!Float.isNaN(out[a * 3]) && !Float.isNaN(out[b * 3]) && !Float.isNaN(out[c * 3])) {
                continue;
            }
            float ux = pos[b * 3] - pos[a * 3], uy = pos[b * 3 + 1] - pos[a * 3 + 1], uz = pos[b * 3 + 2] - pos[a * 3 + 2];
            float wx = pos[c * 3] - pos[a * 3], wy = pos[c * 3 + 1] - pos[a * 3 + 1], wz = pos[c * 3 + 2] - pos[a * 3 + 2];
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            for (int v : new int[]{a, b, c}) {
                if (Float.isNaN(out[v * 3])) {
                    float[] s = acc.computeIfAbsent(key(v), k -> new float[3]);
                    s[0] += nx;
                    s[1] += ny;
                    s[2] += nz;
                }
            }
        }
        for (int v = 0; v < vertices; v++) {
            if (Float.isNaN(out[v * 3])) {
                float[] s = acc.get(key(v));
                float l = s == null ? 0 : (float) Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
                if (l > 1e-12f) {
                    out[v * 3] = s[0] / l;
                    out[v * 3 + 1] = s[1] / l;
                    out[v * 3 + 2] = s[2] / l;
                } else {
                    out[v * 3] = 0;
                    out[v * 3 + 1] = 1;
                    out[v * 3 + 2] = 0;
                }
            }
        }
        return out;
    }

    private Key key(int v) {
        return new Key(Float.floatToIntBits(pos[v * 3]), Float.floatToIntBits(pos[v * 3 + 1]), Float.floatToIntBits(pos[v * 3 + 2]));
    }

    /** All pictures side by side in one texture, plus a small white patch for everything that has only a colour. */
    private void buildAtlas() {
        List<BufferedImage> pics = new ArrayList<>();
        int[] picOf = new int[mats.size()];
        Arrays.fill(picOf, -1);
        for (int m = 0; m < mats.size(); m++) {
            BufferedImage img = mats.get(m).image;
            if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
                int same = -1;
                for (int p = 0; p < pics.size(); p++) {
                    if (pics.get(p) == img) {
                        same = p;
                    }
                }
                if (same < 0) {
                    pics.add(img);
                    same = pics.size() - 1;
                }
                picOf[m] = same;
            }
        }
        int[] w = new int[pics.size()], h = new int[pics.size()], x0 = new int[pics.size()];
        long total = 2;
        for (int p = 0; p < pics.size(); p++) {
            BufferedImage img = pics.get(p);
            double s = Math.min(1.0, (double) MAX_IMAGE / Math.max(img.getWidth(), img.getHeight()));
            w[p] = Math.max(1, (int) Math.round(img.getWidth() * s));
            h[p] = Math.max(1, (int) Math.round(img.getHeight() * s));
            total += w[p];
        }
        if (total > MAX_ATLAS_WIDTH) {
            double s = (double) (MAX_ATLAS_WIDTH - 2) / (total - 2);
            total = 2;
            for (int p = 0; p < pics.size(); p++) {
                w[p] = Math.max(1, (int) (w[p] * s));
                h[p] = Math.max(1, (int) (h[p] * s));
                total += w[p];
            }
        }
        int width = (int) total;
        int height = 2;
        for (int p = 0; p < pics.size(); p++) {
            height = Math.max(height, h[p]);
        }
        BufferedImage atlas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int x = 0;
        for (int p = 0; p < pics.size(); p++) {
            x0[p] = x;
            g.drawImage(pics.get(p), x, 0, w[p], h[p], null);
            x += w[p];
        }
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(x, 0, 2, 2);
        g.dispose();
        float whiteU = (x + 1f) / width, whiteV = 1f / height;
        float[] out = new float[vertices * 2];
        for (int v = 0; v < vertices; v++) {
            int m = matOf[v];
            int p = m >= 0 && m < picOf.length ? picOf[m] : -1;
            if (p < 0) {
                out[v * 2] = whiteU;
                out[v * 2 + 1] = whiteV;
            } else {
                out[v * 2] = (x0[p] + wrap(uv[v * 2]) * w[p]) / width;
                out[v * 2 + 1] = wrap(uv[v * 2 + 1]) * h[p] / height;
            }
        }
        model.uv = out;
        model.texture = atlas;
    }

    private static float wrap(float u) {
        if (u >= 0 && u <= 1) {
            return u;
        }
        float f = u - (float) Math.floor(u);
        return Float.isNaN(f) ? 0 : f;
    }

    private void bounds() {
        float[] p = model.restPos;
        model.minX = model.minY = model.minZ = Float.MAX_VALUE;
        model.maxX = model.maxY = model.maxZ = -Float.MAX_VALUE;
        for (int v = 0; v < vertices; v++) {
            model.minX = Math.min(model.minX, p[v * 3]);
            model.minY = Math.min(model.minY, p[v * 3 + 1]);
            model.minZ = Math.min(model.minZ, p[v * 3 + 2]);
            model.maxX = Math.max(model.maxX, p[v * 3]);
            model.maxY = Math.max(model.maxY, p[v * 3 + 1]);
            model.maxZ = Math.max(model.maxZ, p[v * 3 + 2]);
        }
    }
}
