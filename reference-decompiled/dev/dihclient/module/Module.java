package dev.dihclient.module;

import dev.dihclient.DIHClient;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.ActionSetting;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.Setting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.class_2596;
import net.minecraft.class_310;
import net.minecraft.class_332;

public abstract class Module {
   protected static final class_310 mc = class_310.method_1551();
   private final String name;
   private final Category category;
   private final String description;
   private final List<Setting<?>> settings = new ArrayList<>();
   private boolean enabled;
   private boolean pendingEnable;
   private int keybind = -1;
   private boolean showToggleNotification = true;
   private boolean hidden;

   protected Module(String var1, Category var2, String var3) {
      this.name = var1;
      this.category = var2;
      this.description = var3;
   }

   public final void toggle() {
      this.setEnabled(!this.enabled);
   }

   public final void setEnabled(boolean var1) {
      if (var1 != this.enabled) {
         this.enabled = var1;
         this.pendingEnable = false;

         try {
            if (var1) {
               this.onEnable();
            } else {
               this.onDisable();
            }
         } catch (Throwable var3) {
            DIHClient.LOG.error("[DIHClient] {} {} failed", new Object[]{this.name, var1 ? "enable" : "disable", var3});
         }

         if (this.showToggleNotification && this.isToggleable()) {
            Notifications.toggle(this);
         }

         DIHClient.config().markDirty();
      }
   }

   public final void setEnabledSilently(boolean var1) {
      if (var1 != this.enabled) {
         this.enabled = var1;
         this.pendingEnable = false;
         if (mc.field_1724 != null) {
            try {
               if (var1) {
                  this.onEnable();
               } else {
                  this.onDisable();
               }
            } catch (Throwable var3) {
               DIHClient.LOG.error("[DIHClient] {} state change failed", this.name, var3);
            }
         } else if (var1) {
            this.pendingEnable = true;
         }
      }
   }

   public final void runPendingEnable() {
      if (this.pendingEnable && this.enabled && mc.field_1724 != null) {
         this.pendingEnable = false;

         try {
            this.onEnable();
         } catch (Throwable var2) {
            DIHClient.LOG.error("[DIHClient] {} deferred enable failed", this.name, var2);
         }
      }
   }

   protected void onEnable() {
   }

   protected void onDisable() {
   }

   public void onTick() {
   }

   public void onRender2D(class_332 var1, float var2) {
   }

   public void onRender3D(Render3D var1) {
   }

   public boolean onPacketSend(class_2596<?> var1) {
      return false;
   }

   public void onWorldChange() {
   }

   public void onAction() {
   }

   public boolean isActionModule() {
      return false;
   }

   public boolean isToggleable() {
      return true;
   }

   public String getInfo() {
      return null;
   }

   public List<String> details() {
      return Collections.emptyList();
   }

   protected <S extends Setting<?>> S add(S var1) {
      this.settings.add(var1);
      return (S)var1;
   }

   protected BoolSetting bool(String var1, String var2, boolean var3) {
      return this.add(new BoolSetting(var1, var2, var3));
   }

   protected DoubleSetting dbl(String var1, String var2, double var3, double var5, double var7, double var9) {
      return this.add(new DoubleSetting(var1, var2, var3, var5, var7, var9));
   }

   protected IntSetting integer(String var1, String var2, int var3, int var4, int var5) {
      return this.add(new IntSetting(var1, var2, var3, var4, var5));
   }

   protected <E extends Enum<E>> EnumSetting<E> mode(String var1, String var2, E var3) {
      return this.add(new EnumSetting<>(var1, var2, (E)var3));
   }

   protected ColorSetting color(String var1, String var2, int var3) {
      return this.add(new ColorSetting(var1, var2, var3));
   }

   protected IdListSetting ids(String var1, String var2, IdListSetting.Kind var3, String... var4) {
      return this.add(new IdListSetting(var1, var2, var3, var4));
   }

   protected StringSetting text(String var1, String var2, String var3, int var4) {
      return this.add(new StringSetting(var1, var2, var3, var4));
   }

   protected ActionSetting action(String var1, String var2, Runnable var3) {
      return this.add(new ActionSetting(var1, var2, var3));
   }

   public String name() {
      return this.name;
   }

   public Category category() {
      return this.category;
   }

   public String description() {
      return this.description;
   }

   public List<Setting<?>> settings() {
      return this.settings;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public int keybind() {
      return this.keybind;
   }

   public void setKeybind(int var1) {
      this.keybind = var1;
      DIHClient.config().markDirty();
   }

   public void setKeybindSilently(int var1) {
      this.keybind = var1;
   }

   public boolean showToggleNotification() {
      return this.showToggleNotification;
   }

   public void setShowToggleNotification(boolean var1) {
      this.showToggleNotification = var1;
   }

   public boolean isHidden() {
      return this.hidden;
   }

   public void setHidden(boolean var1) {
      this.hidden = var1;
   }

   public String id() {
      return this.name.toLowerCase().replace(' ', '_');
   }

   protected static boolean inGame() {
      return mc.field_1724 != null && mc.field_1687 != null && mc.field_1761 != null;
   }
}
