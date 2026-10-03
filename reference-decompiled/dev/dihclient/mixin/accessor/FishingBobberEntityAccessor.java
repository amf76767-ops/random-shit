package dev.dihclient.mixin.accessor;

import net.minecraft.class_1536;
import net.minecraft.class_2940;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({class_1536.class})
public interface FishingBobberEntityAccessor {
   @Accessor("field_23234")
   static class_2940<Boolean> dih$caughtFish() {
      throw new AssertionError();
   }
}
