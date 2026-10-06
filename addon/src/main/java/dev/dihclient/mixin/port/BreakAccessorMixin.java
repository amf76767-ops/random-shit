package dev.dihclient.mixin.port;

import net.minecraft.class_636;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_636.class)
public interface BreakAccessorMixin {
    @Accessor("field_3715")
    float dih$progress();

    @Accessor("field_3715")
    void dih$setProgress(float value);

    @Accessor("field_3716")
    int dih$cooldown();

    @Accessor("field_3716")
    void dih$setCooldown(int value);

    @Accessor("field_3717")
    boolean dih$breaking();
}
