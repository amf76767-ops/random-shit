package dev.dihclient.render;

import net.minecraft.class_243;
import org.joml.Vector3f;

public final class Shapes {
   private final Render3D r;
   private double ox;
   private double oy;
   private double oz;
   private double cy = 1.0;
   private double sy;
   private double scale = 1.0;
   private boolean part;
   private double px;
   private double py;
   private double pz;
   private double c1 = 1.0;
   private double s1;
   private double c2 = 1.0;
   private double s2;
   private double c3 = 1.0;
   private double s3;
   private boolean tilt;
   private double ty;
   private double tc1 = 1.0;
   private double ts1;
   private double tc2 = 1.0;
   private double ts2;
   public int tint = -1;
   public int alpha = 255;

   public Shapes(Render3D var1) {
      this.r = var1;
   }

   public void begin(class_243 var1, float var2, double var3) {
      this.ox = var1.field_1352;
      this.oy = var1.field_1351;
      this.oz = var1.field_1350;
      double var5 = Math.toRadians(var2);
      this.cy = Math.cos(var5);
      this.sy = Math.sin(var5);
      this.scale = var3;
      this.part = false;
      this.tilt = false;
   }

   public void tilt(double var1, double var3, double var5) {
      this.tilt = var3 != 0.0 || var5 != 0.0;
      this.ty = var1;
      this.tc1 = Math.cos(Math.toRadians(var3));
      this.ts1 = Math.sin(Math.toRadians(var3));
      this.tc2 = Math.cos(Math.toRadians(var5));
      this.ts2 = Math.sin(Math.toRadians(var5));
   }

   public void part(double var1, double var3, double var5, double var7, double var9, double var11) {
      this.part = true;
      this.px = var1;
      this.py = var3;
      this.pz = var5;
      this.c1 = Math.cos(Math.toRadians(var11));
      this.s1 = Math.sin(Math.toRadians(var11));
      this.c2 = Math.cos(Math.toRadians(var7));
      this.s2 = Math.sin(Math.toRadians(var7));
      this.c3 = Math.cos(Math.toRadians(var9));
      this.s3 = Math.sin(Math.toRadians(var9));
   }

   public void endPart() {
      this.part = false;
   }

   private Vector3f v(double var1, double var3, double var5) {
      if (this.part) {
         double var7 = var1 - this.px;
         double var9 = var3 - this.py;
         double var11 = var5 - this.pz;
         double var13 = var7 * this.c1 - var9 * this.s1;
         double var15 = var7 * this.s1 + var9 * this.c1;
         double var17 = var15 * this.c2 - var11 * this.s2;
         double var19 = var15 * this.s2 + var11 * this.c2;
         double var21 = var13 * this.c3 + var19 * this.s3;
         double var23 = -var13 * this.s3 + var19 * this.c3;
         var1 = var21 + this.px;
         var3 = var17 + this.py;
         var5 = var23 + this.pz;
      }

      if (this.tilt) {
         double var25 = var3 - this.ty;
         double var27 = var1 * this.tc2 - var25 * this.ts2;
         double var29 = var1 * this.ts2 + var25 * this.tc2;
         double var31 = var29 * this.tc1 - var5 * this.ts1;
         double var33 = var29 * this.ts1 + var5 * this.tc1;
         var1 = var27;
         var3 = var31 + this.ty;
         var5 = var33;
      }

      double var26 = var1 * this.scale;
      double var28 = var3 * this.scale;
      double var30 = var5 * this.scale;
      double var32 = var26 * this.cy - var30 * this.sy;
      double var34 = var26 * this.sy + var30 * this.cy;
      class_243 var35 = this.r.camera();
      return new Vector3f((float)(this.ox + var32 - var35.field_1352), (float)(this.oy + var28 - var35.field_1351), (float)(this.oz + var34 - var35.field_1350));
   }

   public class_243 world(double var1, double var3, double var5) {
      Vector3f var7 = this.v(var1, var3, var5);
      class_243 var8 = this.r.camera();
      return new class_243(var7.x + var8.field_1352, var7.y + var8.field_1351, var7.z + var8.field_1350);
   }

   private int c(int var1, float var2) {
      int var3 = (int)((var1 >> 16 & 0xFF) * var2);
      int var4 = (int)((var1 >> 8 & 0xFF) * var2);
      int var5 = (int)((var1 & 0xFF) * var2);
      if (this.tint != -1) {
         var3 = var3 * (this.tint >> 16 & 0xFF) / 255;
         var4 = var4 * (this.tint >> 8 & 0xFF) / 255;
         var5 = var5 * (this.tint & 0xFF) / 255;
      }

      int var6 = (var1 >>> 24) * this.alpha / 255;
      return Math.min(255, var6) << 24 | Math.min(255, var3) << 16 | Math.min(255, var4) << 8 | Math.min(255, var5);
   }

