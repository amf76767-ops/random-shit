package dev.dihclient.scan;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.modules.client.Performance;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Compat;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.class_1923;
import net.minecraft.class_238;
import net.minecraft.class_2818;
import net.minecraft.class_2902;
import net.minecraft.class_2390;
import net.minecraft.class_243;
import net.minecraft.class_3532;

public abstract class ChunkMarkModule extends Module implements ChunkEvents.Listener {
   public final EnumSetting<ChunkMarkModule.Style> style = this.mode(
      "Style",
      "Glow: plate + fading light curtain + beam · Plate: flat square · Column: 4-block box · Pillar: tall beam · Outline: edges only · Full: whole chunk.",
      ChunkMarkModule.Style.GLOW
   );
   public final EnumSetting<ChunkMarkModule.Height> heightMode = this.mode(
      "Height", "Auto: where the evidence was found · Fixed: at Plate Y · Player: at your own height.", ChunkMarkModule.Height.AUTO
   );
   public final IntSetting plateY = this.integer("Plate Y", "Height of the marker when Height = Fixed.", 60, -64, 320)
      .visibleWhen(() -> this.heightMode.get() == ChunkMarkModule.Height.FIXED);
   public final IntSetting opacity = this.integer("Opacity", "Fill opacity of the marker.", 110, 0, 255);
   public final BoolSetting pulse = this.bool("Pulse", "Lets the marker glow in and out.", true);
   public final BoolSetting beam = this.bool("Beam", "Thin light beam in the middle of marked chunks, visible from far away (Glow style).", true)
      .visibleWhen(() -> this.style.get() == ChunkMarkModule.Style.GLOW);
   public final BoolSetting throughWalls = this.bool("Through Walls", "Markers stay visible behind terrain.", true);
   public final BoolSetting fadeInside = this.bool("Fade Inside", "Marker gets see-through while you stand in that chunk.", true);
   public final BoolSetting tracers = this.bool("Tracers", "Draws lines to marked chunks.", false);
   public final BoolSetting labels = this.bool(
      "Show Text", "Text above the marked area (what was found). Off = only the coloured marker, no text at all.", true
   );
   public final BoolSetting labelDetails = this.bool("Text Details", "Second, smaller line with the details (e.g. 12 kelp · 4 vines).", true)
      .visibleWhen(this.labels::get);
   public final BoolSetting labelDistance = this.bool("Text Distance", "Adds the distance in metres to the text.", true).visibleWhen(this.labels::get);
   public final IntSetting labelRange = this.integer("Text Range", "Only show the text for marks closer than this (blocks). 0 = always.", 0, 0, 2048)
      .visibleWhen(this.labels::get);
   public final DoubleSetting labelScale = this.dbl("Text Size", "Size of the text.", 1.0, 0.4, 3.0, 0.1).visibleWhen(this.labels::get);
   public final EnumSetting<ChunkMarkModule.Particles> particleMode = this.mode(
      "Particles",
      "Strip: a solid vertical strip of particles in the middle of every marked chunk (always visible, also with VulkanMod) · Off: no particles at all · Auto: only when Performance → Marker Particles / safe mode is on.",
      ChunkMarkModule.Particles.AUTO
   );
   public final BoolSetting notify = this.bool("Notify", "Toast when a new chunk gets marked.", true);
   public final BoolSetting showDistance = this.bool("Show Distance", "Adds direction and distance to the toast (no coordinates).", true)
      .visibleWhen(this.notify::get);
   private long lastAnnounce;
   private int suppressed;
   private long lastParticles;
   private final Map<Long, String[]> labelCache = new HashMap<>();
   private long labelCacheTime;
   private static final long LABEL_CACHE_MS = 250L;

   protected ChunkMarkModule(String var1, Category var2, String var3) {
      super(var1, var2, var3);
   }

   public interface FriendDrawer {
      boolean draw(Render3D r, double x, double y, double z, double height);
   }

   public static volatile FriendDrawer friendDrawer;

