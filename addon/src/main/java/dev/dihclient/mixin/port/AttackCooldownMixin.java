package dev.dihclient.mixin.port;

import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_310.class)
public interface AttackCooldownMixin {
    @Accessor("field_1771")
    int dih$attackCooldown();
}
