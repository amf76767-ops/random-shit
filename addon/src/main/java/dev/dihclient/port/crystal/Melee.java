package dev.dihclient.port.crystal;

import dev.dihclient.DIHClient;
import dev.dihclient.mixin.port.CrystalClientAccessorMixin;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.player.AutoEat;
import dev.dihclient.modules.render.Freecam;
import net.minecraft.class_12392;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1309;
import net.minecraft.class_1322.class_1323;
import net.minecraft.class_1531;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_239.class_240;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3959;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;
import net.minecraft.class_3965;
import net.minecraft.class_3966;
import net.minecraft.class_5134;
import net.minecraft.class_746;
import net.minecraft.class_9334;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * The few melee helpers (MeleeAttacks) the crystal modules need; friends come from the DIH social list.
 */
final class Melee {
    private static final double LINE_EPSILON_SQ = 0.001;

    private Melee() {
    }

    /** Same gate as the Anubis modules: no screen, not eating, not in Freecam. */
    static boolean canAct(class_310 mc) {
        if (mc.field_1724 == null || mc.field_1687 == null || mc.field_1761 == null || mc.field_1755 != null) {
            return false;
        }
        return !eating() && !ModuleManager.on(Freecam.class);
    }

    /** Auto Eat is holding food right now: do not touch the hand. */
    static boolean eating() {
        try {
            AutoEat eat = ModuleManager.of(AutoEat.class);
            return eat != null && eat.isEnabled() && eat.isEating();
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] crystal: eat check failed", t);
            return false;
        }
    }

    static boolean isFriend(class_1297 entity) {
        try {
            return entity instanceof class_1657 p && DIHClient.social() != null && DIHClient.social().isFriend(p);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean lineIsClear(class_746 player, class_243 from, class_243 to) {
        class_3965 block = player.method_73183().method_17742(new class_3959(from, to, class_3960.field_17558, class_242.field_1348, player));
        return block.method_17783() == class_240.field_1333 || block.method_17784().method_1025(from) >= from.method_1025(to) - LINE_EPSILON_SQ;
    }

    /** A plain left click on the entity, with the reach and cooldown checks of the game. */
    static boolean attack(class_310 mc, class_3966 hit) {
        class_746 player = mc.field_1724;
        if (player == null || mc.field_1687 == null || mc.field_1761 == null) {
            return false;
        } else if (((CrystalClientAccessorMixin) mc).dih$crystalAttackCooldown() > 0) {
            return false;
        } else if (!player.method_3144() && !player.method_6115() && !player.method_7325()) {
            class_1799 held = player.method_6047();
            if (held.method_57826(class_9334.field_63631)) {
                return false;
            } else if (held.method_45435(mc.field_1687.method_45162()) && !player.method_75202(held, 0)) {
                class_1297 target = hit.method_17782();
                if (target.method_31481()) {
                    return false;
                } else {
                    class_12392 range = held.method_58694(class_9334.field_64680);
                    boolean inReach = range != null ? range.method_76736(player, hit.method_17784()) : player.method_56092(target.method_5829(), 0.0);
                    if (!inReach) {
                        return false;
                    } else {
                        mc.field_1761.method_2918(player, target);
                        player.method_6104(class_1268.field_5808);
                        return true;
                    }
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /** A living thing we may hurt: no armor stands, spectators or creative players. */
    static boolean isHittable(class_746 self, class_1297 entity) {
        if (!(entity instanceof class_1309 living && entity != self && !(entity instanceof class_1531))) {
            return false;
        }
        return living.method_5805() && entity.method_5732() && self.method_5854() != entity
                ? !(entity instanceof class_1657 player && (player.method_7325() || player.method_68878()))
                : false;
    }

    /** Base attack damage the item adds in the main hand. */
    static double attackDamage(class_1799 stack) {
        double[] total = new double[]{0.0};
        stack.method_57354(class_1304.field_6173, (attribute, modifier) -> {
            if (class_5134.field_23721.equals(attribute) && modifier.comp_2450() == class_1323.field_6328) {
                total[0] += modifier.comp_2449();
            }
        });
        return total[0];
    }
}
