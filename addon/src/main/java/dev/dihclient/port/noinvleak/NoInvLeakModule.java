package dev.dihclient.port.noinvleak;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import net.minecraft.class_1304;
import net.minecraft.class_1661;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_310;
import net.minecraft.class_746;

/**
 * Ported from an open-source client (GPL-3.0).
 * <p>
 * Hides the items of your own inventory, hotbar, cursor and armor HUD on screen: the game shows a fake item instead (a totem,
 * a random believable loadout, or nothing). With Stream Only (Windows) the real items are drawn into windows that screen
 * capture cannot see, so only the player sees them; the game window itself, which is what a capture records, shows the fakes.
 * <p>
 * The static hooks below are called from the mixins on every slot draw. They never throw: on any error the slot is shown
 * empty (hidden is the safe side for this module).
 */
public class NoInvLeakModule extends Module {
    public enum Style { TOTEM, RANDOM, EMPTY }

    private static volatile NoInvLeakModule active;
    /** The registered module, also while it is switched on but not yet enabled (config loaded before the world). */
    private static volatile NoInvLeakModule instance;
    private static class_1799 totem;
    private static boolean hookFailureLogged;

    public final EnumSetting<Style> style = this.mode("Style", "What the game shows instead of your items: a totem, a random loadout, or nothing.", Style.TOTEM);
    public final BoolSetting fillEmpty = this.bool("Fill Empty Slots", "Show a fake item in empty slots too, so nothing gives away what is empty.", true);
    public final BoolSetting streamOnly = this.bool("Stream Only", "Windows only: you still see your real items, screen capture sees the fakes.", false);
    public final BoolSetting borderless = this.bool("Borderless Fullscreen", "Fullscreen becomes a borderless window, so the capture-proof overlay stays on top of the game.", true);
    public final BoolSetting tooltips = this.bool("Hide Tooltips", "No item tooltip for your own slots (Stream Only draws it capture-proof).", true);
    public final BoolSetting itemName = this.bool("Hide Item Name", "No name of the held item above the hotbar (Stream Only draws it capture-proof).", true);
    public final BoolSetting selectedSlot = this.bool("Hide Selected Slot", "No frame around the selected hotbar slot (it would show which slot you hold).", true);
    public final BoolSetting hud = this.bool("DIH HUD", "The armor HUD element shows the fake items too.", true);

    private final FakeLoadout loadout = new FakeLoadout();

    public NoInvLeakModule() {
        super("No Inv Leak", Category.MISC, "Hides your items on screen for screen shares. Stream Only keeps them visible to you alone.");
        instance = this;
        this.fillEmpty.visibleWhen(() -> this.style.get() != Style.EMPTY);
        this.streamOnly.visibleWhen(Win32::isWindows);
        this.streamOnly.onChange(() -> {
            if (this.streamOnly.get()) {
                StreamOverlay.retry();
            } else {
                StreamOverlay.stop();
            }
            BorderlessFullscreen.tick();
        });
        this.borderless.visibleWhen(() -> Win32.isWindows() && this.streamOnly.get());
        this.borderless.onChange(BorderlessFullscreen::tick);
    }

    /** True while Stream Only wants the borderless window mode (read by {@link BorderlessFullscreen}). */
    static boolean wantsBorderless() {
        NoInvLeakModule module = instance;
        return module != null && module.isEnabled() && module.streamOnly.get() && module.borderless.get();
    }

    @Override
    protected void onEnable() {
        this.loadout.reseed();
        active = this;
        BorderlessFullscreen.tick();
    }

    @Override
    protected void onDisable() {
        active = null;
        StreamOverlay.stop();
        BorderlessFullscreen.tick();
    }

    @Override
    public void onTick() {
        BorderlessFullscreen.tick();
    }

    @Override
    public String getInfo() {
        return this.streamOnly.get() && StreamOverlay.usable() ? "Stream" : null;
    }

    // ---- hooks called by the mixins ----

    /** Hotbar slot draw: {@code seed} is vanilla's 1..9 for hotbar slots, 10 for the offhand. */
    public static class_1799 hotbar(class_1799 stack, int x, int y, int seed) {
        try {
            NoInvLeakModule module = active;
            if (module == null || stack == null) {
                return stack;
            }
            int slot = seed == 10 ? 40 : (seed >= 1 && seed <= 9 ? seed - 1 : 41);
            if (module.streamOverlaying()) {
                StreamOverlay.hotbar(slot, x, y, stack);
                return class_1799.field_8037;
            }
            return module.shown(stack, slot);
        } catch (Throwable t) {
            return hookFailed(t);
        }
    }

