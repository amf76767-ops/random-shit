package dev.dihclient.gui.theme;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class Anim {
   private static final Map<Object, Anim.Value> VALUES = new WeakHashMap<>();
   private static boolean enabled = true;
   private static final Map<Anim.Key, Anim.Value> CHANNELS = new HashMap<>();

   private Anim() {
   }

   public static void setEnabled(boolean var0) {
      enabled = var0;
   }

   public static float get(Object var0, float var1, float var2) {
      Anim.Value var3 = VALUES.computeIfAbsent(var0, var1x -> new Anim.Value(var1));
      return var3.update(var1, var2);
   }

   public static float get(Object var0, String var1, float var2, float var3) {
      return get(new Anim.Key(var0, var1), var2, var3);
   }

   private static float get(Anim.Key var0, float var1, float var2) {
      Anim.Value var3 = CHANNELS.computeIfAbsent(var0, var1x -> new Anim.Value(var1));
      if (CHANNELS.size() > 4096) {
         CHANNELS.clear();
      }

      return var3.update(var1, var2);
   }

   public static float easeOutCubic(float var0) {
      var0 = Math.max(0.0F, Math.min(1.0F, var0));
      float var1 = 1.0F - var0;
      return 1.0F - var1 * var1 * var1;
   }

   public static float easeOutBack(float var0) {
      var0 = Math.max(0.0F, Math.min(1.0F, var0));
      float var1 = 1.70158F;
      float var2 = var1 + 1.0F;
      return 1.0F + var2 * (float)Math.pow(var0 - 1.0F, 3.0) + var1 * (float)Math.pow(var0 - 1.0F, 2.0);
   }

   private record Key(Object owner, String channel) {
   }

   public static final class Value {
      private float value;
      private long last = System.nanoTime();

      public Value(float var1) {
         this.value = var1;
      }

      public float update(float var1, float var2) {
         long var3 = System.nanoTime();
         float var5 = Math.min(0.1F, (float)(var3 - this.last) / 1.0E9F);
         this.last = var3;
         if (!Anim.enabled) {
            return this.value = var1;
         } else {
            this.value = this.value + (var1 - this.value) * (1.0F - (float)Math.exp(-var2 * var5));
            if (Math.abs(var1 - this.value) < 0.001F) {
               this.value = var1;
            }

            return this.value;
         }
      }

      public float get() {
         return this.value;
      }

      public void set(float var1) {
         this.value = var1;
      }
   }
}
