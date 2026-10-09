package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.util.MoveUtil;
import net.minecraft.class_1322;
import net.minecraft.class_1324;
import net.minecraft.class_2960;
import net.minecraft.class_5134;

public class Momentum extends Module {
   private static final class_2960 MODIFIER = class_2960.method_60655("dihclient", "momentum");

   public final EnumSetting<Momentum.Mode> mode = this.mode(
         "Mode", "Normal: your normal walking speed times Multiplier · Strafe: constant speed · BHop: jumps automatically.", Momentum.Mode.STRAFE
      )
      .legacy("momentum.mode");
   public final DoubleSetting multiplier = this.dbl("Multiplier", "How much faster than normal you walk and sprint.", 1.3, 1.0, 5.0, 0.05)
      .visibleWhen(() -> this.mode.get() == Momentum.Mode.NORMAL);
   public final DoubleSetting speed = this.dbl("Speed", "Horizontal speed.", 0.32, 0.05, 2.0, 0.01)
      .legacy("momentum.speed")
      .visibleWhen(() -> this.mode.get() != Momentum.Mode.NORMAL);
   public final DoubleSetting airMultiplier = this.dbl("Air Multiplier", "BHop speed multiplier while airborne.", 0.92, 0.1, 2.0, 0.01)
      .legacy("momentum.airMultiplier")
      .visibleWhen(() -> this.mode.get() == Momentum.Mode.BHOP);
   public final BoolSetting autoJump = this.bool("Auto Jump", "Jumps automatically in BHop mode.", true)
      .legacy("momentum.autoJump")
      .visibleWhen(() -> this.mode.get() == Momentum.Mode.BHOP);

   public Momentum() {
      super("Momentum", Category.MOVEMENT, "Movement speed control: Normal multiplies your normal speed, Strafe keeps a constant speed, BHop jumps automatically.");
   }

   private static class_1324 attribute() {
      return mc.field_1724 == null ? null : mc.field_1724.method_5996(class_5134.field_23719);
   }

   private static void removeModifier() {
      class_1324 a = attribute();
      if (a != null && a.method_6196(MODIFIER)) {
         a.method_6200(MODIFIER);
      }
   }

   private void applyModifier() {
      class_1324 a = attribute();
      if (a == null) {
         return;
      }
      double value = this.multiplier.get() - 1.0;
      class_1322 have = a.method_6199(MODIFIER);
      if (have != null && Math.abs(have.comp_2449() - value) < 1.0E-6) {
         return;
      }
      if (have != null) {
         a.method_6200(MODIFIER);
      }
      a.method_26837(new class_1322(MODIFIER, value, class_1322.class_1323.field_6331));
   }

   @Override
   protected void onDisable() {
      removeModifier();
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null) {
         return;
      }
      if (this.mode.get() == Momentum.Mode.NORMAL) {
         if (ModuleManager.on(Flight.class) || mc.field_1724.method_6128()) {
            removeModifier();
         } else {
            this.applyModifier();
         }
         return;
      }
      removeModifier();
      if (!ModuleManager.on(Flight.class) && MoveUtil.isMoving() && !mc.field_1724.method_6128()) {
         boolean bhop = this.mode.get() == Momentum.Mode.BHOP;
         if (bhop && mc.field_1724.method_24828() && this.autoJump.get()) {
            mc.field_1724.method_6043();
         }

         double factor = bhop && !mc.field_1724.method_24828() ? this.airMultiplier.get() : 1.0;
         double[] dir = MoveUtil.direction(this.speed.get() * factor);
         mc.field_1724.method_18800(dir[0], mc.field_1724.method_18798().field_1351, dir[1]);
      }
   }

   @Override
   public String getInfo() {
      return this.mode.get() == Momentum.Mode.NORMAL ? String.format("x%.2f", this.multiplier.get()) : this.mode.displayValue();
   }

   public static enum Mode {
      NORMAL,
      STRAFE,
      BHOP;
   }
}
