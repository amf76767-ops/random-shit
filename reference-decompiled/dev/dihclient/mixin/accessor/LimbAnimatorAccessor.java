package dev.dihclient.mixin.accessor;

import net.minecraft.class_8080;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({class_8080.class})
public interface LimbAnimatorAccessor {
   @Accessor("field_42109")
   float dih$getLastSpeed();

   @Accessor("field_42109")
   void dih$setLastSpeed(float var1);

   @Accessor("field_42110")
   float dih$getSpeed();

   @Accessor("field_42110")
   void dih$setSpeed(float var1);

   @Accessor("field_42111")
   float dih$getProgress();

   @Accessor("field_42111")
   void dih$setProgress(float var1);
}
