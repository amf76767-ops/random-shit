package net.minecraft;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;

/** Java2D stand-in for the GUI drawing context. One GUI pixel is drawn as scale x scale device pixels. */
public class class_332 {
    public static int fills;
    public static int texts;
    private final BufferedImage image;
    private final Graphics2D g;
    private final int scale;
    private final Matrix3x2fStack pose = new Matrix3x2fStack(64);
    private final ArrayDeque<Rectangle> scissors = new ArrayDeque<>();

    public class_332() {
        this(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), 1);
    }

    public class_332(BufferedImage image, int scale) {
        this.image = image;
        this.scale = scale;
        this.g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    public BufferedImage image() {
        return image;
    }

    public Matrix3x2fStack method_51448() {
        return pose;
    }

    private Rectangle device(float x0, float y0, float x1, float y1) {
        Vector2f a = pose.transformPosition(x0, y0, new Vector2f());
        Vector2f b = pose.transformPosition(x1, y1, new Vector2f());
        int dx0 = Math.round(Math.min(a.x, b.x) * scale);
        int dy0 = Math.round(Math.min(a.y, b.y) * scale);
        int dx1 = Math.round(Math.max(a.x, b.x) * scale);
        int dy1 = Math.round(Math.max(a.y, b.y) * scale);
        return new Rectangle(dx0, dy0, dx1 - dx0, dy1 - dy0);
    }

    public void method_25294(int x0, int y0, int x1, int y1, int color) {
        fills++;
        Rectangle r = device(x0, y0, x1, y1);
        if (!scissors.isEmpty()) {
            r = r.intersection(scissors.peek());
        }
        if (r.width <= 0 || r.height <= 0) {
            return;
        }
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(color, true));
        g.fillRect(r.x, r.y, r.width, r.height);
    }

    public void method_25296(int x0, int y0, int x1, int y1, int top, int bottom) {
        Rectangle r = device(x0, y0, x1, y1);
        if (!scissors.isEmpty()) {
            r = r.intersection(scissors.peek());
        }
        if (r.width <= 0 || r.height <= 0) {
            return;
        }
        g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new GradientPaint(0, r.y, new Color(top, true), 0, r.y + r.height, new Color(bottom, true)));
        g.fillRect(r.x, r.y, r.width, r.height);
    }

    public void method_44379(int x0, int y0, int x1, int y1) {
        Rectangle r = device(x0, y0, x1, y1);
        if (!scissors.isEmpty()) {
            r = r.intersection(scissors.peek());
        }
        scissors.push(r);
    }

    public void method_44380() {
        scissors.pop();
    }

    private void text(String s, int x, int y, int color, boolean shadow) {
        texts++;
        float sx = pose.m00();
        Vector2f p = pose.transformPosition(x, y, new Vector2f());
        java.awt.Font f = class_327.BASE.deriveFont(class_327.SIZE * scale * sx);
        g.setFont(f);
        g.setClip(scissors.isEmpty() ? null : scissors.peek());
        g.setComposite(AlphaComposite.SrcOver);
        float bx = p.x * scale;
        float by = (p.y + 7.4F * sx) * scale;
        if (shadow) {
            int a = (color >>> 24) / 4;
            g.setColor(new Color(((color >> 16 & 0xFF) >> 2) << 16 | ((color >> 8 & 0xFF) >> 2) << 8 | (color & 0xFF) >> 2 | a << 24, true));
            g.drawString(s, bx + scale * sx, by + scale * sx);
        }
        g.setColor(new Color(color, true));
        g.drawString(s, bx, by);
        g.setClip(null);
    }

    public void method_51433(class_327 font, String s, int x, int y, int color, boolean shadow) {
        text(s, x, y, color, shadow);
    }

    public void method_51430(class_327 font, class_5481 s, int x, int y, int color, boolean shadow) {
        text(s.text, x, y, color, shadow);
    }

    public void method_51439(class_327 font, class_2561 s, int x, int y, int color, boolean shadow) {
        text(s.getString(), x, y, color, shadow);
    }
}
