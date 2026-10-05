package dev.dihclient.model3d;

public final class Rig {
    private Rig() {
    }

    public static void pose(Model m, Model.Animation anim, double time, float[] globals) {
        int n = m.nodeCount;
        float[] t = m.restT.clone();
        float[] r = m.restR.clone();
        float[] s = m.restS.clone();
        if (anim != null && anim.duration > 0) {
            float tt = (float) (time % anim.duration);
            if (tt < 0) {
                tt += anim.duration;
            }
            float[] tmp = new float[4];
            for (Model.Channel c : anim.channels) {
                if (c.node < 0 || c.node >= n || c.times.length == 0) {
                    continue;
                }
                sample(c, tt, tmp);
                switch (c.path) {
                    case 0 -> System.arraycopy(tmp, 0, t, c.node * 3, 3);
                    case 1 -> System.arraycopy(tmp, 0, r, c.node * 4, 4);
                    default -> System.arraycopy(tmp, 0, s, c.node * 3, 3);
                }
            }
        }
        float[] local = new float[16];
        for (int k = 0; k < n; k++) {
            int i = m.order[k];
            Mat4.trs(t, i * 3, r, i * 4, s, i * 3, local, 0);
            int p = m.parent[i];
            if (p < 0) {
                System.arraycopy(local, 0, globals, i * 16, 16);
            } else {
                Mat4.mul(globals, p * 16, local, 0, globals, i * 16);
            }
        }
    }

    public static void skin(Model m, float[] globals, float[] outPos, float[] outNrm) {
        int jc = m.jointNode.length;
        float[] jm = new float[jc * 16];
        for (int j = 0; j < jc; j++) {
            Mat4.mul(globals, m.jointNode[j] * 16, m.invBind, j * 16, jm, j * 16);
        }
        float[] pos = m.pos, nrm = m.nrm;
        for (int v = 0; v < m.vertexCount; v++) {
            int o = v * 3;
            float px = pos[o], py = pos[o + 1], pz = pos[o + 2];
            float nx = nrm[o], ny = nrm[o + 1], nz = nrm[o + 2];
            float ox = 0, oy = 0, oz = 0, qx = 0, qy = 0, qz = 0;
            int node = m.rigidNode[v];
            if (node >= 0) {
                int b = node * 16;
                float[] g = globals;
                ox = g[b] * px + g[b + 4] * py + g[b + 8] * pz + g[b + 12];
                oy = g[b + 1] * px + g[b + 5] * py + g[b + 9] * pz + g[b + 13];
                oz = g[b + 2] * px + g[b + 6] * py + g[b + 10] * pz + g[b + 14];
                qx = g[b] * nx + g[b + 4] * ny + g[b + 8] * nz;
                qy = g[b + 1] * nx + g[b + 5] * ny + g[b + 9] * nz;
                qz = g[b + 2] * nx + g[b + 6] * ny + g[b + 10] * nz;
            } else if (m.joints != null) {
                for (int k = 0; k < 4; k++) {
                    float w = m.weights[v * 4 + k];
                    if (w == 0) {
                        continue;
                    }
                    int b = m.joints[v * 4 + k] * 16;
                    ox += w * (jm[b] * px + jm[b + 4] * py + jm[b + 8] * pz + jm[b + 12]);
                    oy += w * (jm[b + 1] * px + jm[b + 5] * py + jm[b + 9] * pz + jm[b + 13]);
                    oz += w * (jm[b + 2] * px + jm[b + 6] * py + jm[b + 10] * pz + jm[b + 14]);
                    qx += w * (jm[b] * nx + jm[b + 4] * ny + jm[b + 8] * nz);
                    qy += w * (jm[b + 1] * nx + jm[b + 5] * ny + jm[b + 9] * nz);
                    qz += w * (jm[b + 2] * nx + jm[b + 6] * ny + jm[b + 10] * nz);
                }
            } else {
                ox = px;
                oy = py;
                oz = pz;
                qx = nx;
                qy = ny;
                qz = nz;
            }
            float l = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (l > 1e-8f) {
                qx /= l;
                qy /= l;
                qz /= l;
            } else {
                qx = 0;
                qy = 1;
                qz = 0;
            }
            outPos[o] = ox;
            outPos[o + 1] = oy;
            outPos[o + 2] = oz;
            outNrm[o] = qx;
            outNrm[o + 1] = qy;
            outNrm[o + 2] = qz;
        }
    }

    static void sample(Model.Channel c, float t, float[] out) {
        float[] times = c.times;
        int n = times.length;
        int comps = c.comps;
        boolean cubic = c.interpolation == 2;
        int stride = cubic ? comps * 3 : comps;
        int valueOffset = cubic ? comps : 0;
        if (n == 1 || t <= times[0]) {
            System.arraycopy(c.values, valueOffset, out, 0, comps);
            return;
        }
        if (t >= times[n - 1]) {
            System.arraycopy(c.values, (n - 1) * stride + valueOffset, out, 0, comps);
            return;
        }
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] <= t) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        float dt = times[hi] - times[lo];
        float f = dt <= 0 ? 0 : (t - times[lo]) / dt;
        int a = lo * stride, b = hi * stride;
        if (c.interpolation == 1) {
            System.arraycopy(c.values, a + valueOffset, out, 0, comps);
        } else if (cubic) {
            float f2 = f * f, f3 = f2 * f;
            float h00 = 2 * f3 - 3 * f2 + 1, h10 = f3 - 2 * f2 + f, h01 = -2 * f3 + 3 * f2, h11 = f3 - f2;
            for (int i = 0; i < comps; i++) {
                float v0 = c.values[a + comps + i], out0 = c.values[a + 2 * comps + i];
                float in1 = c.values[b + i], v1 = c.values[b + comps + i];
                out[i] = h00 * v0 + h10 * dt * out0 + h01 * v1 + h11 * dt * in1;
            }
            if (c.path == 1) {
                normalize(out);
            }
        } else if (c.path == 1) {
            slerp(c.values, a, c.values, b, f, out);
        } else {
            for (int i = 0; i < comps; i++) {
                out[i] = c.values[a + i] + (c.values[b + i] - c.values[a + i]) * f;
            }
        }
    }

    private static void slerp(float[] p, int po, float[] q, int qo, float f, float[] out) {
        float ax = p[po], ay = p[po + 1], az = p[po + 2], aw = p[po + 3];
        float bx = q[qo], by = q[qo + 1], bz = q[qo + 2], bw = q[qo + 3];
        float dot = ax * bx + ay * by + az * bz + aw * bw;
        if (dot < 0) {
            dot = -dot;
            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
        }
        float ka, kb;
        if (dot > 0.9995f) {
            ka = 1 - f;
            kb = f;
        } else {
            double th = Math.acos(dot);
            double sn = Math.sin(th);
            ka = (float) (Math.sin((1 - f) * th) / sn);
            kb = (float) (Math.sin(f * th) / sn);
        }
        out[0] = ka * ax + kb * bx;
        out[1] = ka * ay + kb * by;
        out[2] = ka * az + kb * bz;
        out[3] = ka * aw + kb * bw;
        normalize(out);
    }

    private static void normalize(float[] q) {
        float l = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (l > 0) {
            for (int i = 0; i < 4; i++) {
                q[i] /= l;
            }
        }
    }
}
