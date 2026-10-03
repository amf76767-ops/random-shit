package dev.dihclient.mixin.accessor;

import net.minecraft.class_1747;
import net.minecraft.class_1750;
import net.minecraft.class_2680;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({class_1747.class})
public interface BlockItemInvoker {
   @Invoker("method_7707")
   class_2680 dih$getPlacementState(class_1750 var1);

   @Invoker("method_7709")
   boolean dih$canPlace(class_1750 var1, class_2680 var2);
}
