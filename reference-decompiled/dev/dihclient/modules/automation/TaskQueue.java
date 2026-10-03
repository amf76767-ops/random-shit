package dev.dihclient.modules.automation;

import dev.dihclient.DIHClient;
import dev.dihclient.autobuild.BuildRuntime;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.movement.ElytraBot;
import dev.dihclient.modules.player.AutoCraft;
import dev.dihclient.modules.player.AutoFish;
import dev.dihclient.modules.player.AutoMend;
import dev.dihclient.modules.world.AutoBuild;
import dev.dihclient.modules.world.AutoFarm;
import dev.dihclient.modules.world.AutoMine;
import dev.dihclient.modules.world.Goto;
import dev.dihclient.modules.world.Tunnel;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.class_2338;

public class TaskQueue extends Module {
   public final StringSetting tasks = this.text(
      "Tasks",
      "Tasks separated by > or ;  –  goto <x y z | waypoint>, route, elytra <target>, build, mine [min], tunnel [blocks], farm [min], fish <min>, craft, mend, scan, sort, deposit, wait <sec>, cmd <command>, on/off/toggle <module>, stop.",
      "goto mine > mine 20 > goto base > deposit",
      512
   );
   public final BoolSetting loop = this.bool("Loop", "Starts from the first task again after the last one.", false);
   public final EnumSetting<TaskQueue.OnFail> onFail = this.mode(
      "On Fail", "What happens when a task fails (no path, module can't start, timeout).", TaskQueue.OnFail.SKIP
   );
   public final IntSetting timeout = this.integer("Timeout", "Minutes a single task may take before it counts as failed (0 = no limit).", 30, 0, 600);
   public final BoolSetting announce = this.bool("Announce", "Shows a notification for every task.", true);
   private final List<String> list = new ArrayList<>();
   private int index;
   private String current = "";
   private String status = "Idle";
   private long taskStart;
   private long taskEnd;
   private Module waitFor;
   private boolean started;
   private int graceTicks;
   private int completed;
   private int failed;
   private TaskQueue.Kind kind = TaskQueue.Kind.NONE;
   private class_2338 gotoGoal;
   private int passCompleted;
   private long passStart;

   public TaskQueue() {
      super("TaskQueue", Category.AUTOMATION, "Chains bot jobs: goto, build, mine, tunnel, fish, sort/deposit, wait, commands – runs them one after another.");
      this.action("Skip Task", "Ends the current task and starts the next one.", () -> {
         if (this.isEnabled()) {
            this.finishTask(true, "skipped");
         }
      });
   }

   @Override
   protected void onEnable() {
      this.list.clear();

      for (String var4 : this.tasks.get().split("[>;\\n]")) {
         if (!var4.isBlank()) {
            this.list.add(var4.trim());
         }
      }

      if (this.list.isEmpty()) {
         Notifications.warn("TaskQueue", "No tasks – e.g. \"goto mine > mine 20 > goto base > deposit\"");
         this.setEnabledSilently(false);
      } else {
         String var5 = this.validate();
         if (var5 != null) {
            Notifications.warn("TaskQueue", var5);
            this.setEnabledSilently(false);
         } else {
            this.index = 0;
            this.completed = 0;
            this.failed = 0;
            this.passCompleted = 0;
            this.passStart = System.currentTimeMillis();
            this.kind = TaskQueue.Kind.NONE;
            this.status = "Starting";
         }
      }
   }

   @Override
   protected void onDisable() {
      this.stopCurrent();
      this.kind = TaskQueue.Kind.NONE;
      this.current = "";
      this.status = "Idle";
   }

   @Override
   public void onWorldChange() {
   }

