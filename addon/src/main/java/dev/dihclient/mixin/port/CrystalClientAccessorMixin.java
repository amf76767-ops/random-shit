package dev.dihclient.mixin.port;

import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The left-click cooldown of the client, which the plain-attack path of Crystal Aura respects. */
@Mixin(class_310.class)
public interface CrystalClientAccessorMixin {
    @Accessor("field_1771")
    int dih$crystalAttackCooldown();
}
