package dev.dihclient.mixin.accessor;

import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import net.minecraft.class_10799;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({class_10799.class})
public interface RenderPipelinesAccessor {
   @Accessor("field_56859")
   static Snippet dih$linesSnippet() {
      throw new AssertionError();
   }

   @Accessor("field_56860")
   static Snippet dih$positionColorSnippet() {
      throw new AssertionError();
   }
}
