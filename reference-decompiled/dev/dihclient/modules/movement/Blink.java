package dev.dihclient.modules.movement;

import dev.dihclient.hud.HudManager;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2828;

public class Blink extends Module {
   public final EnumSetting<Blink.Mode> mode = this.mode(
      "Mode", "Hold: keeps the packets until you turn it off (or a limit is hit). Pulse: releases them every few ticks (fake lag).", Blink.Mode.HOLD
   );
   public final EnumSetting<Blink.End> onEnd = this.mode(
      "On Disable",
      "Send: the server gets your real path (teleport). Cancel: the packets are thrown away and you snap back to where you started.",
      Blink.End.SEND
   );
   public final IntSetting pulseTicks = this.integer("Pulse Ticks", "Ticks between two releases.", 8, 2, 40)
      .visibleWhen(() -> this.mode.get() == Blink.Mode.PULSE);
   public final IntSetting maxSeconds = this.integer("Max Seconds", "Releases automatically after this long (0 = never).", 20, 0, 120)
      .visibleWhen(() -> this.mode.get() == Blink.Mode.HOLD);
   public final IntSetting maxDistance = this.integer("Max Distance", "Releases when you got this far from the server's position (0 = off).", 0, 0, 200)
      .visibleWhen(() -> this.mode.get() == Blink.Mode.HOLD);
   public final BoolSetting releaseOnHurt = this.bool(
      "Release On Hurt", "Sends everything when you take damage so the server knows where you really are.", true
   );
   public final BoolSetting rerun = this.bool("Keep Going", "After an automatic release it starts holding again.", true)
      .visibleWhen(() -> this.mode.get() == Blink.Mode.HOLD);
   public final BoolSetting ghost = this.bool("Show Server Position", "Draws where the server thinks you are.", true);
   private final List<class_2596<?>> buffer = new ArrayList<>();
   private boolean flushing;
   private class_243 serverPos;
   private float serverYaw;
   private float serverPitch;
   private int held;
   private int heldTotal;

   public Blink() {
      super("Blink", Category.MOVEMENT, "Holds your movement packets: move on your screen while the server still sees the old spot, then release or snap back.");
      this.action("Release", "Sends the held packets now and keeps blinking.", () -> this.release(true));
   }

   @Override
   protected void onEnable() {
      this.buffer.clear();
      this.held = 0;
      this.heldTotal = 0;
      this.markStart();
   }

   private void markStart() {
      if (mc.field_1724 != null) {
         this.serverPos = mc.field_1724.method_73189();
         this.serverYaw = mc.field_1724.method_36454();
         this.serverPitch = mc.field_1724.method_36455();
      }
   }

   @Override
   protected void onDisable() {
      if (this.onEnd.get() == Blink.End.CANCEL && this.serverPos != null && mc.field_1724 != null && !this.buffer.isEmpty()) {
         this.buffer.clear();
         mc.field_1724.method_5808(this.serverPos.field_1352, this.serverPos.field_1351, this.serverPos.field_1350, this.serverYaw, this.serverPitch);
         mc.field_1724.method_18800(0.0, 0.0, 0.0);
      } else {
         this.release(false);
      }

      this.serverPos = null;
   }

   @Override
   public void onWorldChange() {
      this.buffer.clear();
      this.serverPos = null;
      if (this.isEnabled()) {
         this.setEnabledSilently(false);
      }
   }

   @Override
   public boolean onPacketSend(class_2596<?> var1) {
      if (!this.flushing && var1 instanceof class_2828) {
         if (this.buffer.isEmpty()) {
            this.markStart();
         }

         this.buffer.add(var1);
         return true;
      } else {
         return false;
      }
   }

   private void release(boolean var1) {
      if (mc.method_1562() != null && !this.buffer.isEmpty()) {
         this.flushing = true;

         try {
            for (class_2596 var3 : new ArrayList<>(this.buffer)) {
               mc.method_1562().method_52787(var3);
            }
         } finally {
            this.buffer.clear();
            this.flushing = false;
         }
      } else {
         this.buffer.clear();
      }

      this.held = 0;
      this.markStart();
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && !this.buffer.isEmpty()) {
         this.held++;
         this.heldTotal++;
         if (this.releaseOnHurt.get() && mc.field_1724.field_6235 > 0) {
            this.release(true);
         } else if (this.mode.get() == Blink.Mode.PULSE) {
            if (this.held >= this.pulseTicks.get()) {
               this.release(true);
            }
         } else {
            boolean var1 = this.maxSeconds.get() > 0 && this.held >= this.maxSeconds.get() * 20;
            boolean var2 = this.maxDistance.get() > 0
               && this.serverPos != null
               && mc.field_1724.method_73189().method_1022(this.serverPos) >= this.maxDistance.get().intValue();
            if (var1 || var2) {
               this.release(true);
               if (!this.rerun.get()) {
                  this.setEnabled(false);
               }
            }
         }
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.ghost.get() && this.serverPos != null && !this.buffer.isEmpty()) {
         class_243 var2 = this.serverPos;
         var1.boxOutline(
            new class_238(var2.field_1352 - 0.3, var2.field_1351, var2.field_1350 - 0.3, var2.field_1352 + 0.3, var2.field_1351 + 1.8, var2.field_1350 + 0.3),
            HudManager.accent(),
            true
         );
         var1.line(
            var2.field_1352,
            var2.field_1351 + 0.9,
            var2.field_1350,
            mc.field_1724.method_23317(),
            mc.field_1724.method_23318() + 0.9,
            mc.field_1724.method_23321(),
            HudManager.accent(),
            true
         );
      }
   }

   @Override
   public String getInfo() {
      return this.buffer.isEmpty() ? null : String.format(Locale.ROOT, "%.1fs · %d", this.held / 20.0, this.buffer.size());
   }

   public static enum End {
      SEND,
      CANCEL;
   }

   public static enum Mode {
      HOLD,
      PULSE;
   }
}