   protected boolean showsFriend() {
      return false;
   }

   public abstract Map<Long, Integer> chunkMarks();

   protected String chunkLabel(long var1) {
      return null;
   }

   protected String chunkSubLabel(long var1) {
      return null;
   }

   protected double chunkY(long var1) {
      return Double.NaN;
   }

   protected float chunkIntensity(long var1) {
      return 1.0F;
   }

   public String markLabel() {
      return this.name();
   }

   protected void announce(long var1, String var3) {
      if (this.notify.get() && mc.field_1724 != null) {
         long var4 = System.currentTimeMillis();
         if (var4 - this.lastAnnounce < 2000L) {
            this.suppressed++;
         } else {
            this.lastAnnounce = var4;
            String var6 = var3;
            if (this.suppressed > 0) {
               var6 = var3 + " (+" + this.suppressed + " more)";
               this.suppressed = 0;
            }

            if (this.showDistance.get()) {
               double var7 = (class_1923.method_8325(var1) << 4) + 8;
               double var9 = (class_1923.method_8332(var1) << 4) + 8;
               double var11 = var7 - mc.field_1724.method_23317();
               double var13 = var9 - mc.field_1724.method_23321();
               int var15 = (int)Math.sqrt(var11 * var11 + var13 * var13);
               var6 = var6 + " · " + direction(var11, var13) + " " + var15 + "m";
            }

            Notifications.warn(this.name(), var6);
         }
      }
   }

   protected static String direction(double var0, double var2) {
      String[] var4 = new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
      double var5 = Math.toDegrees(Math.atan2(-var0, var2));
      return var4[Math.floorMod(Math.round((float)(var5 / 45.0)), 8)];
   }

   private void particles(Map<Long, Integer> var1) {
      Performance var2 = Compat.perf();

      boolean var3 = switch ((ChunkMarkModule.Particles)this.particleMode.get()) {
         case AUTO -> var2 != null && var2.particles();
         case STRIP -> true;
         case OFF -> false;
      };
      if (var3) {
         long var4 = System.currentTimeMillis();
         if (var4 - this.lastParticles >= 500L) {
            this.lastParticles = var4;
            double var6 = mc.field_1724.method_23317();
            double var8 = mc.field_1724.method_23321();
            double var10 = mc.field_1724.method_23318();
            ArrayList<Entry<Long, Integer>> var12 = new ArrayList<>(var1.entrySet());
            var12.sort(Comparator.comparingDouble(var4x -> {
               double var5 = (class_1923.method_8325((Long)var4x.getKey()) << 4) + 8 - var6;
               double var7 = (class_1923.method_8332((Long)var4x.getKey()) << 4) + 8 - var8;
               return var5 * var5 + var7 * var7;
            }));
            int var13 = 0;

            for (Entry<Long, Integer> var15 : var12) {
               if (var13++ >= 24) {
                  break;
               }

               double var16 = (class_1923.method_8325((Long)var15.getKey()) << 4) + 8;
               double var18 = (class_1923.method_8332((Long)var15.getKey()) << 4) + 8;
               class_2390 var20 = new class_2390((Integer)var15.getValue() & 16777215, 2.5F);

               for (int var21 = 0; var21 < 40; var21++) {
                  mc.field_1687.method_8466(var20, true, true, var16, var10 - 10.0 + var21 * 0.9, var18, 0.0, 0.0, 0.0);
               }
            }
         }
      }
   }

   private String[] labelsFor(long var1) {
      long var3 = System.currentTimeMillis();
      if (var3 - this.labelCacheTime > 250L) {
         this.labelCache.clear();
         this.labelCacheTime = var3;
      }

      String[] var5 = this.labelCache.get(var1);
      if (var5 == null) {
         String var6 = this.chunkLabel(var1);
         var5 = new String[]{var6, var6 == null ? null : this.chunkSubLabel(var1)};
         this.labelCache.put(var1, var5);
      }

      return var5;
   }

