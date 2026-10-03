package dev.dihclient.modules.render;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.RegistryUtil;
import java.util.Set;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1657;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3532;

public class Esp extends Module {
   public final IdListSetting entities = this.ids("Entities", "Entity types to highlight.", IdListSetting.Kind.ENTITY, new String[]{"minecraft:player"})
      .legacy("esp.mobs")
      .onChange(this::rebuild);
   public final EnumSetting<Esp.Mode> mode = this.mode("Mode", "Box outline, vanilla glow outline or both.", Esp.Mode.BOTH);
   public final BoolSetting throughWalls = this.bool("Through Walls", "Boxes stay visible behind terrain.", true);
   public final IntSetting fillAlpha = this.integer("Fill", "Box fill opacity (0 = outline only).", 30, 0, 255);
   public final BoolSetting tracers = this.bool("Tracers", "Draws a line from the crosshair to every highlighted entity.", false);
   public final DoubleSetting range = this.dbl("Range", "Maximum distance.", 128.0, 8.0, 512.0, 8.0);
   public final ColorSetting playerColor = this.color("Player Color", "Colour for players.", -9663233);
   public final ColorSetting friendColor = this.color("Friend Color", "Colour for friends.", -11740828);
   public final ColorSetting enemyColor = this.color("Enemy Color", "Colour for enemies.", -46261);
   public final ColorSetting mobColor = this.color("Mob Color", "Colour for other entities.", -22964);
   private Set<class_1299<?>> types = Set.of();

   public Esp() {
      super("ESP", Category.RENDER, "Highlights selected entity types with boxes, glow outlines and optional tracers.");
   }

   private void rebuild() {
      this.types = RegistryUtil.entityTypes(this.entities.get());
   }

   @Override
   protected void onEnable() {
      this.rebuild();
   }

   public boolean matches(class_1297 var1) {
      class_310 var2 = class_310.method_1551();
      if (var1 != var2.field_1724 && (var1 != var2.method_1560() || ModuleManager.on(Freecam.class))) {
         if (this.types.isEmpty() && !this.entities.get().isEmpty()) {
            this.rebuild();
         }

         return !this.types.contains(var1.method_5864())
            ? false
            : var2.field_1724 == null || var2.field_1724.method_5858(var1) <= this.range.get() * this.range.get();
      } else {
         return false;
      }
   }

   public int colorFor(class_1297 var1) {
      if (var1 instanceof class_1657 var2) {
         if (DIHClient.social().isFriend(var2)) {
            return this.friendColor.get();
         } else {
            return DIHClient.social().isEnemy(var2) ? this.enemyColor.get() : this.playerColor.get();
         }
      } else {
         return this.mobColor.get();
      }
   }

   public static Integer glowColor(class_1297 var0) {
      if (DIHClient.modules() != null && var0 != null) {
         if (ModuleManager.on(Esp.class)) {
            Esp var1 = ModuleManager.of(Esp.class);
            if (!var1.mode.is(Esp.Mode.BOX) && var1.matches(var0)) {
               return var1.colorFor(var0) & 16777215;
            }
         }

         if (ModuleManager.on(Chams.class) && var0 instanceof class_1657 var3 && var3 != class_310.method_1551().field_1724) {
            Chams var2 = ModuleManager.of(Chams.class);
            return DIHClient.social().isFriend(var3) ? var2.friendColor.get() & 16777215 : var2.color.get() & 16777215;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (!this.mode.is(Esp.Mode.GLOW) || this.tracers.get()) {
         float var2 = var1.tickDelta();

         for (class_1297 var4 : mc.field_1687.method_18112()) {
            if (this.matches(var4)) {
               int var5 = this.colorFor(var4);
               double var6 = class_3532.method_16436(var2, var4.field_6038, var4.method_23317()) - var4.method_23317();
               double var8 = class_3532.method_16436(var2, var4.field_5971, var4.method_23318()) - var4.method_23318();
               double var10 = class_3532.method_16436(var2, var4.field_5989, var4.method_23321()) - var4.method_23321();
               class_238 var12 = var4.method_5829().method_989(var6, var8, var10);
               if (!this.mode.is(Esp.Mode.GLOW)) {
                  var1.box(var12.method_1014(0.05), var5, this.fillAlpha.get(), this.throughWalls.get());
               }

               if (this.tracers.get()) {
                  var1.tracer(
                     new class_243(
                        (var12.field_1323 + var12.field_1320) / 2.0, var12.field_1322 + var4.method_17682() * 0.6, (var12.field_1321 + var12.field_1324) / 2.0
                     ),
                     var5
                  );
               }
            }
         }
      }
   }

   public static enum Mode {
      BOX,
      GLOW,
      BOTH;
   }
}
