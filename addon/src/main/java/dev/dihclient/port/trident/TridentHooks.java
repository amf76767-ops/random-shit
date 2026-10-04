package dev.dihclient.port.trident;

import dev.dihclient.DIHClient;
import dev.dihclient.module.ModuleManager;

/**
 * Ported from an open-source client (GPL-3.0).
 * What {@code TridentRiptideMixin} asks while the trident item runs. Never throws: on an error the vanilla value is used.
 */
public final class TridentHooks {
    private static boolean warned;

    private TridentHooks() {
    }

    /** Trident Util "No Water" or Trident Boost "Out Of Water": the trident acts as if you were wet. */
    public static boolean allowOutOfWater() {
        try {
            TridentUtilModule util = ModuleManager.of(TridentUtilModule.class);
            if (util != null && util.isEnabled() && util.noWater.get()) {
                return true;
            }
            TridentBoostModule boost = ModuleManager.of(TridentBoostModule.class);
            return boost != null && boost.isEnabled() && boost.outOfWater.get();
        } catch (Throwable t) {
            warn(t);
            return false;
        }
    }

    /** Charge ticks the trident asks for (vanilla 10), shortened by Trident Util "Charge Scale". */
    public static int minChargeTicks(int vanilla) {
        try {
            TridentUtilModule util = ModuleManager.of(TridentUtilModule.class);
            return util != null && util.isEnabled() ? TridentLogic.scaledCharge(vanilla, util.chargeScale.get()) : vanilla;
        } catch (Throwable t) {
            warn(t);
            return vanilla;
        }
    }

    /** Trident Boost: multiplier for the riptide launch, 1 when off. */
    public static double riptideMultiplier() {
        try {
            TridentBoostModule boost = ModuleManager.of(TridentBoostModule.class);
            return boost != null && boost.isEnabled() ? boost.boost.get() : 1.0;
        } catch (Throwable t) {
            warn(t);
            return 1.0;
        }
    }

    private static void warn(Throwable t) {
        if (!warned) {
            warned = true;
            DIHClient.LOG.warn("[DIHClient] trident hook failed, using vanilla values", t);
        }
    }
}
