package dev.dihclient.modules.basefinding;

import dev.dihclient.mixin.accessor.ProfilerAccessor;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.class_3417;
import net.minecraft.class_3534;
import net.minecraft.class_3696;
import net.minecraft.class_4757;

public class SpawnerPie extends Module {
   private static volatile boolean wantProfiler;
   private static final char SEP = '\u001e';
   public final BoolSetting trial = this.bool("Trial Spawners", "Also alarms for trial spawners.", true);
   public final BoolSetting sound = this.bool("Sound", "Plays an alarm sound.", true);
   public final BoolSetting bigAlert = this.bool("Big Alert", "Shows a large warning on screen.", true);
   public final IntSetting repeat = this.integer("Repeat (s)", "Repeats the sound while a spawner stays in range (0 = once).", 4, 0, 30);
   public final IntSetting hold = this.integer("Re-arm After (s)", "How long no spawner must show up before the alarm triggers again.", 20, 3, 120);
   public final IntSetting interval = this.integer("Check Interval", "Ticks between two pie chart reads.", 10, 2, 40);
   private final Set<String> alarmed = new HashSet<>();
   private final List<String> hits = new ArrayList<>();
   private long lastSeen;
   private int now;
   private int repeatTimer;
   private int emptyTicks;
   private String status = "Idle";

   public SpawnerPie() {
      super("SpawnerPie", Category.BASEFINDING, "Alarm when a spawner shows up in the profiler pie chart (blockEntities). Works without opening F3.");
   }

   public static boolean wantsProfiler() {
      return wantProfiler;
   }

   @Override
   protected void onEnable() {
      wantProfiler = true;
      this.alarmed.clear();
      this.hits.clear();
      this.emptyTicks = 0;
      this.status = "Waiting for profiler data";
   }

   @Override
   protected void onDisable() {
      wantProfiler = false;
      this.hits.clear();
      this.status = "Idle";
   }

   private void walk(class_3696 var1, String var2, int var3, boolean var4, List<SpawnerPie.Hit> var5, int[] var6) {
      List var7 = var1.method_16067(var2);
      if (var7 != null) {
         for (class_3534 var9 : var7) {
            if (--var6[0] < 0) {
               return;
            }

            String var10 = var9.field_15738;
            String var11 = var10.toLowerCase(Locale.ROOT);
            boolean var12 = var4 || var11.equals("blockentities");
            if (var12 && var11.contains("spawner") && (this.trial.get() || !var11.contains("trial"))) {
               var5.add(new SpawnerPie.Hit(var10, var9.field_15737, var9.field_19384));
            }

            if (var3 < 9) {
               this.walk(var1, var2 + "\u001e" + var10, var3 + 1, var12, var5, var6);
            }
         }
      }
   }

   @Override
   public void onTick() {
      if (inGame()) {
         this.now++;
         if (this.repeatTimer > 0) {
            this.repeatTimer--;
         }

         if (this.now % this.interval.get() == 0) {
            class_4757 var1 = ((ProfilerAccessor)mc).dih$tracker();
            class_3696 var2 = var1 == null ? null : var1.method_24337();
            ArrayList var3 = new ArrayList();
            boolean var4 = false;
            if (var2 != null) {
               List var5 = var2.method_16067("root");
               var4 = var5 != null && !var5.isEmpty();
               if (var4) {
                  this.walk(var2, "root", 0, false, var3, new int[]{4000});
               }
            }

            if (var4) {
               this.emptyTicks = 0;
            } else if (++this.emptyTicks * this.interval.get() > 100) {
               this.status = "No profiler data – open F3 + Shift+3 once";
            }

            this.hits.clear();
            HashSet var9 = new HashSet();

            for (SpawnerPie.Hit var7 : var3) {
               String var8 = var7.name() + " · " + String.format(Locale.ROOT, "%.3f", var7.percent()) + "% · " + var7.visits() + " visits";
               if (!this.hits.contains(var8)) {
                  this.hits.add(var8);
               }

               var9.add(var7.name());
            }

            if (!var9.isEmpty()) {
               this.status = "Spawner in range: " + String.join(", ", var9);
               this.lastSeen = this.now;
               boolean var10 = false;

               for (String var12 : var9) {
                  if (this.alarmed.add(var12)) {
                     var10 = true;
                  }
               }

               if (var10) {
                  this.alarm(var9);
                  this.repeatTimer = this.repeat.get() * 20;
               } else if (this.repeat.get() > 0 && this.repeatTimer == 0 && this.sound.get()) {
                  this.ping();
                  this.repeatTimer = this.repeat.get() * 20;
               }
            } else if (var4) {
               if (this.now - this.lastSeen > this.hold.get().intValue() * 20L) {
                  this.alarmed.clear();
               }

               if (this.alarmed.isEmpty()) {
                  this.status = "Watching the pie chart";
               }
            }
         }
      }
   }

   private void ping() {
      mc.field_1724.method_5783(class_3417.field_17265, 1.0F, 1.0F);
   }

   private void alarm(Set<String> var1) {
      int var2 = (int)Math.floor(mc.field_1724.method_23317());
      int var3 = (int)Math.floor(mc.field_1724.method_23321());
      String var4 = String.join(", ", var1).replace("minecraft:", "");
      Notifications.push(this.name(), "Spawner detected (" + var4 + ") near " + var2 + " " + var3, Notifications.Type.WARNING);
      if (this.bigAlert.get()) {
         Notifications.alert("SPAWNER DETECTED", 100);
      }

      if (this.sound.get()) {
         this.ping();
      }
   }

   @Override
   public String getInfo() {
      return this.hits.isEmpty() ? null : this.hits.size() + "";
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      var1.addAll(this.hits);
      return var1;
   }

   private record Hit(String name, double percent, long visits) {
   }
}
