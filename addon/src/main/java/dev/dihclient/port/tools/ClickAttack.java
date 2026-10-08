package dev.dihclient.port.tools;

import dev.dihclient.mixin.port.CrystalClientAccessorMixin;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_239;
import net.minecraft.class_3966;

public class ClickAttack extends Module {
    private static volatile ClickAttack active;
    private static boolean busy;

    public ClickAttack() {
        super("Click Attack", Category.COMBAT,
                "Modules that attack (Kill Aura, Crystal Aura, Trigger Bot and so on) press the left mouse button instead of sending the attack packet themselves. The game then swings, resets the cooldown and sends the packet like a real click, which avoids lag backs.");
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    public static boolean redirect(class_1657 player, class_1297 target) {
        if (active == null || busy || target == null || mc.field_1724 == null || player != mc.field_1724 || mc.field_1687 == null) {
            return false;
        }
        CrystalClientAccessorMixin acc = (CrystalClientAccessorMixin) mc;
        class_239 old = mc.field_1765;
        int cooldown = acc.dih$crystalAttackCooldown();
        busy = true;
        try {
            mc.field_1765 = new class_3966(target);
            acc.dih$setAttackCooldown(0);
            return acc.dih$doAttack();
        } catch (RuntimeException e) {
            return false;
        } finally {
            mc.field_1765 = old;
            acc.dih$setAttackCooldown(Math.max(cooldown, acc.dih$crystalAttackCooldown()));
            busy = false;
        }
    }
}
