package dev.dihclient.mixin.port;

import net.minecraft.class_1917;
import net.minecraft.class_1952;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_1917.class)
public interface DonutDSpawnerAccessorMixin {
    @Accessor("field_9155")
    class_1952 dih$donutdNextSpawnData();

    @Accessor("field_9158")
    int dih$donutdRequiredPlayerRange();
}
