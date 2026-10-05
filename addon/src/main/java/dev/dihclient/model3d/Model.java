package dev.dihclient.model3d;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class Model {
    public static final class Channel {
        public int node;

        public int path;

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

    public int[] joints;
    public float[] weights;

    public int[] rigidNode;

    public int nodeCount;
    public int[] parent;
    public float[] restT;
    public float[] restR;
    public float[] restS;

    public int[] order;
    public int[] jointNode = new int[0];
    public float[] invBind = new float[0];

    public final List<Animation> animations = new ArrayList<>();
    public BufferedImage texture;

    public float minX, minY, minZ, maxX, maxY, maxZ;

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
