package dev.dihclient.module;

public enum Category {
   COMBAT("Combat"),
   MOVEMENT("Movement"),
   PLAYER("Player"),
   RENDER("Render"),
   WORLD("World"),
   AUTOMATION("Automation"),
   BASEFINDING("Basefinding"),
   MISC("Misc"),
   FUN("Fun"),
   CLIENT("Client");

   public final String title;

   private Category(String nullxx) {
      this.title = nullxx;
   }
}
