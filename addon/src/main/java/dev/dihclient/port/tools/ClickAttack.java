package dev.dihclient.port.tools;

import dev.dihclient.mixin.port.CrystalClientAccessorMixin;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_239;
import net.minecraft.class_310;
import net.minecraft.class_3966;

public final class ClickAttack {
    private static final Map<Module, BoolSetting> OPTIONS = new IdentityHashMap<>();
    private static volatile Module current;
    private static boolean busy;

    private ClickAttack() {
    }

    public static BoolSetting option(Module host) {
        BoolSetting s = new BoolSetting("Click Attack",
                "Attacks by pressing the left mouse button of the game instead of sending the attack itself (swing, cooldown and packet like a real click, fewer lag backs).",
                true);
        OPTIONS.put(host, s);
        return s;
    }

    public static void enter(Module m) {
        current = m;
    }

    public static void leave() {
        current = null;
    }

    public static boolean redirect(class_1657 player, class_1297 target) {
        Module m = current;
        BoolSetting opt = m == null ? null : OPTIONS.get(m);
        class_310 mc = class_310.method_1551();
        if (opt == null || !opt.get() || busy || target == null || mc.field_1724 == null || player != mc.field_1724 || mc.field_1687 == null) {
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
