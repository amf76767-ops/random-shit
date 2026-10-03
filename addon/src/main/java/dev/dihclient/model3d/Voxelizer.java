package dev.dihclient.model3d;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns a model into a grid of coloured blocks: every triangle is sampled densely, each block the surface touches gets
 * the average colour of the samples (texture × material colour). Optionally the inside is filled with the colour of the
 * nearest surface block.
 */
public final class Voxelizer {
    private Voxelizer() {
    }

    public static final class Grid {
        public final int sx;
        public final int sy;
        public final int sz;
        /** 0 = empty, otherwise 0xFF000000 | rgb; index = (y * sz + z) * sx + x */
        public final int[] rgb;
        public int count;

        Grid(int sx, int sy, int sz) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.rgb = new int[sx * sy * sz];
        }

        public int index(int x, int y, int z) {
            return (y * sz + z) * sx + x;
        }
    }

    /**
     * @param height  wanted height in blocks (the model is scaled to it), capped so no side is longer than {@code maxSide}
     * @param solid   fill the inside as well
     */
    public static Grid voxelize(Model m, int height, boolean solid, int maxSide) {
        float ex = m.maxX - m.minX, ey = m.maxY - m.minY, ez = m.maxZ - m.minZ;
        double scale = (double) height / Math.max((double) ey, 1e-6);
        double longest = Math.max(ex, Math.max(ey, ez));
        scale = Math.min(scale, (maxSide - 1) / Math.max(longest, 1e-6));
        int sx = size(ex * scale), sy = size(ey * scale), sz = size(ez * scale);
        Grid g = new Grid(sx, sy, sz);

        Map<Integer, long[]> acc = new HashMap<>();
        float[] p = m.restPos;
        BufferedImage tex = m.texture;
        int tw = tex.getWidth(), th = tex.getHeight();
        int[] texels = tex.getRGB(0, 0, tw, th, null, 0, tw);
        double[] a = new double[3], b = new double[3], c = new double[3];
        for (int t = 0; t < m.triCount; t++) {
            int i0 = m.idx[t * 3], i1 = m.idx[t * 3 + 1], i2 = m.idx[t * 3 + 2];
            load(p, i0, m, scale, a);
            load(p, i1, m, scale, b);
            load(p, i2, m, scale, c);
            double longestEdge = Math.max(dist(a, b), Math.max(dist(b, c), dist(c, a)));
            int n = (int) Math.min(220, Math.ceil(longestEdge / 0.3) + 1);
            for (int i = 0; i <= n; i++) {
                for (int j = 0; j <= n - i; j++) {
                    double u = (double) i / n, v = (double) j / n, w = 1 - u - v;
                    double x = w * a[0] + u * b[0] + v * c[0], y = w * a[1] + u * b[1] + v * c[1], z = w * a[2] + u * b[2] + v * c[2];
                    int vx = clamp((int) Math.floor(x), sx), vy = clamp((int) Math.floor(y), sy), vz = clamp((int) Math.floor(z), sz);
                    float tu = (float) (w * m.uv[i0 * 2] + u * m.uv[i1 * 2] + v * m.uv[i2 * 2]);
                    float tv = (float) (w * m.uv[i0 * 2 + 1] + u * m.uv[i1 * 2 + 1] + v * m.uv[i2 * 2 + 1]);
                    int px = Math.max(0, Math.min(tw - 1, (int) (tu * tw))), py = Math.max(0, Math.min(th - 1, (int) (tv * th)));
                    int texel = texels[py * tw + px];
                    int vc = blend(m.color[i0], m.color[i1], m.color[i2], w, u, v);
                    int col = mul(texel, vc);
                    if ((col >>> 24) < 40) {
                        continue; // see-through part
                    }
                    long[] s = acc.computeIfAbsent(g.index(vx, vy, vz), k -> new long[4]);
                    s[0] += col >> 16 & 255;
                    s[1] += col >> 8 & 255;
                    s[2] += col & 255;
                    s[3]++;
                }
            }
        }
        for (Map.Entry<Integer, long[]> e : acc.entrySet()) {
            long[] s = e.getValue();
            g.rgb[e.getKey()] = 0xFF000000 | (int) (s[0] / s[3]) << 16 | (int) (s[1] / s[3]) << 8 | (int) (s[2] / s[3]);
        }
        if (solid) {
            fill(g);
        }
        for (int v : g.rgb) {
            if (v != 0) {
                g.count++;
            }
        }
        return g;
    }

    private static int size(double extent) {
        return Math.max(1, (int) Math.ceil(extent - 1e-6));
    }

    private static void load(float[] p, int vertex, Model m, double scale, double[] out) {
        out[0] = (p[vertex * 3] - m.minX) * scale;
        out[1] = (p[vertex * 3 + 1] - m.minY) * scale;
        out[2] = (p[vertex * 3 + 2] - m.minZ) * scale;
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int clamp(int v, int size) {
        return Math.max(0, Math.min(size - 1, v));
    }

    private static int blend(int c0, int c1, int c2, double w, double u, double v) {
        int a = (int) (w * (c0 >>> 24) + u * (c1 >>> 24) + v * (c2 >>> 24));
        int r = (int) (w * (c0 >> 16 & 255) + u * (c1 >> 16 & 255) + v * (c2 >> 16 & 255));
        int g = (int) (w * (c0 >> 8 & 255) + u * (c1 >> 8 & 255) + v * (c2 >> 8 & 255));
        int b = (int) (w * (c0 & 255) + u * (c1 & 255) + v * (c2 & 255));
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int mul(int a, int b) {
        int al = (a >>> 24) * (b >>> 24) / 255;
        int r = (a >> 16 & 255) * (b >> 16 & 255) / 255;
        int g = (a >> 8 & 255) * (b >> 8 & 255) / 255;
        int bl = (a & 255) * (b & 255) / 255;
        return al << 24 | r << 16 | g << 8 | bl;
    }

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** Everything that cannot be reached from outside without crossing the surface is inside; it takes the colour of the nearest surface block. */
    private static void fill(Grid g) {
        int px = g.sx + 2, py = g.sy + 2, pz = g.sz + 2;
        boolean[] outside = new boolean[px * py * pz];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        outside[0] = true;
        q.add(new int[]{0, 0, 0});
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : DIRS) {
                int x = c[0] + d[0], y = c[1] + d[1], z = c[2] + d[2];
                if (x < 0 || y < 0 || z < 0 || x >= px || y >= py || z >= pz) {
                    continue;
                }
                int pi = (y * pz + z) * px + x;
                if (outside[pi]) {
                    continue;
                }
                int gx = x - 1, gy = y - 1, gz = z - 1;
                boolean surface = gx >= 0 && gy >= 0 && gz >= 0 && gx < g.sx && gy < g.sy && gz < g.sz && g.rgb[g.index(gx, gy, gz)] != 0;
                if (!surface) {
                    outside[pi] = true;
                    q.add(new int[]{x, y, z});
                }
            }
        }
        // inside = not outside and empty; grow the surface colours into it
        ArrayDeque<int[]> grow = new ArrayDeque<>();
        for (int x = 0; x < g.sx; x++) {
            for (int y = 0; y < g.sy; y++) {
                for (int z = 0; z < g.sz; z++) {
                    if (g.rgb[g.index(x, y, z)] != 0) {
                        grow.add(new int[]{x, y, z});
                    }
                }
            }
        }
        while (!grow.isEmpty()) {
            int[] c = grow.poll();
            int col = g.rgb[g.index(c[0], c[1], c[2])];
            for (int[] d : DIRS) {
                int x = c[0] + d[0], y = c[1] + d[1], z = c[2] + d[2];
                if (x < 0 || y < 0 || z < 0 || x >= g.sx || y >= g.sy || z >= g.sz) {
                    continue;
                }
                if (g.rgb[g.index(x, y, z)] == 0 && !outside[((y + 1) * pz + (z + 1)) * px + (x + 1)]) {
                    g.rgb[g.index(x, y, z)] = col;
                    grow.add(new int[]{x, y, z});
                }
            }
        }
    }
}
