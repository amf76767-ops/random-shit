package dev.dihclient.modules.combat;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.InvUtil;
import java.util.function.Predicate;
import net.minecraft.class_1657;
import net.minecraft.class_1743;
import net.minecraft.class_1799;
import net.minecraft.class_3966;

public class ShieldBreaker extends Module {
   public final DoubleSetting range = this.dbl("Range", "Reacts to blocking players within this distance.", 5.0, 1.0, 8.0, 0.1).legacy("shieldBreaker.range");
   private int previous = -1;

   public ShieldBreaker() {
      super("Shield Breaker", Category.COMBAT, "Selects an axe while the targeted player is blocking, then returns to your previous slot.");
   }

   public void selectAxe() {
      int var1 = InvUtil.findHotbar((Predicate<class_1799>)(var0 -> var0.method_7909() instanceof class_1743));
      if (var1 >= 0 && var1 != InvUtil.selectedSlot()) {
         if (this.previous < 0) {
            this.previous = InvUtil.selectedSlot();
         }

         InvUtil.select(var1);
      }
   }

   @Override
   protected void onDisable() {
      this.previous = -1;
   }

   @Override
   public void onTick() {
      if (mc.field_1765 instanceof class_3966 var1
         && var1.method_17782() instanceof class_1657 var2
         && var2.method_6039()
         && mc.field_1724.method_5739(var2) <= this.range.get()) {
         this.selectAxe();
      } else if (this.previous >= 0) {
         InvUtil.select(this.previous);
         this.previous = -1;
      }
   }
}
