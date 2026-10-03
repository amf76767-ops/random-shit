package dev.dihclient.mixin.port;

import net.minecraft.class_1735;
import net.minecraft.class_465;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The slot under the mouse in a container screen (for Hover Totem). */
@Mixin(class_465.class)
public interface HoveredSlotMixin {
    @Accessor("field_2787")
    class_1735 dih$hoveredSlot();
}
