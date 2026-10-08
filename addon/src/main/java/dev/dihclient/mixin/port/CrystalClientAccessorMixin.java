package dev.dihclient.mixin.port;

import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(class_310.class)
public interface CrystalClientAccessorMixin {
    @Accessor("field_1771")
    int dih$crystalAttackCooldown();

    @Accessor("field_1771")
    void dih$setAttackCooldown(int value);

    @Invoker("method_1536")
    boolean dih$doAttack();
}
