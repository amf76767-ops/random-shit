package dev.dihclient.module;

/**
 * The GUI categories. Same as in 5.6 plus {@link #DONUT} for the modules made for DonutSMP; it sits after Basefinding so the
 * GUI lists it next to it. Nothing keeps the position of a category (saved files use the name), so a new entry moves nothing.
 */
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
    CLIENT("Client");

    public final String title;

    Category(String title) {
        this.title = title;
    }
}
