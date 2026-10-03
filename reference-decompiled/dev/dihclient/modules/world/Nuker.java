package dev.dihclient.modules.world;

import dev.dihclient.autobuild.Worker;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Hammer;
import dev.dihclient.util.RegistryUtil;
import dev.dihclient.util.RotationUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.class_1268;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;

public class Nuker extends Module {
   public final EnumSetting<Nuker.Mode> mode = this.mode(
      "Mode", "All: everything · Flatten: only blocks at feet level and above · Only List / Skip List: filter by the block list.", Nuker.Mode.FLATTEN
   );
   public final IdListSetting blocks = this.ids(
         "Blocks", "Block list for the list modes.", IdListSetting.Kind.BLOCK, new String[]{"minecraft:stone", "minecraft:dirt", "minecraft:grass_block"}
      )
      .visibleWhen(() -> this.mode.get() == Nuker.Mode.ONLY_LIST || this.mode.get() == Nuker.Mode.SKIP_LIST)
      .onChange(this::rebuild);
   public final DoubleSetting range = this.dbl("Range", "Break radius.", 4.5, 1.0, 6.0, 0.1);
   public final IntSetting creativePerTick = this.integer("Creative Per Tick", "Blocks per tick in creative.", 4, 1, 40);
   public final BoolSetting instantOnly = this.bool("Instant Only", "Survival: only breaks blocks that break in one hit (grass, flowers, crops …).", false);
   public final BoolSetting rotate = this.bool("Look At Block", "Turns your view to the block being mined.", false);
   public final BoolSetting hammer = this.bool(
      "3x3 Pickaxe",
      "Survival: uses your 3x3 pickaxe (name set in the \"3x3 Pickaxe\" module) and aims at square centres, so one swing breaks up to 9 blocks – only where every block of the square is one Nuker would break anyway.",
      false
   );
   public final BoolSetting render = this.bool("Render", "Outlines the block being mined.", true);
   private Set<class_2248> list = Set.of();
   private class_2338 current;
   private boolean useHammer;

   public Nuker() {
      super("Nuker", Category.WORLD, "Breaks blocks around you automatically – flatten terrain, clear areas, mine specific blocks.");
   }

   private void rebuild() {
      this.list = RegistryUtil.blocks(this.blocks.get());
   }

   @Override
   protected void onEnable() {
      this.rebuild();
      this.current = null;
   }

   @Override
   protected void onDisable() {
      if (mc.field_1761 != null) {
         mc.field_1761.method_2925();
      }

      this.current = null;
   }

   private boolean valid(class_2338 var1) {
      class_2680 var2 = mc.field_1687.method_8320(var1);
      if (!var2.method_26215() && (var2.method_26227().method_15769() || var2.method_26204() != var2.method_26227().method_15759().method_26204())) {
         float var3 = var2.method_26214(mc.field_1687, var1);
         if (var3 < 0.0F) {
            return false;
         } else if (distSq(var1) > this.range.get() * this.range.get()) {
            return false;
         } else {
            switch ((Nuker.Mode)this.mode.get()) {
               case FLATTEN:
                  if (var1.method_10264() < Math.floor(mc.field_1724.method_23318())) {
                     return false;
                  }
                  break;
               case ONLY_LIST:
                  if (!this.list.contains(var2.method_26204())) {
                     return false;
                  }
                  break;
               case SKIP_LIST:
                  if (this.list.contains(var2.method_26204())) {
                     return false;
                  }
            }

            return mc.field_1724.method_68878() || !this.instantOnly.get() || !(var2.method_26165(mc.field_1724, mc.field_1687, var1) < 1.0F);
         }
      } else {
         return false;
      }
   }

   private static double distSq(class_2338 var0) {
      double var1 = var0.method_10263() + 0.5 - mc.field_1724.method_23317();
      double var3 = var0.method_10264() + 0.5 - mc.field_1724.method_23320();
      double var5 = var0.method_10260() + 0.5 - mc.field_1724.method_23321();
      return var1 * var1 + var3 * var3 + var5 * var5;
   }

   private List<class_2338> targets() {
      ArrayList var1 = new ArrayList();
      int var2 = (int)Math.ceil(this.range.get());
      class_2338 var3 = class_2338.method_49638(mc.field_1724.method_33571());
      double var4 = this.range.get() * this.range.get();

      for (class_2338 var7 : class_2338.method_10097(var3.method_10069(-var2, -var2, -var2), var3.method_10069(var2, var2, var2))) {
         if (!(distSq(var7) > var4) && this.valid(var7)) {
            var1.add(var7.method_10062());
         }
      }

      int var14 = var1.size();
      double[] var15 = new double[var14];
      Integer[] var8 = new Integer[var14];

      for (int var9 = 0; var9 < var14; var9++) {
         var15[var9] = distSq((class_2338)var1.get(var9));
         var8[var9] = var9;
      }

      Arrays.sort(var8, (var1x, var2x) -> Double.compare(var15[var1x], var15[var2x]));
      ArrayList var16 = new ArrayList(var14);

      for (Integer var13 : var8) {
         var16.add((class_2338)var1.get(var13));
      }

      return var16;
   }

   private class_2350 face(class_2338 var1) {
      class_243 var2 = mc.field_1724.method_33571().method_1020(class_243.method_24953(var1));
      return class_2350.method_10142(var2.field_1352, var2.field_1351, var2.field_1350);
   }

   @Override
   public void onTick() {
      if (mc.field_1755 == null) {
         if (mc.field_1724.method_68878()) {
            int var1 = 0;

            for (class_2338 var3 : this.targets()) {
               mc.field_1761.method_2910(var3, this.face(var3));
               if (++var1 >= this.creativePerTick.get()) {
                  break;
               }
            }

            if (var1 > 0) {
               mc.field_1724.method_6104(class_1268.field_5808);
            }
         } else {
            if (this.current == null || !this.valid(this.current)) {
               if (this.current != null) {
                  mc.field_1761.method_2925();
               }

               List var4 = this.targets();
               this.current = var4.isEmpty() ? null : (class_2338)var4.get(0);
               if (this.current == null) {
                  return;
               }

               this.useHammer = false;
               if (this.hammer.get() && Hammer.available()) {
                  class_2338 var6 = Hammer.bestCenter(this.current, this.range.get(), this::valid, this::valid);
                  if (var6 != null) {
                     this.current = var6;
                     this.useHammer = true;
                  }
               }
            }

            if (this.useHammer && !Hammer.safe(this.current, Hammer.face(this.current, mc.field_1724.method_33571()), this::valid)) {
               this.useHammer = false;
            }

            if (this.hammer.get() && (!this.useHammer || !Hammer.select())) {
               Worker.selectBestTool(mc.field_1687.method_8320(this.current), true);
            }

            if (this.rotate.get()) {
               float[] var5 = RotationUtil.rotationsTo(class_243.method_24953(this.current));
               mc.field_1724.method_36456(var5[0]);
               mc.field_1724.method_36457(var5[1]);
            }

            mc.field_1761.method_2902(this.current, this.face(this.current));
            mc.field_1724.method_6104(class_1268.field_5808);
         }
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.render.get() && this.current != null) {
         var1.box(new class_238(this.current), -49088, 40, false);
      }
   }

   @Override
   public String getInfo() {
      return this.mode.displayValue();
   }

   public static enum Mode {
      ALL,
      FLATTEN,
      ONLY_LIST,
      SKIP_LIST;
   }
}
