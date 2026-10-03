package dev.dihclient.hud;

import dev.dihclient.setting.BoolSetting;
import java.util.function.Supplier;
import net.minecraft.class_310;
import net.minecraft.class_332;

public abstract class HudElement {
   protected static final class_310 mc = class_310.method_1551();
   private final String id;
   private final String title;
   private final String legacyId;
   private final Supplier<BoolSetting> toggle;
   private final int defaultX;
   private final int defaultY;
   private int x;
   private int y;
   private float scale = 1.0F;
   protected int width = 40;
   protected int height = 12;
   protected boolean rightSide;
   protected boolean bottomSide;

   protected HudElement(String var1, String var2, String var3, int var4, int var5, Supplier<BoolSetting> var6) {
      this.id = var1;
      this.title = var2;
      this.legacyId = var3;
      this.defaultX = this.x = var4;
      this.defaultY = this.y = var5;
      this.toggle = var6;
   }

   public abstract void render(class_332 var1, float var2, boolean var3);

   public boolean isEnabled() {
      BoolSetting var1 = this.toggle == null ? null : this.toggle.get();
      return var1 != null && var1.get();
   }

   public void setEnabled(boolean var1) {
      BoolSetting var2 = this.toggle == null ? null : this.toggle.get();
      if (var2 != null) {
         var2.set(var1);
      }
   }

   public boolean shouldRender() {
      return this.isEnabled();
   }

   public String id() {
      return this.id;
   }

   public String title() {
      return this.title;
   }

   public String legacyId() {
      return this.legacyId;
   }

   public int x() {
      return this.x;
   }

   public int y() {
      return this.y;
   }

   public float scale() {
      return this.scale;
   }

   public void setScale(float var1) {
      this.scale = Math.max(0.5F, Math.min(2.5F, Math.round(var1 * 20.0F) / 20.0F));
   }

   public void setPosition(int var1, int var2) {
      this.x = var1;
      this.y = var2;
   }

   public void resetPosition() {
      this.x = this.defaultX;
      this.y = this.defaultY;
      this.scale = 1.0F;
   }

   public int width() {
      return this.width;
   }

   public int height() {
      return this.height;
   }

   public int scaledWidth() {
      return Math.max(1, Math.round(this.width * this.scale));
   }

   public int scaledHeight() {
      return Math.max(1, Math.round(this.height * this.scale));
   }

   public int screenX(int var1) {
      int var2 = this.scaledWidth();
      int var3 = this.x < 0 ? var1 + this.x + 1 - var2 : this.x;
      return Math.max(0, Math.min(var1 - var2, var3));
   }

   public int screenY(int var1) {
      int var2 = this.scaledHeight();
      int var3 = this.y < 0 ? var1 + this.y + 1 - var2 : this.y;
      return Math.max(0, Math.min(var1 - var2, var3));
   }

   public void setFromScreen(int var1, int var2, int var3, int var4) {
      int var5 = this.scaledWidth();
      int var6 = this.scaledHeight();
      int var7 = var1 + var5 / 2 > var3 / 2 ? var1 + var5 - var3 - 1 : var1;
      int var8 = var2 + var6 / 2 > var4 / 2 ? var2 + var6 - var4 - 1 : var2;
      this.setPosition(var7, var8);
   }

   public void updateSides(int var1, int var2) {
      this.rightSide = this.screenX(var1) + this.scaledWidth() / 2 > var1 / 2;
      this.bottomSide = this.screenY(var2) + this.scaledHeight() / 2 > var2 / 2;
   }
}
