package dev.dihclient.modules.render;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1923;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_2586;
import net.minecraft.class_2595;
import net.minecraft.class_2601;
import net.minecraft.class_2609;
import net.minecraft.class_2611;
import net.minecraft.class_2614;
import net.minecraft.class_2627;
import net.minecraft.class_2646;
import net.minecraft.class_2806;
import net.minecraft.class_2818;
import net.minecraft.class_3719;

public class StorageEsp extends Module {
   public final BoolSetting chests = this.bool("Chests", "Chests and trapped chests.", true);
   public final BoolSetting barrels = this.bool("Barrels", "Barrels.", true);
   public final BoolSetting shulkers = this.bool("Shulkers", "Shulker boxes.", true);
   public final BoolSetting enderChests = this.bool("Ender Chests", "Ender chests.", true);
   public final BoolSetting furnaces = this.bool("Furnaces", "Furnaces, smokers, blast furnaces.", false);
   public final BoolSetting hoppers = this.bool("Hoppers", "Hoppers, droppers, dispensers.", false);
   public final IntSetting fillAlpha = this.integer("Fill", "Fill opacity.", 35, 0, 255);
   public final BoolSetting throughWalls = this.bool("Through Walls", "Visible behind terrain.", true);
   public final BoolSetting tracers = this.bool("Tracers", "Lines to storage blocks.", false);
   private final List<StorageEsp.Mark> marks = new ArrayList<>();
   private int timer;

   public StorageEsp() {
      super("Storage ESP", Category.RENDER, "Highlights storage and utility containers in loaded chunks.");
   }

   private Integer colorOf(class_2586 var1) {
      if (var1 instanceof class_2646) {
         return this.chests.get() ? -38339 : null;
      } else if (var1 instanceof class_2595) {
         return this.chests.get() ? -20163 : null;
      } else if (var1 instanceof class_3719) {
         return this.barrels.get() ? -4884165 : null;
      } else if (var1 instanceof class_2627) {
         return this.shulkers.get() ? -2724865 : null;
      } else if (var1 instanceof class_2611) {
         return this.enderChests.get() ? -8765953 : null;
      } else if (var1 instanceof class_2609) {
         return this.furnaces.get() ? -6645094 : null;
      } else if (!(var1 instanceof class_2614) && !(var1 instanceof class_2601)) {
         return null;
      } else {
         return this.hoppers.get() ? -10851462 : null;
      }
   }

   @Override
   public void onTick() {
      if (this.timer-- <= 0) {
         this.timer = 10;
         this.marks.clear();
         int var1 = mc.field_1690.method_38521() + 1;
         class_1923 var2 = mc.field_1724.method_31476();

         for (int var3 = -var1; var3 <= var1; var3++) {
            for (int var4 = -var1; var4 <= var1; var4++) {
               class_2818 var5 = mc.field_1687.method_2935().method_2857(var2.field_9181 + var3, var2.field_9180 + var4, class_2806.field_12803, false);
               if (var5 != null) {
                  for (class_2586 var7 : var5.method_12214().values()) {
                     Integer var8 = this.colorOf(var7);
                     if (var8 != null) {
                        this.marks.add(new StorageEsp.Mark(var7.method_11016(), var8));
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      for (StorageEsp.Mark var3 : this.marks) {
         var1.box(new class_238(var3.pos()).method_1011(0.06), var3.color(), this.fillAlpha.get(), this.throughWalls.get());
         if (this.tracers.get()) {
            var1.tracer(var3.pos().method_46558(), var3.color());
         }
      }
   }

   @Override
   public String getInfo() {
      return Integer.toString(this.marks.size());
   }

   public int count() {
      return this.marks.size();
   }

   private record Mark(class_2338 pos, int color) {
   }
}
