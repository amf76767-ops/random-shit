package dev.dihclient.model3d;

/** Column-major 4x4 matrices in plain float arrays (the glTF layout): element (row r, column c) is m[c * 4 + r]. */
public final class Mat4 {
    private Mat4() {
    }

    public static float[] identity() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    /** out = a * b. {@code out} must not be the same array as a or b. */
    public static void mul(float[] a, int ao, float[] b, int bo, float[] out, int oo) {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                float s = 0;
                for (int k = 0; k < 4; k++) {
                    s += a[ao + k * 4 + r] * b[bo + c * 4 + k];
                }
                out[oo + c * 4 + r] = s;
            }
        }
    }

    /** Translation (t), rotation quaternion (x, y, z, w) and scale (s) as one matrix, written at {@code oo}. */
    public static void trs(float[] t, int to, float[] q, int qo, float[] s, int so, float[] out, int oo) {
        float x = q[qo], y = q[qo + 1], z = q[qo + 2], w = q[qo + 3];
        float n = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (n > 0) {
            x /= n;
            y /= n;
            z /= n;
            w /= n;
        } else {
            x = y = z = 0;
            w = 1;
        }
        float xx = x * x, yy = y * y, zz = z * z, xy = x * y, xz = x * z, yz = y * z, wx = w * x, wy = w * y, wz = w * z;
        float sx = s[so], sy = s[so + 1], sz = s[so + 2];
        out[oo] = (1 - 2 * (yy + zz)) * sx;
        out[oo + 1] = 2 * (xy + wz) * sx;
        out[oo + 2] = 2 * (xz - wy) * sx;
        out[oo + 3] = 0;
        out[oo + 4] = 2 * (xy - wz) * sy;
        out[oo + 5] = (1 - 2 * (xx + zz)) * sy;
        out[oo + 6] = 2 * (yz + wx) * sy;
        out[oo + 7] = 0;
        out[oo + 8] = 2 * (xz + wy) * sz;
        out[oo + 9] = 2 * (yz - wx) * sz;
        out[oo + 10] = (1 - 2 * (xx + yy)) * sz;
        out[oo + 11] = 0;
        out[oo + 12] = t[to];
        out[oo + 13] = t[to + 1];
        out[oo + 14] = t[to + 2];
        out[oo + 15] = 1;
    }

    /** Splits an affine matrix into translation, rotation quaternion and scale. */
    public static void decompose(float[] m, float[] t, float[] q, float[] s) {
        t[0] = m[12];
        t[1] = m[13];
        t[2] = m[14];
        float sx = len(m[0], m[1], m[2]);
        float sy = len(m[4], m[5], m[6]);
        float sz = len(m[8], m[9], m[10]);
        float det = m[0] * (m[5] * m[10] - m[9] * m[6]) - m[4] * (m[1] * m[10] - m[9] * m[2]) + m[8] * (m[1] * m[6] - m[5] * m[2]);
        if (det < 0) {
            sx = -sx;
        }
        s[0] = sx;
        s[1] = sy;
        s[2] = sz;
        float isx = sx == 0 ? 0 : 1 / sx, isy = sy == 0 ? 0 : 1 / sy, isz = sz == 0 ? 0 : 1 / sz;
        float r00 = m[0] * isx, r10 = m[1] * isx, r20 = m[2] * isx;
        float r01 = m[4] * isy, r11 = m[5] * isy, r21 = m[6] * isy;
        float r02 = m[8] * isz, r12 = m[9] * isz, r22 = m[10] * isz;
        float tr = r00 + r11 + r22;
        float x, y, z, w;
        if (tr > 0) {
            float k = (float) Math.sqrt(tr + 1) * 2;
            w = 0.25f * k;
            x = (r21 - r12) / k;
            y = (r02 - r20) / k;
            z = (r10 - r01) / k;
        } else if (r00 > r11 && r00 > r22) {
            float k = (float) Math.sqrt(1 + r00 - r11 - r22) * 2;
            w = (r21 - r12) / k;
            x = 0.25f * k;
            y = (r01 + r10) / k;
            z = (r02 + r20) / k;
        } else if (r11 > r22) {
            float k = (float) Math.sqrt(1 + r11 - r00 - r22) * 2;
            w = (r02 - r20) / k;
            x = (r01 + r10) / k;
            y = 0.25f * k;
            z = (r12 + r21) / k;
        } else {
            float k = (float) Math.sqrt(1 + r22 - r00 - r11) * 2;
            w = (r10 - r01) / k;
            x = (r02 + r20) / k;
            y = (r12 + r21) / k;
            z = 0.25f * k;
        }
        q[0] = x;
        q[1] = y;
        q[2] = z;
        q[3] = w;
    }

    private static float len(float a, float b, float c) {
        return (float) Math.sqrt(a * a + b * b + c * c);
    }
}
