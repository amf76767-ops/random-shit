package dev.dihclient.hud.elements;

import dev.dihclient.hud.HudElement;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.render.Gfx;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import net.minecraft.class_332;
import net.minecraft.class_640;

public class WatermarkElement extends HudElement {
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
   private final ArrayList<String> parts = new ArrayList<>();
   private int fps;
   private String fpsText;
   private int ping;
   private String pingText;
   private long timeSecond;
   private String timeText;

   public WatermarkElement() {
      super("watermark", "Watermark", "watermark", 4, 4, () -> HudStyle.hud().watermark);
   }

   @Override
   public void render(class_332 var1, float var2, boolean var3) {
      String var4 = HudStyle.hud().clientName.get();
      if (var4.isBlank()) {
         var4 = "DIHClient";
      }

      ArrayList var5 = this.parts;
      var5.clear();
      int var6 = mc.method_47599();
      if (this.fpsText == null || var6 != this.fps) {
         this.fps = var6;
         this.fpsText = var6 + " fps";
      }

      var5.add(this.fpsText);
      int var7 = ping();
      if (var7 > 0) {
         if (this.pingText == null || var7 != this.ping) {
            this.ping = var7;
            this.pingText = var7 + " ms";
         }

         var5.add(this.pingText);
      }

      long var8 = System.currentTimeMillis() / 1000L;
      if (this.timeText == null || var8 != this.timeSecond) {
         this.timeSecond = var8;
         this.timeText = LocalTime.now().format(TIME);
      }

      var5.add(this.timeText);
      int var10 = Gfx.width(var4);
      int var11 = 8 + var10 + 4;

      for (String var13 : var5) {
         var11 += Gfx.width(var13) + 11;
      }

      this.width = var11 + 2;
      this.height = 16;
      HudStyle.accentPanel(var1, 0, 0, this.width, this.height);
      Gfx.accentText(var1, var4, 6, 4, 0.0);
      int var15 = 6 + var10 + 6;

      for (String var14 : var5) {
         var1.method_25294(var15, 5, var15 + 1, 11, 1090519039);
         var15 += 5;
         Gfx.text(var1, var14, var15, 4, -7564380);
         var15 += Gfx.width(var14) + 6;
      }
   }

   static int ping() {
      if (mc.method_1562() != null && mc.field_1724 != null) {
         class_640 var0 = mc.method_1562().method_2871(mc.field_1724.method_5667());
         return var0 == null ? 0 : var0.method_2959();
      } else {
         return 0;
      }
   }
}
