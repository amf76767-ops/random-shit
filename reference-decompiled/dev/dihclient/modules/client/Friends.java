package dev.dihclient.modules.client;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.social.SocialManager;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1657;
import net.minecraft.class_3966;

public class Friends extends Module {
   public Friends() {
      super("Friends", Category.CLIENT, "Friends are ignored by combat modules. Bind a key to toggle the player under your crosshair.");
      this.action("Clear", "Removes all friends.", () -> DIHClient.social().clearFriends());
   }

   @Override
   public boolean isActionModule() {
      return true;
   }

   @Override
   public boolean isToggleable() {
      return false;
   }

   @Override
   public void onAction() {
      if (mc.field_1765 instanceof class_3966 var1 && var1.method_17782() instanceof class_1657 var2) {
         String var6 = SocialManager.name(var2);
         boolean var4 = DIHClient.social().toggleFriend(var6);
         Notifications.info("Friends", var6 + (var4 ? " added" : " removed"));
      } else {
         Notifications.warn("Friends", "Look at a player first");
      }
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList<>(DIHClient.social().friends());
      if (var1.isEmpty()) {
         var1.add("No friends yet.");
      }

      return var1;
   }
}
