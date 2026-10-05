package dev.fakedonut.mixin;

import dev.fakedonut.AntiXray;
import java.util.List;
import net.minecraft.class_2540;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_6603")
public abstract class ChunkDataMixin {
    @Shadow
    private List<Object> field_34865;

    @Inject(method = "method_38589", at = @At("HEAD"), cancellable = true)
    private static void fd$size(class_2818 chunk, CallbackInfoReturnable<Integer> cir) {
        int total = 0;
        for (class_2826 s : AntiXray.prepare(chunk)) {
            total += s.method_12260();
        }
        cir.setReturnValue(total);
    }

    @Inject(method = "method_38591", at = @At("HEAD"), cancellable = true)
    private static void fd$write(class_2540 buf, class_2818 chunk, CallbackInfo ci) {
        for (class_2826 s : AntiXray.sectionsFor(chunk)) {
            s.method_12257(buf);
        }
        ci.cancel();
    }

    @Inject(method = "<init>(Lnet/minecraft/class_2818;)V", at = @At("RETURN"))
    private void fd$entities(class_2818 chunk, CallbackInfo ci) {
        this.field_34865.removeIf(e -> {
            try {
                Class<?> c = e.getClass();
                java.lang.reflect.Field fx = c.getDeclaredField("field_34866");
                java.lang.reflect.Field fy = c.getDeclaredField("field_34867");
                fx.setAccessible(true);
                fy.setAccessible(true);
                return AntiXray.hiddenEntity(chunk, fx.getInt(e), fy.getInt(e));
            } catch (ReflectiveOperationException ex) {
                return false;
            }
        });
    }
}
