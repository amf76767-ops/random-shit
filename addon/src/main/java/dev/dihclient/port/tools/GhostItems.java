package dev.dihclient.port.tools;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Notifications;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_1661;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

public class GhostItems extends Module {
    public final StringSetting item = this.text("Item", "Item id, e.g. minecraft:netherite_ingot.", "minecraft:netherite_ingot", 64);
    public final IntSetting count = this.integer("Count", "Stack size.", 64, 1, 99);
    public final IntSetting slot = this.integer("Slot", "Hotbar slot 1 to 9. 0 = the slot you hold.", 0, 0, 9);

    private final Map<Integer, class_1799> replaced = new HashMap<>();

    public GhostItems() {
        super("Ghost Items", Category.MISC, "Shows an item in your hotbar only on your screen. The server does not know it: you cannot use, drop, sell or give it, and the next inventory update from the server removes it.");
        this.action("Give", "Puts the item into the slot.", this::give);
        this.action("Clear", "Takes the ghost items away again.", this::clear);
    }

    @Override
    protected void onDisable() {
        this.clear();
    }

    @Override
    public void onWorldChange() {
        this.replaced.clear();
    }

    private void give() {
        if (mc.field_1724 == null) {
            return;
        }
        class_2960 id = class_2960.method_12829(this.item.get().trim());
        class_1792 it = id == null ? null : class_7923.field_41178.method_63535(id);
        if (it == null || it == class_1802.field_8162) {
            Notifications.warn(this.name(), "Unknown item: " + this.item.get());
            return;
        }
        class_1661 inv = mc.field_1724.method_31548();
        int s = this.slot.get() == 0 ? inv.method_67532() : this.slot.get() - 1;
        this.replaced.putIfAbsent(s, inv.method_5438(s).method_7972());
        inv.method_5447(s, new class_1799(it, this.count.get()));
    }

    private void clear() {
        if (mc.field_1724 != null) {
            class_1661 inv = mc.field_1724.method_31548();
            for (Map.Entry<Integer, class_1799> e : this.replaced.entrySet()) {
                inv.method_5447(e.getKey(), e.getValue());
            }
        }
        this.replaced.clear();
    }
}
