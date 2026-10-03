package dev.dihclient.mixin.accessor;

import net.minecraft.class_7172;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({class_7172.class})
public interface SimpleOptionAccessor<T> {
   @Accessor("field_37868")
   void dih$setValueRaw(T var1);
}
