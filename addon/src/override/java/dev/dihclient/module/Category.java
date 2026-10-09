package dev.dihclient.module;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    PLAYER("Player"),
    RENDER("Render"),
    WORLD("World"),
    AUTOMATION("Automation"),
    BASEFINDING("Basefinding"),
    DONUT("Donut"),
    MISC("Misc"),
    FUN("Fun"),
    CLIENT("Client"),
    DEBUG("Debug");

    public final String title;

    Category(String title) {
        this.title = title;
    }
}
