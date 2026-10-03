package dev.dihclient.port.tools;

import dev.dihclient.DIHClient;
import dev.dihclient.mixin.port.HoveredSlotMixin;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1802;
import net.minecraft.class_490;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * With the inventory open, a totem under the mouse is swapped into the offhand (or, with Hotbar Refill, into one hotbar slot).
 */
public class HoverTotem extends Module {
    public final BoolSetting hotbarRefill = this.bool("Hotbar Refill", "Also swaps a hovered totem into the hotbar slot below once the offhand has one.", false);
    public final IntSetting hotbarSlot = this.integer("Hotbar Slot", "Hotbar slot (1-9) that gets the totem.", 1, 1, 9).visibleWhen(this.hotbarRefill::get);

    private boolean broken;

    public HoverTotem() {
        super("Hover Totem", Category.COMBAT, "Swaps a hovered inventory totem into the offhand or selected hotbar slot.");
    }

    @Override
    protected void onEnable() {
        this.broken = false;
    }

    @Override
    public void onTick() {
        if (this.broken || mc.field_1724 == null || mc.field_1761 == null || !(mc.field_1755 instanceof class_490 screen)) {
            return;
        }
        try {
            class_1735 hovered = ((HoveredSlotMixin) screen).dih$hoveredSlot();
            if (hovered == null || !hovered.method_7677().method_31574(class_1802.field_8288)) {
                return;
            }
            int slot = this.hotbarSlot.get();
            int button = ToolsLogic.hoverSwapButton(
                mc.field_1724.method_6079().method_31574(class_1802.field_8288),
                this.hotbarRefill.get(), slot,
                mc.field_1724.method_31548().method_5438(Math.max(1, Math.min(9, slot)) - 1).method_31574(class_1802.field_8288));
            if (button >= 0) {
                mc.field_1761.method_2906(screen.method_17577().field_7763, hovered.field_7874, button, class_1713.field_7791, mc.field_1724);
            }
        } catch (Throwable t) {
            // a changed accessor name must not take the game down
            this.broken = true;
            DIHClient.LOG.warn("[DIHClient] Hover Totem stopped after an error", t);
        }
    }
}
