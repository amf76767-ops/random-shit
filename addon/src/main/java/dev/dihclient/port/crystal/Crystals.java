package dev.dihclient.port.crystal;

import java.util.Set;
import net.minecraft.class_10192;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1511;
import net.minecraft.class_1661;
import net.minecraft.class_1750;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1937;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2343;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_3481;
import net.minecraft.class_3489;
import net.minecraft.class_3532;
import net.minecraft.class_3726;
import net.minecraft.class_3965;
import net.minecraft.class_3966;
import net.minecraft.class_634;
import net.minecraft.class_638;
import net.minecraft.class_640;
import net.minecraft.class_746;
import net.minecraft.class_9334;
import net.minecraft.class_1269.class_9857;
import net.minecraft.class_1269.class_9860;
import net.minecraft.class_1269.class_9861;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Crystal helpers: where a crystal can stand, which hotbar slot to use, how to click, and the ping window.
 */
public final class Crystals {
    public static final int READY = -2;
    public static final int NO_ROUTE = -1;
    private static final int MIN_CONFIRM_TICKS = 4;
    private static final int MAX_CONFIRM_TICKS = 20;
    private static final Set<class_2248> USABLE_BLOCKS = Set.of(
        class_2246.field_23152,
        class_2246.field_10179,
        class_2246.field_10363,
        class_2246.field_10450,
        class_2246.field_10091,
        class_2246.field_10183,
        class_2246.field_17563,
        class_2246.field_10081
    );

    private Crystals() {
    }

    public static boolean isBase(class_2680 state) {
        return state.method_27852(class_2246.field_10540) || state.method_27852(class_2246.field_9987);
    }

    public static class_243 explosionAt(class_2338 base) {
        return class_243.method_24955(base.method_10084());
    }

    public static class_238 boxOn(class_2338 base) {
        return class_1299.field_6110.method_18386().method_30757(explosionAt(base));
    }

    public static boolean hasCrystal(class_1937 level, class_2338 base, class_1297 ignore) {
        for (class_1511 crystal : level.method_18467(class_1511.class, new class_238(base.method_10084()))) {
            if (crystal != ignore) {
                return true;
            }
        }

        return false;
    }

    public static boolean canHost(class_1937 level, class_2338 base, class_1297 ignore) {
        class_2338 above = base.method_10084();
        if (!level.method_22347(above)) {
            return false;
        } else {
            class_238 space = new class_238(
                above.method_10263(),
                above.method_10264(),
                above.method_10260(),
                above.method_10263() + 1.0,
                above.method_10264() + 2.0,
                above.method_10260() + 1.0
            );

            for (class_1297 entity : level.method_8335(ignore, space)) {
                if (entity.method_5805()) {
                    return false;
                }
            }

            return true;
        }
    }

    public static class_2338 obsidianTarget(class_638 level, class_746 player, class_3965 hit, class_2680 clicked) {
        class_2338 target = placedObsidian(level, player, hit, clicked);
        return target != null && canHost(level, target, null) ? target : null;
    }

    public static class_2338 placedObsidian(class_638 level, class_746 player, class_3965 hit, class_2680 clicked) {
        if (!player.method_21823() && reactsToUse(level, hit.method_17777(), clicked)) {
            return null;
        } else {
            class_1750 context = new class_1750(player, class_1268.field_5808, new class_1799(class_1802.field_8281), hit);
            if (!context.method_7716()) {
                return null;
            } else {
                class_2338 target = context.method_8037();
                if (level.method_31606(target) || !level.method_8621().method_11952(target)) {
                    return null;
                } else {
                    return !level.method_8628(class_2246.field_10540.method_9564(), target, class_3726.method_67715(player)) ? null : target;
                }
            }
        }
    }

    private static boolean reactsToUse(class_1937 level, class_2338 pos, class_2680 state) {
        class_2248 block = state.method_26204();
        if (block instanceof class_2343 || USABLE_BLOCKS.contains(block)) {
            return true;
        } else {
            return !state.method_26164(class_3481.field_15495)
                    && !state.method_26164(class_3481.field_15487)
                    && !state.method_26164(class_3481.field_25147)
                    && !state.method_26164(class_3481.field_15493)
                    && !state.method_26164(class_3481.field_26985)
                    && !state.method_26164(class_3481.field_26984)
                    && !state.method_26164(class_3481.field_15470)
                ? state.method_26196(level, pos) != null
                : true;
        }
    }

