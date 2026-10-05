package dev.dihclient.mixin.port;

import dev.dihclient.port.noinvleak.BorderlessFullscreen;
import net.minecraft.class_1041;
import net.minecraft.class_313;
import net.minecraft.class_319;
import net.minecraft.class_323;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ported from an open-source client (GPL-3.0). */
@Mixin(class_1041.class)
public abstract class NoInvLeakBorderlessWindowMixin implements BorderlessFullscreen.Switchable {
    @Shadow
    @Final
    private long field_5187;
    @Shadow
    @Final
    private class_323 field_5195;
    @Shadow
    private boolean field_5191;
    @Shadow
    private boolean field_5177;
    @Shadow
    private int field_5175;
    @Shadow
    private int field_5185;
    @Shadow
    private int field_5174;
    @Shadow
    private int field_5184;
    @Shadow
    private int field_5183;
    @Shadow
    private int field_5198;
    @Shadow
    private int field_5182;
    @Shadow
    private int field_5197;
    @Unique
    private boolean dih$borderless;

    @Inject(method = "method_4479", at = @At("HEAD"), cancellable = true)
    private void dih$setMode(CallbackInfo ci) {
        try {
            if (this.field_5191 && BorderlessFullscreen.wanted()) {
                class_313 monitor = this.field_5195.method_1681((class_1041) (Object) this);
                if (monitor != null) {
                    boolean exclusive = GLFW.glfwGetWindowMonitor(this.field_5187) != 0L;
                    if (!exclusive && !this.dih$borderless) {

                        this.field_5175 = this.field_5183;
                        this.field_5185 = this.field_5198;
                        this.field_5174 = this.field_5182;
                        this.field_5184 = this.field_5197;
                    }
                    class_319 mode = monitor.method_1617();
                    this.field_5183 = monitor.method_1616();
                    this.field_5198 = monitor.method_1618();
                    this.field_5182 = mode.method_1668();
                    this.field_5197 = mode.method_1669();
                    GLFW.glfwSetWindowAttrib(this.field_5187, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                    GLFW.glfwSetWindowMonitor(this.field_5187, 0L, this.field_5183, this.field_5198, this.field_5182, this.field_5197, -1);
                    if (exclusive) {
                        GLFW.glfwSetWindowAttrib(this.field_5187, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                    }
                    this.dih$borderless = true;
                    ci.cancel();
                }
            } else if (this.dih$borderless) {
                this.dih$leaveBorderless();
            }
        } catch (RuntimeException e) {

            this.dih$borderless = false;
        }
    }

    @Unique
    private void dih$leaveBorderless() {
        this.dih$borderless = false;
        GLFW.glfwSetWindowAttrib(this.field_5187, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
        this.field_5183 = this.field_5175;
        this.field_5198 = this.field_5185;
        this.field_5182 = this.field_5174;
        this.field_5197 = this.field_5184;
    }

    @Override
    public boolean dih$isBorderless() {
        return this.dih$borderless;
    }

    @Override
    public void dih$reapplyMode() {
        this.field_5177 = !this.field_5191;
    }
}
