package dev.dihclient.account;

import dev.dihclient.DIHClient;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.class_310;

public final class SessionApplier {
   private static Field sessionField;
   private static Constructor<?> sessionCtor;
   private static Object msaAccountType;
   private static boolean searched;

   private SessionApplier() {
   }

   private static boolean isSessionCtor(Constructor<?> var0) {
      Class[] var1 = var0.getParameterTypes();
      return var1.length == 6
         && var1[0] == String.class
         && var1[1] == UUID.class
         && var1[2] == String.class
         && var1[3] == Optional.class
         && var1[4] == Optional.class
         && var1[5].isEnum();
   }

   private static void locate() {
      if (!searched) {
         searched = true;
         class_310 var0 = class_310.method_1551();

         for (Field var4 : var0.getClass().getDeclaredFields()) {
            Class var5 = var4.getType();

            for (Constructor var9 : var5.getDeclaredConstructors()) {
               if (isSessionCtor(var9)) {
                  sessionField = var4;
                  sessionCtor = var9;

                  for (Object var13 : var9.getParameterTypes()[5].getEnumConstants()) {
                     if ("MSA".equals(((Enum)var13).name())) {
                        msaAccountType = var13;
                     }
                  }

                  if (msaAccountType == null && var9.getParameterTypes()[5].getEnumConstants().length > 0) {
                     Object[] var14 = var9.getParameterTypes()[5].getEnumConstants();
                     msaAccountType = var14[var14.length - 1];
                  }

                  return;
               }
            }
         }
      }
   }

   public static String apply(String var0, UUID var1, String var2, String var3) {
      locate();
      if (sessionField != null && sessionCtor != null) {
         try {
            sessionCtor.setAccessible(true);
            Object var4 = sessionCtor.newInstance(var0, var1, var2, Optional.ofNullable(var3), Optional.empty(), msaAccountType);
            sessionField.setAccessible(true);
            unfinalize(sessionField);
            sessionField.set(class_310.method_1551(), var4);
            return null;
         } catch (Throwable var5) {
            DIHClient.LOG.error("[DIHClient] could not set session", var5);
            return var5.getClass().getSimpleName() + (var5.getMessage() == null ? "" : ": " + var5.getMessage());
         }
      } else {
         return "Session field not found in this version";
      }
   }

   private static void unfinalize(Field var0) {
      try {
         Field var1 = Field.class.getDeclaredField("modifiers");
         var1.setAccessible(true);
         var1.setInt(var0, var0.getModifiers() & -17);
      } catch (Throwable var2) {
      }
   }
}
