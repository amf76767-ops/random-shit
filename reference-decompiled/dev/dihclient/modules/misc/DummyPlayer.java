package dev.dihclient.modules.misc;

import com.mojang.authlib.GameProfile;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import java.util.UUID;
import net.minecraft.class_745;
import net.minecraft.class_1297.class_5529;

public class DummyPlayer extends Module {
   private static final int ENTITY_ID = -4200042;
   private class_745 dummy;

   public DummyPlayer() {
      super("DummyPlayer", Category.MISC, "Spawns a local client-side copy of you for combat/render testing.");
   }

   @Override
   protected void onEnable() {
      if (!inGame()) {
         this.setEnabledSilently(false);
      } else {
         this.dummy = new class_745(mc.field_1687, new GameProfile(UUID.randomUUID(), mc.field_1724.method_7334().name()));
         this.dummy.method_5838(-4200042);
         this.dummy
            .method_5808(
               mc.field_1724.method_23317(),
               mc.field_1724.method_23318(),
               mc.field_1724.method_23321(),
               mc.field_1724.method_36454(),
               mc.field_1724.method_36455()
            );
         this.dummy.method_5847(mc.field_1724.method_5791());
         this.dummy.method_5636(mc.field_1724.method_73188());
         this.dummy.method_31548().method_7377(mc.field_1724.method_31548());
         mc.field_1687.method_53875(this.dummy);
      }
   }

   @Override
   protected void onDisable() {
      if (this.dummy != null && mc.field_1687 != null) {
         mc.field_1687.method_2945(this.dummy.method_5628(), class_5529.field_26999);
      }

      this.dummy = null;
   }

   @Override
   public void onWorldChange() {
      this.dummy = null;
      if (this.isEnabled()) {
         this.setEnabledSilently(false);
      }
   }
}
