package dev.dihclient.modules.movement;

import dev.dihclient.mixin.accessor.PlayerMoveC2SPacketAccessor;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.DoubleSetting;
import net.minecraft.class_2596;
import net.minecraft.class_2828;

public class NoFall extends Module {
   public final DoubleSetting minVelocity = this.dbl("Min Velocity", "Only spoofs while falling faster than this (blocks/tick).", 0.35, 0.0, 3.0, 0.01)
      .legacy("noFall.minVelocity");

   public NoFall() {
      super("NoFall", Category.MOVEMENT, "Spoofs the on-ground flag of movement packets while you are falling.");
   }

   @Override
   public boolean onPacketSend(class_2596<?> var1) {
      if (var1 instanceof class_2828 var2
         && mc.field_1724 != null
         && !mc.field_1724.method_6128()
         && !mc.field_1724.method_31549().field_7479
         && mc.field_1724.method_18798().field_1351 < -this.minVelocity.get()) {
         ((PlayerMoveC2SPacketAccessor)var2).dih$setOnGround(true);
      }

      return false;
   }
}
