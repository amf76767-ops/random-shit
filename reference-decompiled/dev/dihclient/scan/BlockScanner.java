package dev.dihclient.scan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.class_1923;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_2806;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_310;

public final class BlockScanner {
   private static final class_310 mc = class_310.method_1551();
   private final Predicate<class_2680> filter;
   private final Map<class_2680, Boolean> stateCache = new IdentityHashMap<>();
   private final Predicate<class_2680> cachedFilter = this::matches;
   private class_2680 lastState;
   private boolean lastResult;
   private final Map<Long, List<class_2338>> results = new ConcurrentHashMap<>();
   private final Deque<Long> queue = new ArrayDeque<>();
   private final Set<Long> queued = new HashSet<>();
   private int chunksPerTick;
   private int maxPerChunk = 512;
   private Object lastWorld;
   private long lastRefill;
   private int ticks;
   private static final long REFILL_MS = 4000L;

   public BlockScanner(Predicate<class_2680> var1, int var2) {
      this.filter = var1;
      this.chunksPerTick = var2;
   }

   public void setChunksPerTick(int var1) {
      this.chunksPerTick = Math.max(1, var1);
   }

   public void setMaxPerChunk(int var1) {
      this.maxPerChunk = var1;
   }

   public void clear() {
      this.stateCache.clear();
      this.lastState = null;
      this.lastRefill = 0L;
      this.results.clear();
      this.queue.clear();
      this.queued.clear();
   }

   public void tick() {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         if (this.lastWorld != mc.field_1687) {
            this.lastWorld = mc.field_1687;
            this.clear();
         }

         long var1 = System.currentTimeMillis();
         if (this.queue.isEmpty() && var1 - this.lastRefill > 4000L) {
            this.lastRefill = var1;
            this.refill();
         }

         for (int var3 = 0; var3 < this.chunksPerTick && !this.queue.isEmpty() && (var3 <= 0 || ScanBudget.hasTime()); var3++) {
            long var4 = this.queue.pollFirst();
            this.queued.remove(var4);
            this.scan(class_1923.method_8325(var4), class_1923.method_8332(var4));
         }

         if (++this.ticks % 20 == 0) {
            this.results.keySet().removeIf(var0 -> !mc.field_1687.method_2935().method_12123(class_1923.method_8325(var0), class_1923.method_8332(var0)));
         }
      }
   }

   public void prioritize(int var1, int var2) {
      long var3 = class_1923.method_8331(var1, var2);
      if (this.queued.add(var3)) {
         this.queue.addFirst(var3);
      }
   }

   private void refill() {
      int var1 = mc.field_1690.method_38521() + 1;
      class_1923 var2 = mc.field_1724.method_31476();
      ArrayList var3 = new ArrayList();

      for (int var4 = -var1; var4 <= var1; var4++) {
         for (int var5 = -var1; var5 <= var1; var5++) {
            int var6 = var2.field_9181 + var4;
            int var7 = var2.field_9180 + var5;
            if (mc.field_1687.method_2935().method_12123(var6, var7)) {
               var3.add(new long[]{class_1923.method_8331(var6, var7), (long)var4 * var4 + (long)var5 * var5});
            }
         }
      }

      var3.sort((var0, var1x) -> Long.compare(var0[1], var1x[1]));

      for (long[] var9 : var3) {
         if (this.queued.add(var9[0])) {
            this.queue.addLast(var9[0]);
         }
      }
   }

   private boolean matches(class_2680 var1) {
      if (var1 == this.lastState) {
         return this.lastResult;
      } else {
         Boolean var2 = this.stateCache.get(var1);
         if (var2 == null) {
            var2 = this.filter.test(var1);
            this.stateCache.put(var1, var2);
         }

         this.lastState = var1;
         this.lastResult = var2;
         return var2;
      }
   }

   private void scan(int var1, int var2) {
      class_2818 var3 = mc.field_1687.method_2935().method_2857(var1, var2, class_2806.field_12803, false);
      long var4 = class_1923.method_8331(var1, var2);
      if (var3 == null) {
         this.results.remove(var4);
      } else {
         ArrayList var6 = new ArrayList();
         class_2826[] var7 = var3.method_12006();
         int var8 = mc.field_1687.method_31607() >> 4;
         int var9 = var1 << 4;
         int var10 = var2 << 4;

         label59:
         for (int var11 = 0; var11 < var7.length; var11++) {
            class_2826 var12 = var7[var11];
            if (var12 != null && !var12.method_38292() && var12.method_19523(this.cachedFilter)) {
               int var13 = var8 + var11 << 4;

               for (int var14 = 0; var14 < 16; var14++) {
                  for (int var15 = 0; var15 < 16; var15++) {
                     for (int var16 = 0; var16 < 16; var16++) {
                        if (this.matches(var12.method_12254(var16, var14, var15))) {
                           var6.add(new class_2338(var9 + var16, var13 + var14, var10 + var15));
                           if (var6.size() >= this.maxPerChunk) {
                              break label59;
                           }
                        }
                     }
                  }
               }
            }
         }

         if (var6.isEmpty()) {
            this.results.remove(var4);
         } else {
            this.results.put(var4, var6);
         }
      }
   }

   public Map<Long, List<class_2338>> results() {
      return Collections.unmodifiableMap(this.results);
   }

   public List<class_2338> all() {
      ArrayList var1 = new ArrayList();

      for (List var3 : this.results.values()) {
         var1.addAll(var3);
      }

      return var1;
   }

   public int count() {
      int var1 = 0;

      for (List var3 : this.results.values()) {
         var1 += var3.size();
      }

      return var1;
   }
}