   public void quad(double[] var1, double[] var2, double[] var3, double[] var4, int var5, float var6) {
      int var7 = this.c(var5, var6);
      this.r
         .skyQuad(
            this.v(var1[0], var1[1], var1[2]),
            this.v(var2[0], var2[1], var2[2]),
            this.v(var3[0], var3[1], var3[2]),
            this.v(var4[0], var4[1], var4[2]),
            var7,
            var7,
            var7,
            var7
         );
   }

   public void tri(double[] var1, double[] var2, double[] var3, int var4, float var5) {
      this.quad(var1, var2, var3, var3, var4, var5);
   }

   private static double[] p(double var0, double var2, double var4) {
      return new double[]{var0, var2, var4};
   }

   public void box(double var1, double var3, double var5, double var7, double var9, double var11, int var13) {
      double var14 = var1 - var7;
      double var16 = var1 + var7;
      double var18 = var3 - var9;
      double var20 = var3 + var9;
      double var22 = var5 - var11;
      double var24 = var5 + var11;
      this.quad(p(var14, var20, var22), p(var16, var20, var22), p(var16, var20, var24), p(var14, var20, var24), var13, 1.0F);
      this.quad(p(var14, var18, var22), p(var16, var18, var22), p(var16, var18, var24), p(var14, var18, var24), var13, 0.55F);
      this.quad(p(var14, var18, var24), p(var16, var18, var24), p(var16, var20, var24), p(var14, var20, var24), var13, 0.9F);
      this.quad(p(var14, var18, var22), p(var16, var18, var22), p(var16, var20, var22), p(var14, var20, var22), var13, 0.8F);
      this.quad(p(var14, var18, var22), p(var14, var18, var24), p(var14, var20, var24), p(var14, var20, var22), var13, 0.7F);
      this.quad(p(var16, var18, var22), p(var16, var18, var24), p(var16, var20, var24), p(var16, var20, var22), var13, 0.7F);
   }

   public void sphere(double var1, double var3, double var5, double var7, double var9, double var11, int var13, int var14) {
      this.sphere(var1, var3, var5, var7, var9, var11, var13, var14, 0);
   }

   public void dome(double var1, double var3, double var5, double var7, double var9, double var11, int var13, int var14) {
      this.sphere(var1, var3, var5, var7, var9, var11, var13, var14, 4);
   }

   private void sphere(double var1, double var3, double var5, double var7, double var9, double var11, int var13, int var14, int var15) {
      int var16 = var15 > 0 ? 8 : 7;
      byte var17 = 14;

      for (int var18 = var15; var18 < var16; var18++) {
         double var19 = Math.PI * var18 / var16 - (Math.PI / 2);
         double var21 = Math.PI * (var18 + 1) / var16 - (Math.PI / 2);
         float var23 = 0.55F + 0.45F * (var18 + 1) / var16;
         int var24 = var15 > 0 ? (var18 >= 6 ? var13 : var14) : (var18 >= var16 / 2 ? var13 : var14);

         for (int var25 = 0; var25 < var17; var25++) {
            double var26 = (Math.PI * 2) * var25 / var17;
            double var28 = (Math.PI * 2) * (var25 + 1) / var17;
            this.quad(
               this.onSphere(var1, var3, var5, var7, var9, var11, var19, var26),
               this.onSphere(var1, var3, var5, var7, var9, var11, var19, var28),
               this.onSphere(var1, var3, var5, var7, var9, var11, var21, var28),
               this.onSphere(var1, var3, var5, var7, var9, var11, var21, var26),
               var24,
               var23
            );
         }
      }
   }

   private double[] onSphere(double var1, double var3, double var5, double var7, double var9, double var11, double var13, double var15) {
      return p(var1 + Math.cos(var13) * Math.sin(var15) * var7, var3 + Math.sin(var13) * var9, var5 + Math.cos(var13) * Math.cos(var15) * var11);
   }

