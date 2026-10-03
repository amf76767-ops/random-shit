package dev.dihclient.mixin.accessor;

import net.minecraft.class_757;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({class_757.class})
public interface GameRendererAccessor {
   @Accessor("field_55871")
   void dih$setNauseaEffectTime(float var1);

   @Accessor("field_55872")
   void dih$setNauseaEffectSpeed(float var1);
}