   private String validate() {
      for (String var2 : this.list) {
         String var3 = word(var2);
         switch (var3) {
            case "goto":
            case "route":
            case "elytra":
            case "build":
            case "mine":
            case "tunnel":
            case "farm":
            case "fish":
            case "craft":
            case "mend":
            case "scan":
            case "sort":
            case "deposit":
            case "wait":
            case "cmd":
            case "on":
            case "off":
            case "toggle":
            case "stop":
               if ((var3.equals("on") || var3.equals("off") || var3.equals("toggle")) && DIHClient.modules().get(arg(var2)) == null) {
                  return "Unknown module in \"" + var2 + "\"";
               }

               if (var3.equals("fish") && parse(arg(var2), -1) <= 0) {
                  return "\"fish\" needs minutes, e.g. \"fish 30\"";
               }
               break;
            default:
               return "Unknown task \"" + var2 + "\"";
         }
      }

      return null;
   }

   private static String word(String var0) {
      int var1 = var0.indexOf(32);
      return (var1 < 0 ? var0 : var0.substring(0, var1)).toLowerCase(Locale.ROOT);
   }

   private static String arg(String var0) {
      int var1 = var0.indexOf(32);
      return var1 < 0 ? "" : var0.substring(var1 + 1).trim();
   }

   private static int parse(String var0, int var1) {
      try {
         return var0.isEmpty() ? var1 : (int)Math.round(Double.parseDouble(var0.trim().split("\\s+")[0]));
      } catch (NumberFormatException var3) {
         return var1;
      }
   }

   @Override
   public void onTick() {
      if (inGame()) {
         if (this.kind == TaskQueue.Kind.NONE) {
            if (this.index >= this.list.size()) {
               if (!this.loop.get()) {
                  Notifications.push("TaskQueue", "All tasks done (" + this.completed + " ok, " + this.failed + " failed)", Notifications.Type.SUCCESS);
                  this.setEnabled(false);
                  return;
               }

               if (this.completed == this.passCompleted && System.currentTimeMillis() - this.passStart < 10000L) {
                  Notifications.warn("TaskQueue", "Every task failed – loop stopped");
                  this.setEnabled(false);
                  return;
               }

               this.index = 0;
               this.passCompleted = this.completed;
               this.passStart = System.currentTimeMillis();
            }

            this.startTask(this.list.get(this.index));
         } else {
            this.checkTask();
         }
      }
   }