   public void cylinder(double var1, double var3, double var5, double var7, double var9, double var11, int var13, boolean var14) {
      byte var15 = 16;

      for (int var16 = 0; var16 < var15; var16++) {
         double var17 = (Math.PI * 2) * var16 / var15;
         double var19 = (Math.PI * 2) * (var16 + 1) / var15;
         double[] var21 = p(var1 + Math.sin(var17) * var7, var3, var5 + Math.cos(var17) * var7);
         double[] var22 = p(var1 + Math.sin(var19) * var7, var3, var5 + Math.cos(var19) * var7);
         double[] var23 = p(var1 + Math.sin(var19) * var9, var3 + var11, var5 + Math.cos(var19) * var9);
         double[] var24 = p(var1 + Math.sin(var17) * var9, var3 + var11, var5 + Math.cos(var17) * var9);
         this.quad(var21, var22, var23, var24, var13, 0.75F + 0.25F * (float)Math.abs(Math.cos(var17)));
         if (var14) {
            this.tri(var24, var23, p(var1, var3 + var11, var5), var13, 1.0F);
            this.tri(var21, var22, p(var1, var3, var5), var13, 0.55F);
         }
      }
   }

   public void disc(double var1, double var3, double var5, double var7, int var9) {
      byte var10 = 16;

      for (int var11 = 0; var11 < var10; var11++) {
         double var12 = (Math.PI * 2) * var11 / var10;
         double var14 = (Math.PI * 2) * (var11 + 1) / var10;
         this.tri(
            p(var1, var3, var5),
            p(var1 + Math.sin(var12) * var7, var3, var5 + Math.cos(var12) * var7),
            p(var1 + Math.sin(var14) * var7, var3, var5 + Math.cos(var14) * var7),
            var9,
            1.0F
         );
      }
   }

   public void ring(double var1, double var3, double var5, double var7, double var9, int var11) {
      byte var12 = 24;

      for (int var13 = 0; var13 < var12; var13++) {
         double var14 = (Math.PI * 2) * var13 / var12;
         double var16 = (Math.PI * 2) * (var13 + 1) / var12;
         this.quad(
            p(var1 + Math.sin(var14) * var7, var3, var5 + Math.cos(var14) * var7),
            p(var1 + Math.sin(var16) * var7, var3, var5 + Math.cos(var16) * var7),
            p(var1 + Math.sin(var16) * var9, var3, var5 + Math.cos(var16) * var9),
            p(var1 + Math.sin(var14) * var9, var3, var5 + Math.cos(var14) * var9),
            var11,
            1.0F
         );
      }
   }

   public void rod(double var1, double var3, double var5, double var7, double var9, double var11, double var13, int var15) {
      double var16 = var7 - var1;
      double var18 = var9 - var3;
      double var20 = var11 - var5;
      double var22 = Math.sqrt(var16 * var16 + var18 * var18 + var20 * var20);
      if (!(var22 < 1.0E-6)) {
         double var24 = 0.0;
         double var26 = 1.0;
         double var28 = 0.0;
         if (Math.abs(var18 / var22) > 0.9) {
            var24 = 1.0;
            var26 = 0.0;
         }

         double var30 = var18 * var28 - var20 * var26;
         double var32 = var20 * var24 - var16 * var28;
         double var34 = var16 * var26 - var18 * var24;
         double var36 = Math.sqrt(var30 * var30 + var32 * var32 + var34 * var34);
         var30 = var30 / var36 * var13;
         var32 = var32 / var36 * var13;
         var34 = var34 / var36 * var13;
         double var38 = (var18 * var34 - var20 * var32) / var22;
         double var40 = (var20 * var30 - var16 * var34) / var22;
         double var42 = (var16 * var32 - var18 * var30) / var22;
         double[][] var44 = new double[][]{{var30, var32, var34}, {var38, var40, var42}, {-var30, -var32, -var34}, {-var38, -var40, -var42}};

         for (int var45 = 0; var45 < 4; var45++) {
            double[] var46 = var44[var45];
            double[] var47 = var44[(var45 + 1) % 4];
            this.quad(
               p(var1 + var46[0], var3 + var46[1], var5 + var46[2]),
               p(var1 + var47[0], var3 + var47[1], var5 + var47[2]),
               p(var7 + var47[0], var9 + var47[1], var11 + var47[2]),
               p(var7 + var46[0], var9 + var46[1], var11 + var46[2]),
               var15,
               var45 % 2 == 0 ? 0.85F : 0.7F
            );
         }
      }
   }

   public void line(double var1, double var3, double var5, double var7, double var9, double var11, int var13) {
      this.r.line(this.world(var1, var3, var5), this.world(var7, var9, var11), var13, false);
   }
}
