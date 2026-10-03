package dev.dihclient.mixin.port;

import net.minecraft.class_241;
import net.minecraft.class_744;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the movement correction of the silent rotation rewrite the move vector. */
@Mixin(class_744.class)
public interface CrystalInputAccessorMixin {
    @Accessor("field_55868")
    void dih$crystalSetMoveVector(class_241 vector);
}
