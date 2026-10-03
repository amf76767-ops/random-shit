package dev.dihclient.modules.render;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.util.KeyUtil;
import net.minecraft.class_3532;
import net.minecraft.class_5498;

public class FreeLook extends Module {
   public final EnumSetting<FreeLook.Mode> mode = this.mode(
      "Mode", "Camera: mouse moves the camera, player keeps its direction. Player: mouse moves the player, camera stays.", FreeLook.Mode.CAMERA
   );
   public final EnumSetting<FreeLook.Activation> activation = this.mode(
      "Activation", "Toggle: key on / key off. Hold: only while the key is held.", FreeLook.Activation.TOGGLE
   );
   public final BoolSetting togglePerspective = this.bool("Toggle Perspective", "Switches to third person while FreeLook is on.", true)
      .legacy("freelook.thirdPerson");
   public final BoolSetting arrows = this.bool("Arrows Control Opposite", "Arrow keys turn the other one (player in Camera mode, camera in Player mode).", true);
   public final DoubleSetting arrowSpeed = this.dbl("Arrow Speed", "Turn speed of the arrow keys in degrees per tick.", 4.0, 0.5, 20.0, 0.5)
      .visibleWhen(this.arrows::get);
   public final BoolSetting clip = this.bool(
      "Camera Clip", "The third-person camera goes through walls instead of being pushed to your head – look around corners and through walls.", true
   );
   public final DoubleSetting distance = this.dbl("Distance", "Third-person camera distance while FreeLook is on (1 = vanilla).", 1.0, 0.5, 6.0, 0.1);
   public float cameraYaw;
   public float cameraPitch;
   private class_5498 previous;
   private boolean activatedByKey;

   public FreeLook() {
      super("FreeLook", Category.RENDER, "Look around freely while your player keeps its direction (like Meteor). Third person by default.");
      this.setShowToggleNotification(false);
   }

   public static FreeLook active() {
      return ModuleManager.on(FreeLook.class) ? ModuleManager.of(FreeLook.class) : null;
   }

   public static boolean cameraClip() {
      FreeLook var0 = active();
      return var0 != null && var0.clip.get();
   }

   public static float distanceFactor() {
      FreeLook var0 = active();
      return var0 == null ? 1.0F : var0.distance.getFloat();
   }

   @Override
   protected void onEnable() {
      if (mc.field_1724 == null) {
         this.setEnabledSilently(false);
      } else {
         this.cameraYaw = mc.field_1724.method_36454();
         this.cameraPitch = mc.field_1724.method_36455();
         this.activatedByKey = this.keybind() >= 0 && KeyUtil.isKeyDown(this.keybind());
         this.previous = mc.field_1690.method_31044();
         if (this.togglePerspective.get() && this.previous == class_5498.field_26664) {
            mc.field_1690.method_31043(class_5498.field_26665);
         }
      }
   }

   @Override
   protected void onDisable() {
      if (this.previous != null && mc.field_1690 != null && this.togglePerspective.get()) {
         mc.field_1690.method_31043(this.previous);
      }

      this.previous = null;
   }

   @Override
   public void onWorldChange() {
      if (this.isEnabled()) {
         this.setEnabled(false);
      }
   }

   @Override
   public void onTick() {
      if (this.activation.get() == FreeLook.Activation.HOLD && this.activatedByKey && !KeyUtil.isKeyDown(this.keybind())) {
         this.setEnabled(false);
      } else if (this.arrows.get() && mc.field_1755 == null) {
         float var1 = this.arrowSpeed.getFloat();
         float var2 = 0.0F;
         float var3 = 0.0F;
         if (KeyUtil.isKeyDown(263)) {
            var2 -= var1;
         }

         if (KeyUtil.isKeyDown(262)) {
            var2 += var1;
         }

         if (KeyUtil.isKeyDown(265)) {
            var3 -= var1;
         }

         if (KeyUtil.isKeyDown(264)) {
            var3 += var1;
         }

         if (var2 != 0.0F || var3 != 0.0F) {
            if (this.mode.get() == FreeLook.Mode.CAMERA) {
               mc.field_1724.method_36456(mc.field_1724.method_36454() + var2);
               mc.field_1724.method_36457(class_3532.method_15363(mc.field_1724.method_36455() + var3, -90.0F, 90.0F));
            } else {
               this.cameraYaw += var2;
               this.cameraPitch = class_3532.method_15363(this.cameraPitch + var3, -90.0F, 90.0F);
            }
         }
      }
   }

   public boolean look(double var1, double var3) {
      if (this.mode.get() == FreeLook.Mode.PLAYER) {
         return false;
      } else {
         this.cameraYaw += (float)(var1 * 0.15);
         this.cameraPitch = class_3532.method_15363(this.cameraPitch + (float)(var3 * 0.15), -90.0F, 90.0F);
         return true;
      }
   }

   public float yaw() {
      return this.cameraYaw;
   }

   public float pitch() {
      return this.cameraPitch;
   }

   @Override
   public String getInfo() {
      return this.mode.displayValue();
   }

   public static enum Activation {
      TOGGLE,
      HOLD;
   }

   public static enum Mode {
      CAMERA,
      PLAYER;
   }
}
