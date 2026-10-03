package dev.dihclient.mixin.accessor;

import net.minecraft.class_310;
import net.minecraft.class_4757;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({class_310.class})
public interface ProfilerAccessor {
   @Accessor("field_22225")
   class_4757 dih$tracker();
}
