package dev.dihclient.modules.client;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;

public class NotificationsModule extends Module {
   public final BoolSetting toggles = this.bool("Module Toggles", "Shows a toast when a module is toggled.", true).legacy("notifications");
   public final BoolSetting chat = this.bool("Chat Echo", "Also prints notifications into the chat (only you see them).", false);

   public NotificationsModule() {
      super("Notifications", Category.CLIENT, "Toast notifications for toggles and alerts.");
   }
}
