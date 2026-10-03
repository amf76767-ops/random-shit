package dev.dihclient.render;

import dev.dihclient.DIHClient;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.util.Notifications;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.class_12249;
import net.minecraft.class_1921;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_4597;
import net.minecraft.class_4608;
import net.minecraft.class_327.class_6415;
import net.minecraft.class_4587.class_4665;
import net.minecraft.class_4597.class_4598;
import org.joml.Vector3f;

public final class Render3D {
   private static final class_310 mc = class_310.method_1551();
   private final class_4587 matrices;
   private final class_4597 consumers;
   private final class_243 cam;
   private final float tickDelta;
   private final Set<class_1921> used = new LinkedHashSet<>();
   private boolean textUsed;
   private class_243 look;
   private final class_1921[] layers = new class_1921[4];
   private final Vector3f normal = new Vector3f();

   private Render3D(class_4587 var1, class_4597 var2, class_243 var3, float var4) {
      this.matrices = var1;
      this.consumers = var2;
      this.cam = var3;
      this.tickDelta = var4;
   }

   public static void render(WorldRenderContext var0, ModuleManager var1) {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         class_4587 var2 = var0.matrices();
         class_4597 var3 = var0.consumers();
         if (var2 != null && var3 != null) {
            class_243 var4 = mc.field_1773.method_19418().method_71156();
            Render3D var5 = new Render3D(var2, var3, var4, mc.method_61966().method_60637(true));
            var2.method_22903();

            try {
               var1.render3D(var5);
            } catch (Throwable var15) {
               DIHClient.LOG.error("[DIHClient] render3D failed", var15);
            } finally {
               var2.method_22909();
            }

            if (var3 instanceof class_4598 var6) {
               for (class_1921 var8 : var5.used) {
                  try {
                     var6.method_22994(var8);
                  } catch (Throwable var17) {
                     if (DihRenderLayers.markFailed()) {
                        DIHClient.LOG.error("[DIHClient] custom render layer failed, switching to safe rendering", var17);
                        mc.execute(() -> Notifications.warn("DIHClient", "Your renderer can't draw through walls – switched to safe rendering."));
                     }
                  }
               }

               if (var5.textUsed) {
                  try {
                     var6.method_22993();
                  } catch (Throwable var14) {
                     DIHClient.LOG.error("[DIHClient] text draw failed", var14);
                  }
               }
            }
         }
      }
   }

   public boolean visible(class_238 var1, double var2) {
      double var4 = (var1.field_1323 + var1.field_1320) / 2.0 - this.cam.field_1352;
      double var6 = (var1.field_1322 + var1.field_1325) / 2.0 - this.cam.field_1351;
      double var8 = (var1.field_1321 + var1.field_1324) / 2.0 - this.cam.field_1350;
      double var10 = (var1.field_1320 - var1.field_1323) / 2.0;
      double var12 = (var1.field_1325 - var1.field_1322) / 2.0;
      double var14 = (var1.field_1324 - var1.field_1321) / 2.0;
      double var16 = Math.sqrt(var10 * var10 + var12 * var12 + var14 * var14);
      double var18 = var4 * var4 + var6 * var6 + var8 * var8;
      if (var2 > 0.0 && var18 > (var2 + var16) * (var2 + var16)) {
         return false;
      } else if (var18 <= var16 * var16) {
         return true;
      } else {
         class_243 var20 = this.lookVec();
         return var4 * var20.field_1352 + var6 * var20.field_1351 + var8 * var20.field_1350 > -var16;
      }
   }

   private class_243 lookVec() {
      if (this.look == null) {
         class_4184 var1 = mc.field_1773.method_19418();
         this.look = class_243.method_1030(var1.method_19329(), var1.method_19330());
      }

      return this.look;
   }

   public float tickDelta() {
      return this.tickDelta;
   }

   public class_243 camera() {
      return this.cam;
   }

   private class_1921 linesLayer(boolean var1) {
      int var2 = var1 ? 0 : 1;
      class_1921 var3 = this.layers[var2];
      if (var3 == null) {
         var3 = this.layers[var2] = DihRenderLayers.lines(var1);
      }

      return var3;
   }

   private class_1921 filledLayer(boolean var1) {
      int var2 = var1 ? 2 : 3;
      class_1921 var3 = this.layers[var2];
      if (var3 == null) {
         var3 = this.layers[var2] = DihRenderLayers.filled(var1);
      }

      return var3;
   }

   private class_4588 buffer(class_1921 var1) {
      this.used.add(var1);
      return this.consumers.method_73477(var1);
   }

   public void line(class_243 var1, class_243 var2, int var3, boolean var4) {
      this.line(var1.field_1352, var1.field_1351, var1.field_1350, var2.field_1352, var2.field_1351, var2.field_1350, var3, var4);
   }

   public void line(double var1, double var3, double var5, double var7, double var9, double var11, int var13, boolean var14) {
      class_4588 var15 = this.buffer(this.linesLayer(var14));
      this.emitLine(
         var15,
         (float)(var1 - this.cam.field_1352),
         (float)(var3 - this.cam.field_1351),
         (float)(var5 - this.cam.field_1350),
         (float)(var7 - this.cam.field_1352),
         (float)(var9 - this.cam.field_1351),
         (float)(var11 - this.cam.field_1350),
         var13
      );
   }

   private void emitLine(class_4588 var1, float var2, float var3, float var4, float var5, float var6, float var7, int var8) {
      class_4665 var9 = this.matrices.method_23760();
      Vector3f var10 = this.normal.set(var5 - var2, var6 - var3, var7 - var4);
      if (var10.lengthSquared() < 1.0E-8F) {
         var10.set(0.0F, 1.0F, 0.0F);
      }

      var10.normalize();
      var1.method_56824(var9, var2, var3, var4).method_39415(var8).method_61959(var9, var10).method_75298(2.0F);
      var1.method_56824(var9, var5, var6, var7).method_39415(var8).method_61959(var9, var10).method_75298(2.0F);
   }

   public void boxOutline(class_238 var1, int var2, boolean var3) {
      if (this.visible(var1, 0.0)) {
         class_4588 var4 = this.buffer(this.linesLayer(var3));
         float var5 = (float)(var1.field_1323 - this.cam.field_1352);
         float var6 = (float)(var1.field_1322 - this.cam.field_1351);
         float var7 = (float)(var1.field_1321 - this.cam.field_1350);
         float var8 = (float)(var1.field_1320 - this.cam.field_1352);
         float var9 = (float)(var1.field_1325 - this.cam.field_1351);
         float var10 = (float)(var1.field_1324 - this.cam.field_1350);
         this.emitLine(var4, var5, var6, var7, var8, var6, var7, var2);
         this.emitLine(var4, var8, var6, var7, var8, var6, var10, var2);
         this.emitLine(var4, var8, var6, var10, var5, var6, var10, var2);
         this.emitLine(var4, var5, var6, var10, var5, var6, var7, var2);
         this.emitLine(var4, var5, var9, var7, var8, var9, var7, var2);
         this.emitLine(var4, var8, var9, var7, var8, var9, var10, var2);
         this.emitLine(var4, var8, var9, var10, var5, var9, var10, var2);
         this.emitLine(var4, var5, var9, var10, var5, var9, var7, var2);
         this.emitLine(var4, var5, var6, var7, var5, var9, var7, var2);
         this.emitLine(var4, var8, var6, var7, var8, var9, var7, var2);
         this.emitLine(var4, var8, var6, var10, var8, var9, var10, var2);
         this.emitLine(var4, var5, var6, var10, var5, var9, var10, var2);
      }
   }

   public void boxFilled(class_238 var1, int var2, boolean var3) {
      if (this.visible(var1, 0.0)) {
         class_4588 var4 = this.buffer(this.filledLayer(var3));
         class_4665 var5 = this.matrices.method_23760();
         float var6 = (float)(var1.field_1323 - this.cam.field_1352);
         float var7 = (float)(var1.field_1322 - this.cam.field_1351);
         float var8 = (float)(var1.field_1321 - this.cam.field_1350);
         float var9 = (float)(var1.field_1320 - this.cam.field_1352);
         float var10 = (float)(var1.field_1325 - this.cam.field_1351);
         float var11 = (float)(var1.field_1324 - this.cam.field_1350);
         quad(var4, var5, var6, var7, var8, var9, var7, var8, var9, var7, var11, var6, var7, var11, var2);
         quad(var4, var5, var6, var10, var8, var6, var10, var11, var9, var10, var11, var9, var10, var8, var2);
         quad(var4, var5, var6, var7, var8, var6, var10, var8, var9, var10, var8, var9, var7, var8, var2);
         quad(var4, var5, var6, var7, var11, var9, var7, var11, var9, var10, var11, var6, var10, var11, var2);
         quad(var4, var5, var6, var7, var8, var6, var7, var11, var6, var10, var11, var6, var10, var8, var2);
         quad(var4, var5, var9, var7, var8, var9, var10, var8, var9, var10, var11, var9, var7, var11, var2);
      }
   }

   private static void quad(
      class_4588 var0,
      class_4665 var1,
      float var2,
      float var3,
      float var4,
      float var5,
      float var6,
      float var7,
      float var8,
      float var9,
      float var10,
      float var11,
      float var12,
      float var13,
      int var14
   ) {
      var0.method_56824(var1, var2, var3, var4).method_39415(var14);
      var0.method_56824(var1, var5, var6, var7).method_39415(var14);
      var0.method_56824(var1, var8, var9, var10).method_39415(var14);
      var0.method_56824(var1, var11, var12, var13).method_39415(var14);
   }

   public void skyQuad(float[] var1, int var2) {
      class_4588 var3 = this.buffer(DihRenderLayers.filled(false));
      class_4665 var4 = this.matrices.method_23760();
      var3.method_56824(var4, var1[0], var1[1], var1[2]).method_39415(var2);
      var3.method_56824(var4, var1[3], var1[4], var1[5]).method_39415(var2);
      var3.method_56824(var4, var1[6], var1[7], var1[8]).method_39415(var2);
      var3.method_56824(var4, var1[9], var1[10], var1[11]).method_39415(var2);
   }

   public void skyQuad(Vector3f var1, Vector3f var2, Vector3f var3, Vector3f var4, int var5, int var6, int var7, int var8) {
      class_4588 var9 = this.buffer(DihRenderLayers.filled(false));
      class_4665 var10 = this.matrices.method_23760();
      var9.method_56824(var10, var1.x, var1.y, var1.z).method_39415(var5);
      var9.method_56824(var10, var2.x, var2.y, var2.z).method_39415(var6);
      var9.method_56824(var10, var3.x, var3.y, var3.z).method_39415(var7);
      var9.method_56824(var10, var4.x, var4.y, var4.z).method_39415(var8);
   }

   public void box(class_238 var1, int var2, int var3, boolean var4) {
      if (var3 > 0) {
         this.boxFilled(var1, var2 & 16777215 | Math.min(255, var3) << 24, var4);
      }

      this.boxOutline(var1, var2, var4);
   }

   public void billboard(class_2960 var1, class_243 var2, float var3, float var4, float var5, float var6, int var7, int var8, float var9) {
      class_4588 var10 = this.buffer(class_12249.method_76000(var1));
      class_4184 var11 = mc.field_1773.method_19418();
      double var12 = Math.toRadians(var11.method_19330());
      double var14 = Math.toRadians(var11.method_19329());
      Vector3f var16 = new Vector3f((float)(-Math.cos(var12)), 0.0F, (float)(-Math.sin(var12)));
      Vector3f var17 = new Vector3f((float)(-Math.sin(var14) * -Math.sin(var12)), (float)Math.cos(var14), (float)(-Math.sin(var14) * Math.cos(var12)));
      if (var9 != 0.0F) {
         float var18 = (float)Math.cos(var9);
         float var19 = (float)Math.sin(var9);
         Vector3f var20 = new Vector3f(var16).mul(var18).add(new Vector3f(var17).mul(var19));
         Vector3f var21 = new Vector3f(var17).mul(var18).sub(new Vector3f(var16).mul(var19));
         var16 = var20;
         var17 = var21;
      }

      float var33 = var3 / 2.0F;
      float var34 = var4 / 2.0F;
      float var35 = (float)(var2.field_1352 - this.cam.field_1352);
      float var36 = (float)(var2.field_1351 - this.cam.field_1351);
      float var22 = (float)(var2.field_1350 - this.cam.field_1350);
      class_4665 var23 = this.matrices.method_23760();
      int var24 = class_4608.field_21444;
      float[][] var25 = new float[][]{{-var33, -var34, 0.0F, var6}, {var33, -var34, 1.0F, var6}, {var33, var34, 1.0F, var5}, {-var33, var34, 0.0F, var5}};
      Vector3f var26 = new Vector3f(var16).cross(var17);
      boolean var27 = var26.x * -var35 + var26.y * -var36 + var26.z * -var22 < 0.0F;

      for (int var28 = 0; var28 < 4; var28++) {
         float[] var29 = var25[var27 ? 3 - var28 : var28];
         float var30 = var35 + var16.x * var29[0] + var17.x * var29[1];
         float var31 = var36 + var16.y * var29[0] + var17.y * var29[1];
         float var32 = var22 + var16.z * var29[0] + var17.z * var29[1];
         var10.method_56824(var23, var30, var31, var32)
            .method_39415(var7)
            .method_22913(var29[2], var29[3])
            .method_22922(var24)
            .method_60803(var8)
            .method_60831(var23, 0.0F, 1.0F, 0.0F);
      }
   }

   public void tracer(class_243 var1, int var2) {
      class_243 var3 = class_243.method_1030(mc.field_1773.method_19418().method_19329(), mc.field_1773.method_19418().method_19330());
      class_243 var4 = this.cam.method_1019(var3.method_1021(0.5));
      this.line(var4, var1, var2, true);
   }

   public void text(String var1, class_243 var2, int var3, float var4) {
      class_327 var5 = mc.field_1772;
      this.matrices.method_22903();
      this.matrices.method_22904(var2.field_1352 - this.cam.field_1352, var2.field_1351 - this.cam.field_1351, var2.field_1350 - this.cam.field_1350);
      this.matrices.method_22907(mc.field_1773.method_19418().method_23767());
      float var6 = 0.025F * var4;
      this.matrices.method_22905(var6, -var6, var6);
      float var7 = -var5.method_1727(var1) / 2.0F;
      var5.method_27521(
         var1, var7, 0.0F, var3, false, this.matrices.method_23760().method_23761(), this.consumers, class_6415.field_33994, 1610612736, 15728880
      );
      this.matrices.method_22909();
      this.textUsed = true;
   }
}
