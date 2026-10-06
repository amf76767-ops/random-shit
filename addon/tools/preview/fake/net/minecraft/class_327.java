package net.minecraft;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.io.InputStream;

public class class_327 {
    public static final float SIZE = 9.0F;
    public static final Font BASE = load();
    private static final FontRenderContext FRC = new FontRenderContext(new AffineTransform(), true, true);

    private static Font load() {
        try (InputStream in = class_327.class.getResourceAsStream("/assets/dihclient/font/inter-regular.ttf")) {
            if (in != null) {
                return Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(SIZE);
            }
        } catch (Exception e) {
        }
        return new Font("SansSerif", Font.PLAIN, 9);
    }

    public int method_1727(String s) {
        return (int) Math.ceil(BASE.getStringBounds(s, FRC).getWidth());
    }

    public int method_30880(class_5481 s) {
        return method_1727(s.text);
    }
}