   private void startTask(String var1) {
      this.current = var1;
      this.taskStart = System.currentTimeMillis();
      this.taskEnd = 0L;
      this.waitFor = null;
      this.started = false;
      this.graceTicks = 10;
      this.gotoGoal = null;
      String var2 = word(var1);
      String var3 = arg(var1);
      if (this.announce.get()) {
         Notifications.info("TaskQueue", "Task " + (this.index + 1) + "/" + this.list.size() + ": " + var1);
      }

      switch (var2) {
         case "goto":
            Goto.Target var12 = Goto.resolve(var3);
            if (var12 == null) {
               this.finishTask(false, "unknown target \"" + var3 + "\"");
               return;
            }

            this.gotoGoal = var12.pos();
            Goto.start(var12.pos().method_10263(), var12.hasY() ? var12.pos().method_10264() : Integer.MIN_VALUE, var12.pos().method_10260(), var12.label());
            this.kind = TaskQueue.Kind.GOTO;
            this.waitFor = Goto.instance();
            break;
         case "route":
            Goto.instance().startRouteNow();
            if (!Goto.running()) {
               this.finishTask(false, "route is empty or has an unknown stop");
               return;
            }

            this.kind = TaskQueue.Kind.MODULE;
            this.waitFor = Goto.instance();
            break;
         case "elytra":
            Goto.Target var11 = Goto.resolve(var3);
            if (var11 == null) {
               this.finishTask(false, "unknown target \"" + var3 + "\"");
               return;
            }

            ElytraBot.start(
               var11.pos().method_10263(), var11.hasY() ? var11.pos().method_10264() : Integer.MIN_VALUE, var11.pos().method_10260(), var11.label()
            );
            this.kind = TaskQueue.Kind.MODULE;
            this.waitFor = ModuleManager.of(ElytraBot.class);
            break;
         case "build":
            AutoBuild var10 = ModuleManager.of(AutoBuild.class);
            if (var10.runtime().isRunning()) {
               if (var10.runtime().phase() == BuildRuntime.Phase.PAUSED) {
                  var10.runtime().pauseToggle();
               }
            } else if (!var10.runtime().resume()) {
               this.finishTask(false, "no build to continue (start one in the AutoBuild browser once)");
               return;
            }

            if (!var10.isEnabled()) {
               var10.setEnabled(true);
            }

            this.kind = TaskQueue.Kind.BUILD;
            break;
         case "mine":
            this.runModule(ModuleManager.of(AutoMine.class), parse(var3, 0));
            break;
         case "farm":
            this.runModule(ModuleManager.of(AutoFarm.class), parse(var3, 0));
            break;
         case "fish":
            this.runModule(ModuleManager.of(AutoFish.class), parse(var3, 10));
            break;
         case "craft":
            this.runModule(ModuleManager.of(AutoCraft.class), 0);
            break;
         case "mend":
            this.runModule(ModuleManager.of(AutoMend.class), 0);
            break;
         case "tunnel":
            Tunnel var9 = ModuleManager.of(Tunnel.class);
            int var13 = parse(var3, -1);
            if (var13 > 0) {
               var9.length.set(var13);
            }

            this.runModule(var9, 0);
            break;
         case "scan":
         case "sort":
         case "deposit":
            StashSorter.run(var2.equals("scan") ? StashSorter.Mode.SCAN : (var2.equals("sort") ? StashSorter.Mode.SORT : StashSorter.Mode.DEPOSIT));
            this.kind = TaskQueue.Kind.STASH;
            this.waitFor = ModuleManager.of(StashSorter.class);
            break;
         case "wait":
            this.taskEnd = this.taskStart + Math.max(1, parse(var3, 5)) * 1000L;
            this.kind = TaskQueue.Kind.TIMER;
            break;
         case "cmd":
            String var8 = var3.startsWith("/") ? var3.substring(1) : var3;
            if (!var8.isEmpty() && mc.field_1724 != null) {
               mc.field_1724.field_3944.method_45730(var8);
            }

            this.taskEnd = this.taskStart + 1500L;
            this.kind = TaskQueue.Kind.TIMER;
            break;
         case "on":
         case "off":
         case "toggle":
            Module var6 = DIHClient.modules().get(var3);
            if (var6 == null) {
               this.finishTask(false, "unknown module");
               return;
            }

            boolean var7 = var2.equals("on") || var2.equals("toggle") && !var6.isEnabled();
            if (var6.isEnabled() != var7) {
               var6.setEnabled(var7);
            }

            this.finishTask(true, null);
            return;
         case "stop":
            Notifications.push("TaskQueue", "Stopped by the \"stop\" task", Notifications.Type.INFO);
            this.index = this.list.size();
            this.setEnabled(false);
            return;
         default:
            this.finishTask(false, "unknown task");
            return;
      }

      this.status = "Running: " + var1;
   }

   private void runModule(Module var1, int var2) {
      if (var1 == null) {
         this.finishTask(false, "module missing");
      } else {
         if (var1.isEnabled()) {
            var1.setEnabled(false);
         }

         var1.setEnabled(true);
         if (!var1.isEnabled()) {
            this.finishTask(false, var1.name() + " could not start");
         } else {
            this.waitFor = var1;
            this.kind = TaskQueue.Kind.MODULE;
            if (var2 > 0) {
               this.taskEnd = this.taskStart + var2 * 60000L;
            }
         }
      }
   }

