package dev.dihclient.modules.player;

import dev.dihclient.mixin.accessor.MinecraftClientAccessor;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.IntSetting;

public class FastPlace extends Module {
   public final IntSetting delay = this.integer("Delay", "Item use cooldown in ticks (vanilla 4).", 0, 0, 4).legacy("fastPlace.delay");

   public FastPlace() {
      super("FastPlace", Category.PLAYER, "Reduces Minecraft's item-use cooldown to the configured number of ticks.");
   }

   @Override
   public void onTick() {
      MinecraftClientAccessor var1 = (MinecraftClientAccessor)mc;
      if (var1.dih$getItemUseCooldown() > this.delay.get()) {
         var1.dih$setItemUseCooldown(this.delay.get());
      }
   }
}
