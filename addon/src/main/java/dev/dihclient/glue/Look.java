package dev.dihclient.glue;

import java.util.Locale;

public record Look(String id, float[] light, float[] skyMulHorizon, float[] skyMulZenith, float[] skyAddHorizon, float[] skyAddZenith,
                   float[] cloudMul, float[] cloudAdd) {
    private static final float[] ONE = {1f, 1f, 1f};
    private static final float[] ZERO = {0f, 0f, 0f};

    public static final Look NONE = new Look("none", ONE, ONE, ONE, ZERO, ZERO, ONE, ZERO);

    public static float[] rgb(float r, float g, float b) {
        return new float[]{r, g, b};
    }

    public byte[] patch(String entry, byte[] data) {
        if (this == NONE) {
            return data;
        }
        String file = entry.substring(entry.lastIndexOf('/') + 1);
        String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        String out = switch (file) {
            case "lightmap.fsh" -> set(text, "LIGHT_TINT", this.light);
            case "sky.fsh" -> set(set(set(set(text, "SKY_MUL_H", this.skyMulHorizon), "SKY_MUL_Z", this.skyMulZenith),
                    "SKY_ADD_H", this.skyAddHorizon), "SKY_ADD_Z", this.skyAddZenith);
            case "rendertype_clouds.fsh" -> set(set(text, "CLOUD_MUL", this.cloudMul), "CLOUD_ADD", this.cloudAdd);
            default -> text;
        };
        return out.equals(text) ? data : out.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    static String set(String text, String name, float[] v) {
        String vec = String.format(Locale.ROOT, "vec3(%.3f, %.3f, %.3f)", v[0], v[1], v[2]);
        return text.replaceAll("(const\\s+vec3\\s+" + name + "\\s*=\\s*)vec3\\([^)]*\\)", "$1" + vec);
    }
}
