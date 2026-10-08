package dev.dihclient.port.tools;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import java.util.ArrayDeque;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2846;

public class NoGhostBlocks extends Module {
    private static volatile NoGhostBlocks active;

    public final BoolSetting breaking = this.bool("Breaking", "A broken block only disappears when the server says so. Without it the block vanishes at once and may come back (ghost block).", true);
    public final BoolSetting resync = this.bool("Resync", "After every break asks the server for the real block, so a block that was not really broken shows up again at once.", false);
    public final IntSetting delay = this.integer("Resync Delay", "Ticks to wait before the server is asked.", 4, 1, 20)
            .visibleWhen(this.resync::get);

    private final ArrayDeque<long[]> due = new ArrayDeque<>();
    private int ticks;

    public NoGhostBlocks() {
        super("No Ghost Blocks", Category.WORLD, "Stops blocks that look broken on your screen but still exist on the server.");
    }

    public static boolean holdsBreak() {
        NoGhostBlocks m = active;
        return m != null && m.breaking.get();
    }

    public static void broke(class_2338 pos) {
        NoGhostBlocks m = active;
        if (m != null && m.resync.get()) {
            m.due.add(new long[] {m.ticks + m.delay.get(), pos.method_10063()});
        }
    }

    @Override
    protected void onEnable() {
        active = this;
        this.due.clear();
    }

    @Override
    protected void onDisable() {
        active = null;
        this.due.clear();
    }

    @Override
    public void onTick() {
        this.ticks++;
        if (mc.method_1562() == null) {
            this.due.clear();
            return;
        }
        while (!this.due.isEmpty() && this.due.peekFirst()[0] <= this.ticks) {
            long[] job = this.due.pollFirst();
            mc.method_1562().method_52787(new class_2846(class_2846.class_2847.field_12971, class_2338.method_10092(job[1]), class_2350.field_11033));
        }
    }
}
