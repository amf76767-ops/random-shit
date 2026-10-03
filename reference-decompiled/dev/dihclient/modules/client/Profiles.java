package dev.dihclient.modules.client;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.util.ConfigShare;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.List;

public class Profiles extends Module {
   public Profiles() {
      super("Profiles", Category.CLIENT, "Save and load three complete configuration profiles, share your config with friends.");
      this.action(
         "Share Config",
         "Copies a code with all your changed settings to the clipboard (also saved in dihclient/shared). Keybinds and secrets are not included.",
         ConfigShare::export
      );
      this.action(
         "Import Settings",
         "Applies a DIH1:… code from the clipboard (settings only, modules stay on / off as they are).",
         () -> ConfigShare.importClipboard(false)
      );
      this.action("Import Everything", "Applies a code from the clipboard including which modules are on.", () -> ConfigShare.importClipboard(true));

      for (int var1 = 1; var1 <= 3; var1++) {
         int var2 = var1;
         this.action("Save " + var2, "Saves the current configuration to slot " + var2 + ".", () -> {
            boolean var1x = DIHClient.config().saveProfile(var2);
            Notifications.push("Profiles", var1x ? "Saved profile " + var2 : "Saving failed", var1x ? Notifications.Type.SUCCESS : Notifications.Type.ERROR);
         });
         this.action(
            "Load " + var2,
            "Loads slot " + var2 + ".",
            () -> {
               boolean var1x = DIHClient.config().loadProfile(var2);
               Notifications.push(
                  "Profiles",
                  var1x ? "Loaded profile " + var2 : "Profile " + var2 + " is empty",
                  var1x ? Notifications.Type.SUCCESS : Notifications.Type.WARNING
               );
            }
         );
      }
   }

   @Override
   public boolean isToggleable() {
      return false;
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();

      for (int var2 = 1; var2 <= 3; var2++) {
         var1.add("Slot " + var2 + ": " + (DIHClient.config().profileExists(var2) ? "saved" : "empty"));
      }

      return var1;
   }
}
