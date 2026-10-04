package dev.dihclient.port.noinvleak;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Not in the original: replaces its ImGui pass. Puts the fake slot pictures on the window's back buffer right before the buffers are
 * swapped, i.e. after the real background of the slots was read back and after the game drew the frame without any item in
 * those slots. The capture-excluded overlay windows then cover them for the player, a screen capture sees only these.
 * <p>
 * Plain GL blit from a small texture; every GL state that is touched goes through {@link GlStateManager} (or is read back and
 * restored), so the game's own state cache stays right.
 */
final class FakeBlitter {
    private static final int READ = 36008;
    private static final int DRAW = 36009;
    private static final int TEXTURE_2D = 3553;
    private static final int COLOR_ATTACHMENT0 = 36064;
    private static final int RGBA8 = 32856;
    private static final int BGRA = 32993;
    private static final int UNSIGNED_INT_8_8_8_8_REV = 33639;
    private static final int NEAREST = 9728;
    private static final int COLOR_BUFFER_BIT = 16384;
    private static final int TEXTURE_BINDING_2D = 32873;
    private static final int SCISSOR_TEST = 3089;

    private int texture;
    private int fbo;
    private int side;
    private ByteBuffer upload;
    private int previousRead;
    private int previousDraw;
    private int previousTexture;
    private boolean scissorWasOn;
    private boolean open;

    /** Binds everything for a run of {@link #draw} calls with pictures of {@code px} x {@code px}. */
    void begin(int px) {
        this.previousRead = GlStateManager.getFrameBuffer(READ);
        this.previousDraw = GlStateManager.getFrameBuffer(DRAW);
        this.previousTexture = GL11.glGetInteger(TEXTURE_BINDING_2D);
        this.scissorWasOn = GL11.glIsEnabled(SCISSOR_TEST);
        if (this.scissorWasOn) {
            GlStateManager._disableScissorTest();
        }
        if (this.texture == 0) {
            this.texture = GlStateManager._genTexture();
        }
        if (this.fbo == 0) {
            this.fbo = GlStateManager.glGenFramebuffers();
        }
        GlStateManager._bindTexture(this.texture);
        if (this.side != px) {
            GlStateManager._texParameter(TEXTURE_2D, 10241, NEAREST);
            GlStateManager._texParameter(TEXTURE_2D, 10240, NEAREST);
            GlStateManager._texImage2D(TEXTURE_2D, 0, RGBA8, px, px, 0, BGRA, UNSIGNED_INT_8_8_8_8_REV, BufferUtils.createByteBuffer(px * px * 4));
            this.upload = BufferUtils.createByteBuffer(px * px * 4).order(ByteOrder.nativeOrder());
            this.side = px;
        }
        GlStateManager._glBindFramebuffer(READ, this.fbo);
        GlStateManager._glFramebufferTexture2D(READ, COLOR_ATTACHMENT0, TEXTURE_2D, this.texture, 0);
        GlStateManager._glBindFramebuffer(DRAW, 0);
        this.open = true;
    }

    /**
     * One picture (opaque ARGB, top row first) with its top-left corner at (x, yTop) counted from the window's top edge.
     */
    void draw(int[] argb, int x, int yTop, int windowHeight) {
        int px = this.side;
        this.upload.clear();
        this.upload.asIntBuffer().put(argb, 0, px * px);
        this.upload.limit(px * px * 4);
        GlStateManager._texSubImage2D(TEXTURE_2D, 0, 0, 0, px, px, BGRA, UNSIGNED_INT_8_8_8_8_REV, this.upload);
        int bottom = windowHeight - yTop - px;
        // source rows are flipped (y1 < y0): GL's origin is the bottom-left, the picture's top row comes first
        GlStateManager._glBlitFrameBuffer(0, px, px, 0, x, bottom, x + px, bottom + px, COLOR_BUFFER_BIT, NEAREST);
    }

    void end() {
        if (this.open) {
            this.open = false;
            GlStateManager._glBindFramebuffer(READ, this.previousRead);
            GlStateManager._glBindFramebuffer(DRAW, this.previousDraw);
            GlStateManager._bindTexture(this.previousTexture);
            if (this.scissorWasOn) {
                GlStateManager._enableScissorTest();
            }
        }
    }

    void release() {
        if (this.open) {
            this.end();
        }
        if (this.fbo != 0) {
            GlStateManager._glDeleteFramebuffers(this.fbo);
            this.fbo = 0;
        }
        if (this.texture != 0) {
            GlStateManager._deleteTexture(this.texture);
            this.texture = 0;
        }
        this.side = 0;
        this.upload = null;
    }
}