    /** Container screen slot draw, for slots of the player's own inventory only. */
    public static class_1799 container(class_1799 stack, class_1735 slot, int x, int y, boolean hovered) {
        try {
            NoInvLeakModule module = active;
            if (module == null || stack == null) {
                return stack;
            }
            if (module.streamOverlaying()) {
                StreamOverlay.container(slot.method_34266(), x, y, stack, hovered);
                return class_1799.field_8037;
            }
            return module.shown(stack, slot.method_34266());
        } catch (Throwable t) {
            return hookFailed(t);
        }
    }

    /** The stack on the cursor. */
    public static class_1799 carried(class_1799 stack, int x, int y) {
        try {
            NoInvLeakModule module = active;
            if (module == null || stack == null || stack.method_7960()) {
                return stack;
            }
            if (module.streamOverlaying()) {
                StreamOverlay.carried(stack, x, y);
                return class_1799.field_8037;
            }
            return module.fake(stack, 41);
        } catch (Throwable t) {
            return hookFailed(t);
        }
    }

    /** Armor HUD element. */
    public static class_1799 shownInHud(class_1799 stack, class_1304 slot) {
        try {
            NoInvLeakModule module = active;
            return module != null && module.hud.get() && stack != null && !stack.method_7960() ? module.fake(stack, inventorySlot(slot)) : stack;
        } catch (Throwable t) {
            return hookFailed(t);
        }
    }

    static class_1799 fakeFor(int slot) {
        NoInvLeakModule module = active;
        return module == null ? class_1799.field_8037 : module.fake(class_1799.field_8037, slot);
    }

    static boolean fakesWanted() {
        NoInvLeakModule module = active;
        return module != null && module.style.get() != Style.EMPTY;
    }

    static boolean fillsEmpty() {
        NoInvLeakModule module = active;
        return module != null && module.fillEmpty.get() && module.style.get() != Style.EMPTY;
    }

    /** True while the capture-proof overlay really takes over. */
    public static boolean overlaying() {
        NoInvLeakModule module = active;
        return module != null && module.streamOnly.get() && StreamOverlay.usable();
    }

    private boolean streamOverlaying() {
        return this.streamOnly.get() && StreamOverlay.usable();
    }

    /** True for a slot of the player's own inventory (not a chest, a crafting grid, ...). */
    public static boolean ownSlot(class_1735 slot) {
        return active != null && slot != null && slot.field_7871 instanceof class_1661;
    }

    public static boolean hidesTooltips() {
        NoInvLeakModule module = active;
        return module != null && module.tooltips.get();
    }

    public static boolean hidesItemName() {
        NoInvLeakModule module = active;
        return module != null && module.itemName.get();
    }

    public static boolean hidesSelectedSlot() {
        NoInvLeakModule module = active;
        return module != null && module.selectedSlot.get();
    }

    static boolean overlayWanted() {
        NoInvLeakModule module = active;
        return module != null && module.streamOnly.get();
    }

    static boolean opaqueCells() {
        NoInvLeakModule module = active;
        return module != null && module.style.get() != Style.EMPTY;
    }

    private class_1799 shown(class_1799 stack, int slot) {
        return stack.method_7960() && !this.fillEmpty.get() ? stack : this.fake(stack, slot);
    }

    private class_1799 fake(class_1799 stack, int slot) {
        switch (this.style.get()) {
            case EMPTY:
                return class_1799.field_8037;
            case RANDOM:
                return this.loadout.get(slot);
            default:
                class_1799 stand = totem;
                if (stand == null) {
                    totem = stand = new class_1799(class_1802.field_8288);
                }
                return stand;
        }
    }

    private static class_1799 hookFailed(Throwable t) {
        if (!hookFailureLogged) {
            hookFailureLogged = true;
            DIHClient.LOG.warn("[DIHClient] No Inv Leak hook failed, slot shown empty", t);
        }
        return class_1799.field_8037;
    }

    private static int inventorySlot(class_1304 slot) {
        return switch (slot) {
            case field_6169 -> 39;
            case field_6174 -> 38;
            case field_6172 -> 37;
            case field_6166 -> 36;
            case field_6171 -> 40;
            case field_6173 -> {
                class_746 player = class_310.method_1551().field_1724;
                yield player != null ? player.method_31548().method_67532() : 41;
            }
            default -> 41;
        };
    }
}
