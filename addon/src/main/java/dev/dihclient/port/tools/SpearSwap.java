package dev.dihclient.port.tools;

import dev.dihclient.mixin.port.AttackCooldownMixin;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.render.Freecam;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.class_12125;
import net.minecraft.class_1268;
import net.minecraft.class_1661;
import net.minecraft.class_1799;
import net.minecraft.class_1887;
import net.minecraft.class_1893;
import net.minecraft.class_310;
import net.minecraft.class_6880;
import net.minecraft.class_746;
import net.minecraft.class_9334;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Swaps to your best Lunge spear, lunges and swaps back. The module's own key (the keybind in the GUI) is the lunge key,
 * like the other key-triggered modules; the module has to be switched on for it to work.
 */
public class SpearSwap extends Module {
    private static final int BUFFER_TICKS = 10;

    public final IntSetting cooldown = this.integer("Cooldown", "Ticks to wait after a lunge before the next one.", 12, 0, 40);
    public final BoolSetting switchBack = this.bool("Switch Back", "Goes back to the slot you were holding after the lunge.", true);
    public final IntSetting backDelay = this.integer("Back Delay", "Ticks before it switches back.", 3, 1, 20).visibleWhen(this.switchBack::get);
    public final IntSetting jitter = this.integer("Jitter", "Up to this many random extra ticks on the switch back.", 1, 0, 4).visibleWhen(this.switchBack::get);

    private int ticks;
    private int readyTick;
    private boolean pending;
    private int pendingTicks;
    private boolean returning;
    private int spearSlot = -1;
    private int returnSlot = -1;
    private int returnTick;
    private class_746 owner;

    public SpearSwap() {
        super("Spear Swap", Category.COMBAT, "Swaps to your Lunge spear, lunges and swaps back. Bind a key to lunge.");
    }

    @Override
    public boolean isActionModule() {
        return true;
    }

    @Override
    protected void onEnable() {
        this.reset();
        this.owner = mc.field_1724;
    }

    @Override
    protected void onDisable() {
        if (this.returning) {
            this.finishReturn(this.switchBack.get());
        }
        this.reset();
    }

    @Override
    public void onWorldChange() {
        this.reset();
    }

    private void reset() {
        this.pending = false;
        this.pendingTicks = 0;
        this.returning = false;
        this.spearSlot = -1;
        this.returnSlot = -1;
        this.readyTick = this.ticks;
        this.owner = null;
    }

    /** The lunge key was pressed. */
    @Override
    public void onAction() {
        if (mc.field_1755 == null && mc.field_1724 != null && mc.field_1687 != null && !this.returning && !this.pending && this.ticks >= this.readyTick) {
            this.pending = true;
            this.pendingTicks = 0;
        }
    }

    @Override
    public void onTick() {
        this.ticks++;
        class_746 player = mc.field_1724;
        if (player != this.owner) {
            this.reset();
            this.owner = player;
        }
        if (player != null && mc.field_1687 != null && mc.field_1761 != null) {
            if (this.returning) {
                this.followReturn(player);
            } else if (this.pending) {
                this.tryLunge(player);
            }
        } else if (this.returning || this.pending) {
            this.reset();
        }
    }

    private void followReturn(class_746 player) {
        if (player.method_31548().method_67532() != this.spearSlot) {
            this.finishReturn(false); // the player changed slot themselves
        } else if (this.ticks >= this.returnTick && mc.field_1755 == null) {
            this.finishReturn(true);
        }
    }

    private void finishReturn(boolean restore) {
        class_746 player = mc.field_1724;
        if (restore && player != null && this.returnSlot >= 0 && player.method_31548().method_67532() == this.spearSlot) {
            InvUtil.select(this.returnSlot);
        }
        this.returning = false;
        this.spearSlot = -1;
        this.returnSlot = -1;
    }

    private void tryLunge(class_746 player) {
        if (++this.pendingTicks > BUFFER_TICKS) {
            this.pending = false; // could not lunge in time, the press is forgotten
            return;
        }
        if (!this.canLunge(player)) {
            return;
        }
        class_1661 inventory = player.method_31548();
        int slot = bestSpear(inventory);
        if (slot < 0) {
            this.pending = false;
            return;
        }
        class_1799 spear = inventory.method_5438(slot);
        class_12125 weapon = spear.method_58694(class_9334.field_63631);
        if (weapon == null || !canJab(player, spear)) {
            return;
        }
        this.pending = false;
        int previous = inventory.method_67532();
        InvUtil.select(slot);
        mc.field_1761.method_75407(weapon);
        player.method_6104(class_1268.field_5808);
        this.readyTick = this.ticks + this.cooldown.get();
        if (previous != slot && this.switchBack.get()) {
            int spread = this.jitter.get() > 0 ? ThreadLocalRandom.current().nextInt(this.jitter.get() + 1) : 0;
            this.returning = true;
            this.spearSlot = slot;
            this.returnSlot = previous;
            this.returnTick = this.ticks + ToolsLogic.returnDelay(this.backDelay.get(), spread);
        }
    }

    private boolean canLunge(class_746 player) {
        if (mc.field_1755 != null || mc.field_1761.method_2928() || ModuleManager.on(Freecam.class)) {
            return false;
        }
        return !player.method_6115() && !player.method_5799() && !player.method_6128() && !player.method_5765()
            && (player.method_7344().method_75882() || player.method_31549().field_7478);
    }

    private static boolean canJab(class_746 player, class_1799 spear) {
        class_310 game = mc;
        return ((AttackCooldownMixin) game).dih$attackCooldown() <= 0 && !player.method_3144()
            && spear.method_45435(game.field_1687.method_45162()) && !player.method_75202(spear, 0);
    }

    private static int bestSpear(class_1661 inventory) {
        int[] levels = new int[class_1661.method_7368()];
        for (int slot = 0; slot < levels.length; slot++) {
            class_1799 stack = inventory.method_5438(slot);
            if (stack.method_57826(class_9334.field_63631) && !stack.method_63692()) {
                levels[slot] = lungeLevel(stack);
            }
        }
        return ToolsLogic.pickSpear(levels, inventory.method_67532());
    }

    private static int lungeLevel(class_1799 stack) {
        for (Entry<class_6880<class_1887>> entry : stack.method_58657().method_57539()) {
            if (entry.getKey().method_40225(class_1893.field_63420)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }
}
