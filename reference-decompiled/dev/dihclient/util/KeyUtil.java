package dev.dihclient.util;

import dev.dihclient.mixin.accessor.KeyBindingAccessor;
import java.util.Locale;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_3675;
import net.minecraft.class_3675.class_306;
import net.minecraft.class_3675.class_307;
import org.lwjgl.glfw.GLFW;

public final class KeyUtil {
   private KeyUtil() {
   }

   public static boolean isPhysicallyDown(class_304 var0) {
      class_310 var1 = class_310.method_1551();
      class_306 var2 = ((KeyBindingAccessor)var0).dih$getBoundKey();
      if (var2 != null && !var2.equals(class_3675.field_16237)) {
         long var3 = var1.method_22683().method_4490();
         return var2.method_1442() == class_307.field_1672
            ? GLFW.glfwGetMouseButton(var3, var2.method_1444()) == 1
            : class_3675.method_15987(var1.method_22683(), var2.method_1444());
      } else {
         return false;
      }
   }

   public static boolean isKeyDown(int var0) {
      return var0 < 0 ? false : class_3675.method_15987(class_310.method_1551().method_22683(), var0);
   }

   public static String keyName(int var0) {
      if (var0 < 0) {
         return "NONE";
      } else {
         String var1 = GLFW.glfwGetKeyName(var0, 0);
         if (var1 != null && !var1.isBlank()) {
            return var1.toUpperCase(Locale.ROOT);
         } else {
            return switch (var0) {
               case 32 -> "SPACE";
               case 257 -> "ENTER";
               case 258 -> "TAB";
               case 259 -> "BACK";
               case 260 -> "INS";
               case 261 -> "DEL";
               case 262 -> "RIGHT";
               case 263 -> "LEFT";
               case 264 -> "DOWN";
               case 265 -> "UP";
               case 266 -> "PGUP";
               case 267 -> "PGDN";
               case 268 -> "HOME";
               case 269 -> "END";
               case 280 -> "CAPS";
               case 340 -> "LSHIFT";
               case 341 -> "LCTRL";
               case 342 -> "LALT";
               case 344 -> "RSHIFT";
               case 345 -> "RCTRL";
               case 346 -> "RALT";
               default -> var0 >= 290 && var0 <= 314 ? "F" + (var0 - 290 + 1) : (var0 >= 320 && var0 <= 329 ? "NUM" + (var0 - 320) : "KEY" + var0);
            };
         }
      }
   }
}
