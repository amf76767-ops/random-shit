package dev.dihclient.port.noinvleak;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.class_10366;
import net.minecraft.class_10444;
import net.minecraft.class_11278;
import net.minecraft.class_308.class_11274;
import net.minecraft.class_310;
import net.minecraft.class_4587;
import net.minecraft.class_4608;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/**
 * Ported from an open-source client (GPL-3.0) (the render step of its ItemIcons).
 * <p>
 * Draws one item model, lit like in a GUI slot, into a square cell of an off-screen texture. All render state it touches is put
 * back afterwards so the frame that is being built is not disturbed.
 */
final class ItemRender {
    private static final int[] VIEWPORT = new int[4];
    private static class_11278 projection;

    private ItemRender() {
    }

    static void render(class_10444 model, GpuTexture target, GpuTextureView targetView, GpuTexture targetDepth,
            GpuTextureView targetDepthView, int x, int y, int cell) {
        class_310 mc = class_310.method_1551();
        int side = target.getWidth(0);
        int glY = side - y - cell;
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target, 0, targetDepth, 1.0, x, glY, cell, cell);
        GpuTextureView colorBefore = RenderSystem.outputColorTextureOverride;
        GpuTextureView depthBefore = RenderSystem.outputDepthTextureOverride;
        GpuBufferSlice lightsBefore = RenderSystem.getShaderLights();
        GpuBufferSlice projectionBefore = RenderSystem.getProjectionMatrixBuffer();
        class_10366 projectionTypeBefore = RenderSystem.getProjectionType();
        GL11.glGetIntegerv(2978, VIEWPORT);
        GlStateManager._viewport(VIEWPORT[0], VIEWPORT[1], VIEWPORT[2], VIEWPORT[3]);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        try {
            RenderSystem.outputColorTextureOverride = targetView;
            RenderSystem.outputDepthTextureOverride = targetDepthView;
            if (projection == null) {
                projection = new class_11278("DIH icons", -1000.0F, 1000.0F, true);
            }
            RenderSystem.setProjectionMatrix(projection.method_71092(side, side), class_10366.field_54954);
            class_11274 light = model.method_65608() ? class_11274.field_60027 : class_11274.field_60026;
            mc.field_1773.method_71114().method_71034(light);
            RenderSystem.enableScissorForRenderTypeDraws(x, glY, cell, cell);
            class_4587 pose = new class_4587();
            pose.method_46416(x + cell * 0.5F, y + cell * 0.5F, 0.0F);
            pose.method_22905(cell, -cell, cell);
            model.method_65604(pose, mc.field_1773.method_72910(), 15728880, class_4608.field_21444, 0);
            mc.field_1773.method_72911().method_73002();
            mc.method_22940().method_23000().method_22993();
        } finally {
            RenderSystem.disableScissorForRenderTypeDraws();
            RenderSystem.outputColorTextureOverride = colorBefore;
            RenderSystem.outputDepthTextureOverride = depthBefore;
            RenderSystem.setShaderLights(lightsBefore);
            RenderSystem.setProjectionMatrix(projectionBefore, projectionTypeBefore);
            modelView.popMatrix();
            GlStateManager._viewport(VIEWPORT[0], VIEWPORT[1], VIEWPORT[2], VIEWPORT[3]);
        }
    }
}
