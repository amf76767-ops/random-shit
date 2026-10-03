package dev.dihclient.scan;

import dev.dihclient.modules.client.Performance;
import dev.dihclient.util.Compat;

public final class ScanBudget {
   private static long deadline;

   private ScanBudget() {
   }

   public static void begin() {
      Performance var0 = Compat.perf();
      double var1 = var0 == null ? 2.0 : var0.scanBudget.get();
      deadline = System.nanoTime() + (long)(var1 * 1000000.0);
   }

   public static boolean hasTime() {
      return System.nanoTime() < deadline;
   }
}
