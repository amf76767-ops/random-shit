package dev.dihclient.mixin.accessor;

import net.minecraft.class_12247;
import net.minecraft.class_1921;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({class_1921.class})
public interface RenderLayerInvoker {
   @Invoker("method_75940")
   static class_1921 dih$of(String var0, class_12247 var1) {
      throw new AssertionError();
   }
}
