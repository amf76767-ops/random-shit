package dev.dihclient.port.tools;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.StringSetting;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.class_1747;
import net.minecraft.class_1799;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_2960;
import net.minecraft.class_3965;
import net.minecraft.class_7923;

public class GhostBlocks extends Module {
    private static volatile GhostBlocks active;

    public final BoolSetting useHeld = this.bool("Use Held Block", "Right click places the block you hold. Off = always the block below.", true);
    public final StringSetting block = this.text("Block", "Block id used when you hold no block (or Use Held Block is off).", "minecraft:diamond_block", 64);
    public final BoolSetting leftRemoves = this.bool("Left Click Removes", "Left click on a ghost block takes it away again.", true);

    private final Map<Long, class_2680> originals = new LinkedHashMap<>();

    public GhostBlocks() {
        super("Ghost Blocks", Category.WORLD, "Places blocks only on your screen (for planning or screenshots). The server and other players see nothing, and every block update from the server removes them.");
        this.action("Clear All", "Removes all ghost blocks.", this::restore);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
        this.restore();
    }

    @Override
    public void onWorldChange() {
        this.originals.clear();
    }

    @Override
    public String getInfo() {
        return Integer.toString(this.originals.size());
    }

    private void restore() {
        if (mc.field_1687 != null) {
            for (Map.Entry<Long, class_2680> e : this.originals.entrySet()) {
                mc.field_1687.method_8501(class_2338.method_10092(e.getKey()), e.getValue());
            }
        }
        this.originals.clear();
    }

    private class_2680 chosen() {
        class_1799 held = mc.field_1724.method_6047();
        if (this.useHeld.get() && held.method_7909() instanceof class_1747 item) {
            return item.method_7711().method_9564();
        }
        class_2960 id = class_2960.method_12829(this.block.get().trim());
        class_2248 b = id == null ? null : class_7923.field_41175.method_63535(id);
        return b == null ? null : b.method_9564();
    }

    public static boolean onUse(class_3965 hit) {
        GhostBlocks m = active;
        if (m == null || mc.field_1687 == null || mc.field_1724 == null || hit == null) {
            return false;
        }
        class_2680 state = m.chosen();
        if (state == null || state.method_26215()) {
            return false;
        }
        class_2338 at = hit.method_17777().method_10093(hit.method_17780());
        m.originals.putIfAbsent(at.method_10063(), mc.field_1687.method_8320(at));
        mc.field_1687.method_8501(at, state);
        return true;
    }

    public static boolean onAttack(class_2338 pos) {
        GhostBlocks m = active;
        if (m == null || !m.leftRemoves.get() || mc.field_1687 == null) {
            return false;
        }
        class_2680 original = m.originals.remove(pos.method_10063());
        if (original == null) {
            return false;
        }
        mc.field_1687.method_8501(pos, original);
        return true;
    }
}
