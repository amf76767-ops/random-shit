package dev.dihclient;

import dev.dihclient.model3d.Model;
import dev.dihclient.model3d.ModelLoader;
import dev.dihclient.model3d.Rig;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Model3dTests {
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

    static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("m3d");
        skinned(tmp);
        obj(tmp);
        sample();
        voxels(tmp);
        broken(tmp);
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    /** A triangle with two joints, the second one slides up between t=0 and t=1. Also tests missing normals. */
    static void skinned(Path tmp) throws Exception {
        ByteArrayOutputStream bin = new ByteArrayOutputStream();
        int[] off = new int[8];
        off[0] = bin.size();
        put(bin, 0, 0, 0, 1, 0, 0, 0, 2, 0); // positions
        off[1] = bin.size();
        bin.write(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0}); // joints (ubyte vec4 x3)
        off[2] = bin.size();
        put(bin, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0); // weights: every vertex fully on its first joint
        off[3] = bin.size();
        bin.write(new byte[]{0, 0, 1, 0, 2, 0, 0, 0}); // indices ushort x3 + pad
        off[4] = bin.size();
        put(bin, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1);
        put(bin, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, -1, 0, 1); // joint 1 inverse bind: translate(0,-1,0)
        off[5] = bin.size();
        put(bin, 0, 1); // times
        off[6] = bin.size();
        put(bin, 0, 1, 0, 0, 3, 0); // translation keys
        String json = "{\"asset\":{\"version\":\"2.0\"},\"scene\":0,\"scenes\":[{\"nodes\":[0,2]}],"
                + "\"nodes\":[{\"name\":\"root\",\"children\":[1]},{\"name\":\"child\",\"translation\":[0,1,0]},{\"mesh\":0,\"skin\":0}],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0,\"JOINTS_0\":1,\"WEIGHTS_0\":2},\"indices\":3}]}],"
                + "\"skins\":[{\"joints\":[0,1],\"inverseBindMatrices\":4}],"
                + "\"animations\":[{\"name\":\"Rise\",\"channels\":[{\"sampler\":0,\"target\":{\"node\":1,\"path\":\"translation\"}}],"
                + "\"samplers\":[{\"input\":5,\"output\":6}]}],"
                + "\"accessors\":["
                + "{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                + "{\"bufferView\":1,\"componentType\":5121,\"count\":3,\"type\":\"VEC4\"},"
                + "{\"bufferView\":2,\"componentType\":5126,\"count\":3,\"type\":\"VEC4\"},"
                + "{\"bufferView\":3,\"componentType\":5123,\"count\":3,\"type\":\"SCALAR\"},"
                + "{\"bufferView\":4,\"componentType\":5126,\"count\":2,\"type\":\"MAT4\"},"
                + "{\"bufferView\":5,\"componentType\":5126,\"count\":2,\"type\":\"SCALAR\"},"
                + "{\"bufferView\":6,\"componentType\":5126,\"count\":2,\"type\":\"VEC3\"}],"
                + "\"bufferViews\":[" + view(off[0], 36) + "," + view(off[1], 12) + "," + view(off[2], 48) + "," + view(off[3], 6) + ","
                + view(off[4], 128) + "," + view(off[5], 8) + "," + view(off[6], 24) + "],"
                + "\"buffers\":[{\"byteLength\":" + bin.size() + "}]}";
        Path f = tmp.resolve("skin.glb");
        Files.write(f, glb(json, bin.toByteArray()));
        Model m = ModelLoader.load(f);
        check(m.vertexCount == 3 && m.triCount == 1, "skinned model has 3 vertices, 1 triangle");
        check(near(m.restPos[7], 2f), "rest pose keeps the top vertex at y=2, got " + m.restPos[7]);
        check(near(m.nrm[2], 1f), "normal computed for a counter-clockwise triangle is +Z");
        check(m.animations.size() == 1 && near(m.animations.get(0).duration, 1f), "one animation, 1 second");
        float[] g = new float[m.nodeCount * 16];
        float[] p = new float[9], n = new float[9];
        Rig.pose(m, m.animations.get(0), 0.5, g);
        Rig.skin(m, g, p, n);
        check(near(p[7], 3f) && near(p[3], 1f) && near(p[1], 0f), "at t=0.5 the top vertex rose to y=3, got " + p[7]);
        Rig.pose(m, m.animations.get(0), 1.5, g); // loops: same as 0.5
        Rig.skin(m, g, p, n);
        check(near(p[7], 3f), "animation loops");
        check(m.texture != null && m.texture.getWidth() >= 2, "a white fallback texture exists");
        check(near(m.height(), 2f), "height 2");
    }

    /** A quad with negative indices, a material colour and a picture; UVs are flipped to the top-left origin. */
    static void obj(Path tmp) throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                img.setRGB(x, y, 0xFFFF0000);
            }
        }
        javax.imageio.ImageIO.write(img, "png", tmp.resolve("tex.png").toFile());
        Files.writeString(tmp.resolve("q.mtl"), "newmtl red\nKd 1 0.5 0\nmap_Kd tex.png\n");
        Files.writeString(tmp.resolve("q.obj"),
                "mtllib q.mtl\nv 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\nusemtl red\nf -4/-4 -3/-3 -2/-2 -1/-1\n");
        Model m = ModelLoader.load(tmp.resolve("q.obj"));
        check(m.vertexCount == 4 && m.triCount == 2, "quad becomes 2 triangles");
        check(near(m.nrm[2], 1f) || near(m.nrm[2], -1f), "quad normal computed");
        check(m.nrm[2] < -0.99f, "turned 180 degrees around Y: the +Z face of the file now looks along -Z");
        check((m.color[0] >> 16 & 255) == 255 && (m.color[0] >> 8 & 255) == 128, "Kd colour applied");
        check(m.texture.getWidth() == 4 + 2, "atlas = picture + 2px white patch, got " + m.texture.getWidth());
        float vTop = m.uv[1];
        check(vTop >= 0f && vTop <= 1f, "uv in range");
    }

    /** The example that ships with the client loads, has animations and a sensible size. */
    static void sample() throws Exception {
        Path f = Path.of("src/resources/assets/dihclient/models/example_robot.glb");
        check(Files.isRegularFile(f), "example model exists");
        if (!Files.isRegularFile(f)) {
            return;
        }
        Model m = ModelLoader.load(f);
        check(m.triCount > 20, "example has triangles: " + m.triCount);
        check(m.height() > 1.5f && m.height() < 2.1f, "example is about player sized: " + m.height());
        check(m.animations.size() >= 2, "example has idle and walk");
        check(m.guess("idle") != null && m.guess("walk") != null, "example animation names");
        float[] g = new float[m.nodeCount * 16];
        float[] p = new float[m.vertexCount * 3], n = new float[m.vertexCount * 3];
        Rig.pose(m, m.guess("walk"), 0.3, g);
        Rig.skin(m, g, p, n);
        boolean moved = false;
        for (int i = 0; i < p.length; i++) {
            if (Math.abs(p[i] - m.restPos[i]) > 0.01f) {
                moved = true;
            }
        }
        check(moved, "walk animation moves vertices");
    }

    /** A white cube becomes a cube of white blocks; solid or hollow. */
    static void voxels(Path tmp) throws Exception {
        Files.writeString(tmp.resolve("cube.mtl"), "newmtl w\nKd 1 1 1\n");
        StringBuilder o = new StringBuilder("mtllib cube.mtl\nusemtl w\n");
        float[][] v = {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}};
        for (float[] p : v) {
            o.append("v ").append(p[0]).append(' ').append(p[1]).append(' ').append(p[2]).append('\n');
        }
        int[][] f = {{1, 2, 3, 4}, {5, 8, 7, 6}, {1, 5, 6, 2}, {4, 3, 7, 8}, {1, 4, 8, 5}, {2, 6, 7, 3}};
        for (int[] q : f) {
            o.append("f ").append(q[0]).append(' ').append(q[1]).append(' ').append(q[2]).append(' ').append(q[3]).append('\n');
        }
        Files.writeString(tmp.resolve("cube.obj"), o.toString());
        Model m = ModelLoader.load(tmp.resolve("cube.obj"));
        dev.dihclient.model3d.Voxelizer.Grid hollow = dev.dihclient.model3d.Voxelizer.voxelize(m, 10, false, 64);
        check(hollow.sx == 10 && hollow.sy == 10 && hollow.sz == 10, "cube grid is 10^3: " + hollow.sx + "," + hollow.sy + "," + hollow.sz);
        check(hollow.count == 1000 - 512, "hollow cube has 488 surface blocks, got " + hollow.count);
        dev.dihclient.model3d.Voxelizer.Grid solid = dev.dihclient.model3d.Voxelizer.voxelize(m, 10, true, 64);
        check(solid.count == 1000, "solid cube has 1000 blocks, got " + solid.count);
        var pal = dev.dihclient.model3d.BlockPalette.entries(dev.dihclient.model3d.BlockPalette.Set.CONCRETE);
        check(dev.dihclient.model3d.BlockPalette.nearest(pal, 0xFFFFFF).id().equals("minecraft:white_concrete"), "white maps to white concrete");
        check(dev.dihclient.model3d.BlockPalette.nearest(pal, 0x000000).id().equals("minecraft:black_concrete"), "black maps to black concrete");
        check(dev.dihclient.model3d.BlockPalette.nearest(pal, 0x902020).id().equals("minecraft:red_concrete"), "red maps to red concrete: " + dev.dihclient.model3d.BlockPalette.nearest(pal, 0x902020).id());
        Model tung = ModelLoader.load(Path.of("src/resources/assets/dihclient/models/tung_tung_tung_sahur.obj"));
        dev.dihclient.model3d.Voxelizer.Grid tg = dev.dihclient.model3d.Voxelizer.voxelize(tung, 40, false, 128);
        check(tg.sy == 40 && tg.count > 500 && tg.count < 20000, "the log man at 40 blocks: " + tg.count + " blocks, " + tg.sx + "x" + tg.sy + "x" + tg.sz);
    }

    static void broken(Path tmp) throws Exception {
        Files.writeString(tmp.resolve("bad.glb"), "not a model");
        try {
            ModelLoader.load(tmp.resolve("bad.glb"));
            check(false, "garbage must be rejected");
        } catch (java.io.IOException e) {
            check(true, "garbage rejected");
        }
        Files.writeString(tmp.resolve("e.obj"), "# nothing\n");
        try {
            ModelLoader.load(tmp.resolve("e.obj"));
            check(false, "empty obj must be rejected");
        } catch (java.io.IOException e) {
            check(true, "empty obj rejected");
        }
        try {
            ModelLoader.load(tmp.resolve("x.blend"));
            check(false, "blend must be rejected");
        } catch (java.io.IOException e) {
            check(e.getMessage().contains("glTF"), "blend gives the export hint");
        }
    }

    private static String view(int offset, int length) {
        return "{\"buffer\":0,\"byteOffset\":" + offset + ",\"byteLength\":" + length + "}";
    }

    private static void put(ByteArrayOutputStream out, float... f) {
        ByteBuffer b = ByteBuffer.allocate(f.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : f) {
            b.putFloat(v);
        }
        out.writeBytes(b.array());
    }

    static byte[] glb(String json, byte[] bin) {
        byte[] j = json.getBytes(StandardCharsets.UTF_8);
        int jl = (j.length + 3) & ~3, bl = (bin.length + 3) & ~3;
        ByteBuffer b = ByteBuffer.allocate(12 + 8 + jl + 8 + bl).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x46546C67).putInt(2).putInt(b.capacity());
        b.putInt(jl).putInt(0x4E4F534A).put(j);
        for (int i = j.length; i < jl; i++) {
            b.put((byte) ' ');
        }
        b.putInt(bl).putInt(0x004E4942).put(bin);
        return b.array();
    }
}