   private double baseY(long var1, double var3) {
      switch ((ChunkMarkModule.Height)this.heightMode.get()) {
         case FIXED:
            return this.plateY.get().intValue();
         case AUTO:
            double var5 = this.chunkY(var1);
            if (!Double.isNaN(var5)) {
               return Math.floor(var5);
            }
         default:
            return var3;
      }
   }

   private static int alpha(int var0, int var1) {
      return var0 & 16777215 | Math.max(0, Math.min(255, var1)) << 24;
   }

   private void glow(Render3D var1, int var2, int var3, double var4, int var6, int var7, float var8, boolean var9) {
      double var10 = var2;
      double var12 = var3;
      double var14 = var2 + 16;
      double var16 = var3 + 16;
      var1.boxFilled(new class_238(var10, var4, var12, var14, var4 + 0.06, var16), alpha(var6, (int)(var7 * 0.55F)), var9);
      var1.boxOutline(new class_238(var10, var4, var12, var14, var4 + 0.06, var16), alpha(var6, 230), var9);
      double var18 = 3.0 + 9.0 * var8;
      byte var20 = 7;
      double var21 = 0.04;

      for (int var23 = 0; var23 < var20; var23++) {
         double var24 = var4 + var18 * var23 / var20;
         double var26 = var4 + var18 * (var23 + 1) / var20;
         float var28 = 1.0F - (float)var23 / var20;
         int var29 = alpha(var6, (int)(var7 * 0.7F * var28 * var28));
         var1.boxFilled(new class_238(var10, var24, var12, var14, var26, var12 + var21), var29, var9);
         var1.boxFilled(new class_238(var10, var24, var16 - var21, var14, var26, var16), var29, var9);
         var1.boxFilled(new class_238(var10, var24, var12, var10 + var21, var26, var16), var29, var9);
         var1.boxFilled(new class_238(var14 - var21, var24, var12, var14, var26, var16), var29, var9);
      }

      int var30 = alpha(var6, 200);
      var1.line(var10, var4, var12, var10, var4 + var18, var12, var30, var9);
      var1.line(var14, var4, var12, var14, var4 + var18, var12, var30, var9);
      var1.line(var10, var4, var16, var10, var4 + var18, var16, var30, var9);
      var1.line(var14, var4, var16, var14, var4 + var18, var16, var30, var9);
      if (this.beam.get()) {
         var1.boxFilled(
            new class_238(var10 + 7.7, var4, var12 + 7.7, var10 + 8.3, var4 + 48.0 + 40.0 * var8, var12 + 8.3), alpha(var6, (int)(var7 * 0.45F)), var9
         );
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      Map<Long, Integer> var2 = this.chunkMarks();
      if (!var2.isEmpty()) {
         this.particles(var2);
         float var3 = this.pulse.get() ? (float)(Math.sin(System.currentTimeMillis() / 350.0) * 0.5 + 0.5) : 1.0F;
         double var4 = Math.floor(class_3532.method_16436(var1.tickDelta(), mc.field_1724.field_5971, mc.field_1724.method_23318()));
         int var6 = (int)(this.opacity.get().intValue() * (this.pulse.get() ? 0.55F + 0.45F * var3 : 1.0F));
         boolean var7 = this.throughWalls.get();
         int var8 = mc.field_1724.method_31476().field_9181;
         int var9 = mc.field_1724.method_31476().field_9180;

         for (Entry<Long, Integer> var11 : var2.entrySet()) {
            long var12 = (Long)var11.getKey();
            int var14 = class_1923.method_8325(var12);
            int var15 = class_1923.method_8332(var12);
            int var16 = var14 << 4;
            int var17 = var15 << 4;
            int var18 = (Integer)var11.getValue() | 0xFF000000;
            double var19 = this.baseY(var12, var4);
            float var21 = Math.max(0.05F, Math.min(1.0F, this.chunkIntensity(var12)));
            boolean var22 = this.fadeInside.get() && var14 == var8 && var15 == var9;
            int var23 = var22 ? var6 / 4 : var6;

            class_238 var24 = switch ((ChunkMarkModule.Style)this.style.get()) {
               case PLATE -> new class_238(var16, var19, var17, var16 + 16, var19 + 0.15, var17 + 16);
               case COLUMN -> new class_238(var16, var19, var17, var16 + 16, var19 + 4.0, var17 + 16);
               case PILLAR -> new class_238(var16 + 6, var19, var17 + 6, var16 + 10, var19 + 120.0, var17 + 10);
               case OUTLINE -> new class_238(var16, var19, var17, var16 + 16, var19 + 1.0, var17 + 16);
               case FULL -> new class_238(
                  var16, mc.field_1687.method_31607(), var17, var16 + 16, mc.field_1687.method_31607() + mc.field_1687.method_31605(), var17 + 16
               );
               case GLOW -> new class_238(var16, var19, var17, var16 + 16, var19 + 12.0 + (this.beam.get() ? 88.0 : 0.0), var17 + 16);
            };
            if (var1.visible(var24, 0.0)) {
               double friendY = var19 + 1.0;
               boolean friendShown = false;
               if (this.showsFriend()) {
                  class_2818 friendChunk = mc.field_1687.method_2935().method_21730(var14, var15);
                  if (friendChunk != null) {
                     friendY = Math.max(friendY, friendChunk.method_12005(class_2902.class_2903.field_13197, 8, 8) + 0.5);
                  }

                  friendShown = friendDrawer != null && friendDrawer.draw(var1, var16 + 8, friendY, var17 + 8, 6.0);
               }

               if (!friendShown) {
               switch ((ChunkMarkModule.Style)this.style.get()) {
                  case PILLAR:
                     var1.box(var24, var18, var23, var7);
                     var1.box(new class_238(var16, var19, var17, var16 + 16, var19 + 0.15, var17 + 16), var18, var23 / 2, var7);
                     break;
                  case OUTLINE:
                     var1.boxOutline(var24, var18, var7);
                     break;
                  case FULL:
                  default:
                     var1.box(var24, var18, var22 ? var23 / 2 : var23, var7);
                     break;
                  case GLOW:
                     this.glow(var1, var16, var17, var19, var18, var23, var21, var7);
               }
               }

               if (this.tracers.get()) {
                  var1.tracer(new class_243(var16 + 8, var19 + 0.5, var17 + 8), var18);
               }

               if (this.labels.get()) {
                  String[] var25 = this.labelsFor(var12);
                  String var26 = var25[0];
                  if (var26 != null) {
                     double var27 = friendShown ? friendY + 8.5 : switch ((ChunkMarkModule.Style)this.style.get()) {
                        case PILLAR -> var19 + 122.0;
                        default -> var19 + 5.0;
                        case FULL -> mc.field_1724.method_23320() + 3.0;
                        case GLOW -> var19 + 4.0 + 9.0 * var21;
                     };
                     class_243 var29 = new class_243(var16 + 8, var27, var17 + 8);
                     double var30 = var1.camera().method_1022(var29);
                     if (this.labelRange.get() <= 0 || !(var30 > this.labelRange.get().intValue())) {
                        float var32 = (float)(Math.max(1.2, var30 / 9.0) * this.labelScale.get());
                        var1.text(this.labelDistance.get() ? var26 + " · " + (int)var30 + "m" : var26, var29, var18, var32);
                        String var33 = this.labelDetails.get() ? var25[1] : null;
                        if (var33 != null) {
                           var1.text(var33, var29.method_1031(0.0, -0.32 * var32, 0.0), -2565928, var32 * 0.7F);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public String getInfo() {
      return Integer.toString(this.chunkMarks().size());
   }

   public static enum Height {
      FIXED,
      PLAYER,
      AUTO;
   }

   public static enum Particles {
      AUTO,
      STRIP,
      OFF;
   }

   public static enum Style {
      PLATE,
      COLUMN,
      PILLAR,
      OUTLINE,
      FULL,
      GLOW;
   }
}