    public static int route(class_746 player, class_1792 item, class_2680 clicked, int preferredSlot) {
        class_1799 main = player.method_6047();
        if (main.method_31574(item)) {
            return -2;
        } else {
            boolean inOffhand = player.method_6079().method_31574(item);
            if (inOffhand && letsClickThrough(main, clicked)) {
                return -2;
            } else {
                class_1661 inventory = player.method_31548();
                int slot = hotbarSlotOf(inventory, item);
                if (slot != -1) {
                    return slot;
                } else if (!inOffhand) {
                    return -1;
                } else if (class_1661.method_7380(preferredSlot) && letsClickThrough(inventory.method_5438(preferredSlot), clicked)) {
                    return preferredSlot;
                } else {
                    for (int var8 = 0; var8 < class_1661.method_7368(); var8++) {
                        if (letsClickThrough(inventory.method_5438(var8), clicked)) {
                            return var8;
                        }
                    }

                    return -1;
                }
            }
        }
    }

    public static boolean letsClickThrough(class_1799 stack, class_2680 clicked) {
        if (stack.method_7960()) {
            return true;
        } else if (!stack.method_57826(class_9334.field_53964) && !stack.method_57826(class_9334.field_56396) && !stack.method_57826(class_9334.field_63632)) {
            class_10192 equippable = stack.method_58694(class_9334.field_54196);
            return equippable != null && equippable.comp_3213()
                ? false
                : stack.method_31573(class_3489.field_42611)
                    || stack.method_31574(class_1802.field_49814)
                    || stack.method_31574(class_1802.field_8288)
                    || stack.method_31573(class_3489.field_42612) && isBase(clicked);
        } else {
            return false;
        }
    }

    public static boolean isCombatItem(class_1799 stack) {
        return stack.method_7960()
            || stack.method_31573(class_3489.field_42611)
            || stack.method_31573(class_3489.field_42612)
            || stack.method_31574(class_1802.field_49814)
            || stack.method_31574(class_1802.field_8288);
    }

    public static int hotbarSlotOf(class_1661 inventory, class_1792 item) {
        for (int slot = 0; slot < class_1661.method_7368(); slot++) {
            if (inventory.method_5438(slot).method_31574(item)) {
                return slot;
            }
        }

        return -1;
    }

    public static boolean strike(class_310 mc, class_746 player, class_1297 target, Bypass.Profile profile) {
        if (player == null || mc.field_1761 == null || target == null || target.method_31481() || player.method_7325()) {
            return false;
        } else if (profile != null && profile.grim()) {
            mc.field_1761.method_2918(player, target);
            player.method_6104(class_1268.field_5808);
            return true;
        } else {
            return Melee.attack(mc, new class_3966(target, target.method_5829().method_1005()));
        }
    }

    public static void rightClick(class_310 mc, class_746 player, class_3965 hit) {
        for (class_1268 hand : class_1268.values()) {
            class_1799 stack = player.method_5998(hand);
            if (!stack.method_45435(mc.field_1687.method_45162())) {
                return;
            }

            int countBefore = stack.method_7947();
            class_1269 result = mc.field_1761.method_2896(player, hand, hit);
            if (result instanceof class_9860 success) {
                if (success.comp_2909() == class_9861.field_52427) {
                    player.method_6104(hand);
                    if (!stack.method_7960() && (stack.method_7947() != countBefore || player.method_56992())) {
                        mc.field_1773.field_4012.method_3215(hand);
                    }
                }

                return;
            }

            if (result instanceof class_9857) {
                return;
            }

            if (!stack.method_7960() && mc.field_1761.method_2919(player, hand) instanceof class_9860 success) {
                if (success.comp_2909() == class_9861.field_52427) {
                    player.method_6104(hand);
                }

                mc.field_1773.field_4012.method_3215(hand);
                return;
            }
        }
    }

    public static int confirmWindow(class_310 mc, class_746 player) {
        return class_3532.method_15340(latencyMs(mc, player) / 50 + 3, 4, 20);
    }

    public static int latencyMs(class_310 mc, class_746 player) {
        class_634 connection = mc.method_1562();
        if (connection == null) {
            return 0;
        } else {
            class_640 info = connection.method_2871(player.method_5667());
            return info == null ? 0 : Math.max(info.method_2959(), 0);
        }
    }

    /** The point of the box closest to a point (the point itself when it is inside). */
    public static class_243 closestPoint(class_243 point, class_238 box) {
        return new class_243(
            Math.max(box.field_1323, Math.min(box.field_1320, point.field_1352)),
            Math.max(box.field_1322, Math.min(box.field_1325, point.field_1351)),
            Math.max(box.field_1321, Math.min(box.field_1324, point.field_1350))
        );
    }
}
