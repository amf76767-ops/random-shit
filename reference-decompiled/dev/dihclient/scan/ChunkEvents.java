package dev.dihclient.scan;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Module;
import net.minecraft.class_2338;
import net.minecraft.class_2680;

public final class ChunkEvents {
   private ChunkEvents() {
   }

   private static void dispatch(ChunkEvents.Call var0) {
      if (DIHClient.modules() != null) {
         for (Module var2 : DIHClient.modules().all()) {
            if (var2.isEnabled() && var2 instanceof ChunkEvents.Listener var3) {
               try {
                  var0.run(var3);
               } catch (Throwable var5) {
                  DIHClient.LOG.error("[DIHClient] chunk listener {} failed", var2.name(), var5);
               }
            }
         }
      }
   }

   public static void onChunkLoaded(int var0, int var1) {
      dispatch(var2 -> var2.onChunkLoaded(var0, var1));
   }

   public static void onChunkUnloaded(int var0, int var1) {
      dispatch(var2 -> var2.onChunkUnloaded(var0, var1));
   }

   public static void onBlockUpdate(class_2338 var0, class_2680 var1) {
      dispatch(var2 -> var2.onBlockUpdate(var0, var1));
   }

   private interface Call {
      void run(ChunkEvents.Listener var1);
   }

   public interface Listener {
      void onChunkLoaded(int var1, int var2);

      default void onBlockUpdate(class_2338 var1, class_2680 var2) {
      }

      default void onChunkUnloaded(int var1, int var2) {
      }
   }
}