   private void checkTask() {
      long var1 = System.currentTimeMillis();
      if (this.timeout.get() > 0 && var1 - this.taskStart > this.timeout.get().intValue() * 60000L && this.kind != TaskQueue.Kind.TIMER) {
         this.finishTask(false, "timeout");
      } else {
         if (this.graceTicks > 0) {
            this.graceTicks--;
         }

         int var3 = (int)((var1 - this.taskStart) / 1000L);
         switch (this.kind) {
            case MODULE:
            case STASH:
               if (this.taskEnd > 0L && var1 >= this.taskEnd) {
                  this.finishTask(true, null);
               } else if (this.waitFor == null || !this.waitFor.isEnabled() && this.graceTicks == 0) {
                  boolean var7 = this.kind != TaskQueue.Kind.STASH || ModuleManager.of(StashSorter.class).finished();
                  this.finishTask(var7, var7 ? null : "sorting stopped early");
               } else {
                  this.status = this.current
                     + " · "
                     + AutoBuild.time(var3)
                     + (this.taskEnd > 0L ? " / " + AutoBuild.time((int)((this.taskEnd - this.taskStart) / 1000L)) : "");
               }
               break;
            case TIMER:
               this.status = this.current + " · " + Math.max(0L, (this.taskEnd - var1) / 1000L) + "s";
               if (var1 >= this.taskEnd) {
                  this.finishTask(true, null);
               }
               break;
            case GOTO:
               if (!Goto.running() && this.graceTicks == 0) {
                  boolean var6 = this.gotoGoal != null
                     && mc.field_1724 != null
                     && Math.abs(mc.field_1724.method_23317() - this.gotoGoal.method_10263()) < 4.0
                     && Math.abs(mc.field_1724.method_23321() - this.gotoGoal.method_10260()) < 4.0;
                  this.finishTask(var6, var6 ? null : "did not arrive");
               } else {
                  this.status = this.current + " · " + Goto.statusText();
               }
               break;
            case BUILD:
               AutoBuild var4 = ModuleManager.of(AutoBuild.class);
               BuildRuntime.Phase var5 = var4.runtime().phase();
               if (var5 == BuildRuntime.Phase.FINISHED) {
                  this.finishTask(true, null);
               } else if (var4.isEnabled() && var5 != BuildRuntime.Phase.IDLE) {
                  this.status = this.current + " · " + var4.runtime().doneCount() + "/" + var4.runtime().totalCount();
               } else {
                  this.finishTask(false, "build stopped");
               }
               break;
            default:
               this.kind = TaskQueue.Kind.NONE;
         }
      }
   }

   private void stopCurrent() {
      if (this.kind != TaskQueue.Kind.MODULE && this.kind != TaskQueue.Kind.STASH && this.kind != TaskQueue.Kind.GOTO) {
         if (this.kind == TaskQueue.Kind.BUILD) {
            AutoBuild var1 = ModuleManager.of(AutoBuild.class);
            if (var1.runtime().isBuilding()) {
               var1.runtime().pauseToggle();
            }
         }
      } else if (this.waitFor != null && this.waitFor.isEnabled()) {
         this.waitFor.setEnabled(false);
      }
   }

   private void finishTask(boolean var1, String var2) {
      this.stopCurrent();
      this.kind = TaskQueue.Kind.NONE;
      if (var1) {
         this.completed++;
      } else {
         this.failed++;
         Notifications.warn("TaskQueue", "Task \"" + this.current + "\" failed" + (var2 == null ? "" : ": " + var2));
         if (this.onFail.get() == TaskQueue.OnFail.STOP) {
            this.setEnabled(false);
            return;
         }

         if (this.onFail.get() == TaskQueue.OnFail.RETRY && System.currentTimeMillis() - this.taskStart > 3000L) {
            return;
         }
      }

      this.index++;
   }

   @Override
   public String getInfo() {
      return this.isEnabled() && !this.list.isEmpty() ? Math.min(this.index + 1, this.list.size()) + "/" + this.list.size() + " " + word(this.current) : null;
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      if (this.isEnabled()) {
         var1.add("Task " + Math.min(this.index + 1, this.list.size()) + "/" + this.list.size() + " · " + this.completed + " ok · " + this.failed + " failed");
         if (this.index + 1 < this.list.size()) {
            var1.add("Next: " + this.list.get(this.index + 1));
         }
      }

      return var1;
   }

   public String hudLine() {
      return this.isEnabled() ? "Tasks " + Math.min(this.index + 1, this.list.size()) + "/" + this.list.size() + ": " + this.current : null;
   }

   private static enum Kind {
      NONE,
      MODULE,
      TIMER,
      GOTO,
      BUILD,
      STASH;
   }

   public static enum OnFail {
      SKIP,
      RETRY,
      STOP;
   }
}
