package dev.dihclient.util;

import dev.dihclient.DIHClient;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.Performance;
import net.fabricmc.loader.api.FabricLoader;

public final class Compat {
   public static final boolean VULKAN = FabricLoader.getInstance().isModLoaded("vulkanmod");
   public static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");

   private Compat() {
   }

   public static boolean safeRender() {
      Performance var0 = perf();
      if (var0 == null) {
         return VULKAN;
      } else {
         return switch ((Performance.RenderMode)var0.renderMode.get()) {
            case AUTO -> VULKAN;
            case SAFE -> true;
            case FULL -> false;
         };
      }
   }

   public static boolean chunkXray() {
      return !VULKAN && !SODIUM;
   }

   public static boolean lowDetail() {
      Performance var0 = perf();
      return var0 != null && var0.lowDetail.get();
   }

   public static Performance perf() {
      if (DIHClient.modules() == null) {
         return null;
      } else {
         try {
            return ModuleManager.of(Performance.class);
         } catch (Exception var1) {
            return null;
         }
      }
   }

   public static String rendererName() {
      if (VULKAN) {
         return "VulkanMod";
      } else {
         return SODIUM ? "Sodium" : "Vanilla";
      }
   }
}
