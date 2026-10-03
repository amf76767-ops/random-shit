package dev.dihclient.modules.fun;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_5250;
import net.minecraft.class_5251;

public class UwuChat extends Module {
   public final EnumSetting<UwuChat.Mode> mode = this.mode("Mode", "UwU speak, reversed text, rainbow colours or both UwU + rainbow.", UwuChat.Mode.UWU);
   public final BoolSetting faces = this.bool("Faces", "Adds kaomoji like (◕ᴗ◕✿) and uwu at the end.", true);
   public final BoolSetting stutter = this.bool("Stutter", "S-s-sometimes stutters at the start of words.", true);
   private static final String[] FACES = new String[]{" uwu", " owo", " >w<", " (◕ᴗ◕✿)", " ^w^", " (｡♥‿♥｡)", " :3", " rawr x3", " (*^ω^*)", " nyaa~"};

   public UwuChat() {
      super("UwU Chat", Category.FUN, "Turns incoming chat into UwU speak (or reverse / rainbow). Only you see it.");
   }

   public static class_2561 transformIncoming(class_2561 var0) {
      if (ModuleManager.on(UwuChat.class) && var0 != null) {
         String var1 = var0.getString();
         return var1.contains("[DIH]") ? var0 : ModuleManager.of(UwuChat.class).transform(var0);
      } else {
         return var0;
      }
   }

   private class_2561 transform(class_2561 var1) {
      ArrayList var2 = new ArrayList();
      ArrayList var3 = new ArrayList();
      var1.method_27658((var2x, var3x) -> {
         var2.add(var3x);
         var3.add(var2x);
         return Optional.empty();
      }, class_2583.field_24360);
      UwuChat.Mode var4 = this.mode.get();
      if (var4 == UwuChat.Mode.REVERSE) {
         class_5250 var14 = class_2561.method_43473();

         for (int var15 = var2.size() - 1; var15 >= 0; var15--) {
            var14.method_10852(
               class_2561.method_43470(new StringBuilder((String)var2.get(var15)).reverse().toString()).method_10862((class_2583)var3.get(var15))
            );
         }

         return var14;
      } else {
         boolean var5 = var4 == UwuChat.Mode.UWU || var4 == UwuChat.Mode.UWU_RAINBOW;
         boolean var6 = var4 == UwuChat.Mode.RAINBOW || var4 == UwuChat.Mode.UWU_RAINBOW;
         class_5250 var7 = class_2561.method_43473();
         int var8 = 0;

         for (int var9 = 0; var9 < var2.size(); var9++) {
            String var10 = var5 ? this.uwuify((String)var2.get(var9)) : (String)var2.get(var9);
            if (!var6) {
               var7.method_10852(class_2561.method_43470(var10).method_10862((class_2583)var3.get(var9)));
            } else {
               for (int var11 = 0; var11 < var10.length(); var11++) {
                  float var12 = var8++ * 0.04F % 1.0F;
                  int var13 = Color.HSBtoRGB(var12, 0.6F, 1.0F) & 16777215;
                  var7.method_10852(
                     class_2561.method_43470(String.valueOf(var10.charAt(var11)))
                        .method_10862(((class_2583)var3.get(var9)).method_27703(class_5251.method_27717(var13)))
                  );
               }
            }
         }

         if (var5 && this.faces.get()) {
            String var16 = FACES[Math.floorMod(var1.getString().hashCode(), FACES.length)];
            var7.method_10852(class_2561.method_43470(var16).method_10862(class_2583.field_24360.method_27703(class_5251.method_27717(16751317))));
         }

         return var7;
      }
   }

   private String uwuify(String var1) {
      StringBuilder var2 = new StringBuilder(var1.length() + 8);
      boolean var3 = true;

      for (int var4 = 0; var4 < var1.length(); var4++) {
         char var5 = var1.charAt(var4);
         char var6 = var4 + 1 < var1.length() ? var1.charAt(var4 + 1) : 32;
         if (var3 && this.stutter.get() && Character.isLetter(var5) && ThreadLocalRandom.current().nextInt(9) == 0) {
            var2.append(var5).append('-');
         }

         var3 = !Character.isLetterOrDigit(var5);
         switch (var5) {
            case 'L':
            case 'R':
               var2.append('W');
               break;
            case 'N':
            case 'n':
               var2.append(var5);
               if ("aeiouAEIOU".indexOf(var6) >= 0) {
                  var2.append((char)(Character.isUpperCase(var5) ? 'Y' : 'y'));
               }
               break;
            case 'l':
            case 'r':
               var2.append('w');
               break;
            default:
               var2.append(var5);
         }
      }

      return var2.toString().replace("ove", "uv").replace("th", "d");
   }

   public static enum Mode {
      UWU,
      REVERSE,
      RAINBOW,
      UWU_RAINBOW;
   }
}
