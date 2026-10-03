package dev.dihclient.hud.elements;

import dev.dihclient.DIHClient;
import dev.dihclient.gui.theme.Anim;
import dev.dihclient.gui.theme.Theme;
import dev.dihclient.hud.HudElement;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.module.Module;
import dev.dihclient.modules.client.Hud;
import dev.dihclient.render.Gfx;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.class_332;

public class ModuleListElement extends HudElement {
   private static final int LINE_H = 11;
   private static final Comparator<ModuleListElement.Line> BY_WIDTH = Comparator.comparingInt(var0 -> -var0.width);
   private static final Comparator<ModuleListElement.Line> BY_NAME = Comparator.comparing(var0 -> var0.module.name());
   private final Map<Module, ModuleListElement.Line> lines = new IdentityHashMap<>();
   private final List<ModuleListElement.Line> shown = new ArrayList<>();
   private long measuredAt;

   public ModuleListElement() {
      super("modules", "Active Modules", "modules", -1, 4, () -> HudStyle.hud().activeModules);
   }

   private static String name(Module var0, Hud var1) {
      String var2 = var0.name();
      return var1.listLowercase.get() ? var2.toLowerCase(Locale.ROOT) : var2;
   }

   @Override
   public void render(class_332 var1, float var2, boolean var3) {
      Hud var4 = HudStyle.hud();
      boolean var5 = var4.listLowercase.get();
      boolean var6 = var4.listSuffix.get();
      long var7 = System.currentTimeMillis();
      boolean var9 = var7 - this.measuredAt > 1000L;
      if (var9) {
         this.measuredAt = var7;
      }

      List var10 = this.shown;
      var10.clear();

      for (Module var12 : DIHClient.modules().all()) {
         if (var12.isToggleable() && !var12.isHidden()) {
            float var13 = var12.isEnabled() ? 1.0F : 0.0F;
            ModuleListElement.Line var14 = this.lines.get(var12);
            if (var14 == null) {
               var14 = new ModuleListElement.Line(var12, var13);
               this.lines.put(var12, var14);
            }

            var14.anim = var14.value.update(var13, 10.0F);
            if (var14.anim > 0.02F) {
               var14.refresh(var4, var5, var6, var9);
               var10.add(var14);
            }
         }
      }

      var10.sort(var4.listSort.get() == Hud.ListSort.WIDTH ? BY_WIDTH : BY_NAME);
      if (var10.isEmpty()) {
         this.width = 80;
         this.height = 11;
         if (var3) {
            Gfx.text(var1, "Active Modules", 2, 2, -10788238);
         }
      } else {
         int var26 = 0;

         for (ModuleListElement.Line var29 : var10) {
            var26 = Math.max(var26, var29.width);
         }

         this.width = var26 + 2;
         boolean var28 = HudStyle.glass();
         int var30 = var28 ? HudStyle.bgColor() : 0;
         float var31 = 0.0F;
         int var15 = 0;

         for (ModuleListElement.Line var17 : var10) {
            float var18 = var17.anim;
            float var19 = Anim.easeOutCubic(var18);
            int var20 = var17.width;
            int var21 = (int)((1.0F - var19) * (var20 + 4));
            int var22 = this.rightSide ? this.width - var20 + var21 : -var21;
            int var23 = (int)var31;
            int var24 = Math.max(1, (int)(11.0F * Math.min(1.0F, var18 * 1.3F)));
            int var25 = Theme.accentAt(var15 * 0.06);
            if (var28) {
               var1.method_25294(var22, var23, var22 + var20, var23 + var24, Theme.withAlpha(var30, var18));
               if (this.rightSide) {
                  var1.method_25294(var22 + var20, var23, var22 + var20 + 2, var23 + var24, Theme.withAlpha(var25, var18));
               } else {
                  var1.method_25294(var22 - 2, var23, var22, var23 + var24, Theme.withAlpha(var25, var18));
               }
            }

            if (var24 >= 9) {
               Gfx.text(var1, var17.name, var22 + 4, var23 + 2, Theme.withAlpha(var25, var18));
               if (var17.suffix != null) {
                  Gfx.text(var1, var17.suffix, var22 + 4 + var17.nameWidth, var23 + 2, Theme.withAlpha(-7564380, var18));
               }
            }

            var31 += 11.0F * var18;
            var15++;
         }

         this.height = Math.max(11, (int)var31);
      }
   }

   private static final class Line {
      final Module module;
      final Anim.Value value;
      float anim;
      String name;
      String suffix;
      int nameWidth;
      int width;
      private String rawInfo;
      private int flags = -1;

      Line(Module var1, float var2) {
         this.module = var1;
         this.value = new Anim.Value(var2);
      }

      void refresh(Hud var1, boolean var2, boolean var3, boolean var4) {
         String var5 = var3 ? this.module.getInfo() : null;
         if (var5 != null && var5.isEmpty()) {
            var5 = null;
         }

         int var6 = (var2 ? 1 : 0) | (var3 ? 2 : 0);
         if (var4 || var6 != this.flags || !Objects.equals(var5, this.rawInfo) || this.name == null) {
            this.flags = var6;
            this.rawInfo = var5;
            this.name = ModuleListElement.name(this.module, var1);
            this.suffix = var5 == null ? null : " " + (var2 ? var5.toLowerCase(Locale.ROOT) : var5);
            this.nameWidth = Gfx.width(this.name);
            this.width = this.nameWidth + (this.suffix == null ? 0 : Gfx.width(this.suffix)) + 8;
         }
      }
   }
}
