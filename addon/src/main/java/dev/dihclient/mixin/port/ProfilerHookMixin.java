package dev.dihclient.mixin.port;

import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.port.profile.FrameProfiler;
import dev.dihclient.render.Render3D;
import net.minecraft.class_332;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times the tick and the drawing of every module for the Frame Profiler. While the profiler is off, each hook is one boolean check. */
@Mixin(ModuleManager.class)
public abstract class ProfilerHookMixin {
    @Inject(method = "safe", at = @At("HEAD"), require = 0)
    private static void dih$tickStart(Module module, String what, Runnable action, CallbackInfo ci) {
        if (FrameProfiler.active() && "tick".equals(what)) {
            FrameProfiler.begin(module.name(), FrameProfiler.TICK);
        }
    }

    @Inject(method = "safe", at = @At("RETURN"), require = 0)
    private static void dih$tickEnd(Module module, String what, Runnable action, CallbackInfo ci) {
        if (FrameProfiler.active() && "tick".equals(what)) {
            FrameProfiler.end();
        }
    }

    @Redirect(method = "render2D", at = @At(value = "INVOKE", target = "Ldev/dihclient/module/Module;onRender2D(Lnet/minecraft/class_332;F)V"), require = 0)
    private void dih$draw2d(Module module, class_332 graphics, float delta) {
        if (FrameProfiler.active()) {
            FrameProfiler.begin(module.name(), FrameProfiler.R2D);
            try {
                module.onRender2D(graphics, delta);
            } finally {
                FrameProfiler.end();
            }
        } else {
            module.onRender2D(graphics, delta);
        }
    }

    @Redirect(method = "render3D", at = @At(value = "INVOKE", target = "Ldev/dihclient/module/Module;onRender3D(Ldev/dihclient/render/Render3D;)V"), require = 0)
    private void dih$draw3d(Module module, Render3D render) {
        if (FrameProfiler.active()) {
            FrameProfiler.begin(module.name(), FrameProfiler.R3D);
            try {
                module.onRender3D(render);
            } finally {
                FrameProfiler.end();
            }
        } else {
            module.onRender3D(render);
        }
    }
}
