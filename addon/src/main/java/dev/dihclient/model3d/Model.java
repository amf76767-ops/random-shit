package dev.dihclient.model3d;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * A loaded 3D model, independent of Minecraft. All vertices live in one set of arrays; the triangles are in {@link #idx}.
 * Static parts are bound to one node ({@link #rigidNode}), skinned vertices to up to four joints ({@link #joints}).
 */
public final class Model {
    public static final class Channel {
        public int node;
        /** 0 translation, 1 rotation, 2 scale */
        public int path;
        /** 0 linear, 1 step, 2 cubic spline */
        public int interpolation;
        public float[] times;
        public float[] values;
        public int comps;
    }

    public static final class Animation {
        public String name = "";
        public float duration;
        public final List<Channel> channels = new ArrayList<>();
    }

    public String name = "";
    public int vertexCount;
    public int triCount;
    public float[] pos;
    public float[] nrm;
    public float[] uv;
    public int[] color;
    public int[] idx;
    /** 4 joint indices per vertex (into {@link #jointNode}), null when the model has no skin. */
    public int[] joints;
    public float[] weights;
    /** node a vertex is attached to, or -1 for skinned vertices */
    public int[] rigidNode;

    public int nodeCount;
    public int[] parent;
    public float[] restT;
    public float[] restR;
    public float[] restS;
    /** nodes sorted so that every parent comes before its children */
    public int[] order;
    public int[] jointNode = new int[0];
    public float[] invBind = new float[0];

    public final List<Animation> animations = new ArrayList<>();
    public BufferedImage texture;

    public float minX, minY, minZ, maxX, maxY, maxZ;
    /** positions and normals in the rest pose, in model space */
    public float[] restPos;
    public float[] restNrm;

    public float height() {
        return maxY - minY;
    }

    public boolean hasAnimations() {
        return !animations.isEmpty();
    }

    public Animation findAnimation(String wanted) {
        if (wanted == null || wanted.isBlank()) {
            return null;
        }
        String w = wanted.trim().toLowerCase(java.util.Locale.ROOT);
        for (Animation a : animations) {
            if (a.name.equalsIgnoreCase(w)) {
                return a;
            }
        }
        for (Animation a : animations) {
            if (a.name.toLowerCase(java.util.Locale.ROOT).contains(w)) {
                return a;
            }
        }
        return null;
    }

    /** First animation whose name contains one of the words, or null. */
    public Animation guess(String... words) {
        for (String w : words) {
            for (Animation a : animations) {
                if (a.name.toLowerCase(java.util.Locale.ROOT).contains(w)) {
                    return a;
                }
            }
        }
        return null;
    }
}
