package dev.dihclient.modules.player;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.Hammer;
import net.minecraft.class_1268;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_3965;

public class PacketMine extends Module {
   public final DoubleSetting range = this.dbl("Range", "Keeps mining the marked block within this distance.", 6.0, 3.0, 8.0, 0.1);
   public final BoolSetting render = this.bool("Render", "Highlights the block being mined.", true);
   public final BoolSetting hammer = this.bool(
      "3x3 Pickaxe",
      "Takes your 3x3 pickaxe (name set in the \"3x3 Pickaxe\" module) into the hand for the marked block – you choose where, so the whole square goes.",
      false
   );
   private class_2338 target;
   private class_2350 side = class_2350.field_11036;

   public PacketMine() {
      super("PacketMine", Category.PLAYER, "Keeps breaking the block you started mining even when you look away, until it is gone.");
   }

   @Override
   public void onTick() {
      if (mc.field_1690.field_1886.method_1434()
         && mc.field_1765 instanceof class_3965 var1
         && !mc.field_1687.method_8320(var1.method_17777()).method_26215()
         && !var1.method_17777().equals(this.target)) {
         this.target = var1.method_17777().method_10062();
         this.side = var1.method_17780();
         if (this.hammer.get()) {
            Hammer.select();
         }

         mc.field_1761.method_2910(this.target, this.side);
      }

      if (this.target != null) {
         if (!mc.field_1687.method_8320(this.target).method_26215()
            && !(class_243.method_24953(this.target).method_1022(mc.field_1724.method_33571()) > this.range.get())) {
            boolean var4 = mc.field_1690.field_1886.method_1434()
               && mc.field_1765 instanceof class_3965 var5
               && var5.method_17777().equals(this.target)
               && mc.field_1755 == null;
            if (!var4) {
               mc.field_1761.method_2902(this.target, this.side);
               mc.field_1724.method_6104(class_1268.field_5808);
            }
         } else {
            this.target = null;
         }
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.target != null && this.render.get()) {
         var1.box(new class_238(this.target), -43691, 40, false);
      }
   }

   @Override
   protected void onDisable() {
      this.target = null;
      if (mc.field_1761 != null) {
         mc.field_1761.method_2925();
      }
   }
}
