package dev.dihclient.modules.client;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.util.Compat;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.List;

public class Performance extends Module {
   public final EnumSetting<Performance.RenderMode> renderMode = this.mode(
         "Render Mode",
         "Auto: safe mode when VulkanMod is installed. Safe: only vanilla render layers + particle markers + chunk radar (works with every renderer, nothing through walls). Full: all effects.",
         Performance.RenderMode.AUTO
      )
      .onChange(this::reloadWorld);
   public final DoubleSetting scanBudget = this.dbl(
      "Scan Budget", "Milliseconds per tick all finders together may spend scanning chunks. Lower = smoother, finds a bit slower.", 2.0, 0.5, 10.0, 0.5
   );
   public final BoolSetting lowDetail = this.bool(
      "Low Detail", "Cheaper effects: fewer sky stars, lower cape resolution, slower animations, fewer labels.", false
   );
   public final BoolSetting markerParticles = this.bool(
      "Marker Particles", "Chunk finders also show particle beams (always visible, works with VulkanMod). Auto-on in safe mode.", false
   );
   private boolean announced;

   public Performance() {
      super("Performance", Category.CLIENT, "Lag settings and VulkanMod compatibility mode (render mode, scan budget, low detail).");
      this.setShowToggleNotification(false);
   }

   private void reloadWorld() {
      if (mc.field_1769 != null && mc.field_1687 != null) {
         mc.field_1769.method_3279();
      }
   }

   @Override
   public boolean isToggleable() {
      return false;
   }

   @Override
   public void onWorldChange() {
      if (mc.field_1724 != null || mc.method_1562() != null) {
         this.announceOnce();
      }
   }

   public void announceOnce() {
      if (!this.announced && Compat.VULKAN) {
         this.announced = true;
         Notifications.info("DIHClient", "VulkanMod detected – Vulkan mode on: safe rendering, particle markers, Xray as outlines. Tip: HUD → Chunk Radar.");
      }
   }

   public boolean particles() {
      return this.markerParticles.get() || Compat.safeRender();
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Renderer: " + Compat.rendererName() + " · Safe render: " + (Compat.safeRender() ? "on" : "off"));
      var1.add("Xray: " + (Compat.chunkXray() ? "real (chunk)" : "outline mode"));
      return var1;
   }

   public static enum RenderMode {
      AUTO,
      SAFE,
      FULL;
   }
}
