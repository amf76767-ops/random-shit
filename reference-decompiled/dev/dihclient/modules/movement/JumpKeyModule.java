package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.modules.render.Freecam;

public abstract class JumpKeyModule extends Module {
   private boolean lastDown;
   private boolean wasOnGround;
   protected boolean fromGround;

   protected JumpKeyModule(String var1, String var2) {
      super(var1, Category.MOVEMENT, var2);
   }

   protected boolean jumpPressedNow() {
      boolean var1 = mc.field_1690.field_1903.method_1434() && Freecam.active() == null;
      boolean var2 = var1 && !this.lastDown;
      this.lastDown = var1;
      this.fromGround = this.wasOnGround;
      this.wasOnGround = mc.field_1724.method_24828();
      return var2;
   }
}
