package dev.dihclient.port.trident;

import dev.dihclient.DIHClient;
import dev.dihclient.module.ModuleManager;

/** Ported from an open-source client (GPL-3.0). */
public final class TridentHooks {
    private static boolean warned;

    private TridentHooks() {
    }

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

    public static int minChargeTicks(int vanilla) {
        try {
            TridentUtilModule util = ModuleManager.of(TridentUtilModule.class);
            return util != null && util.isEnabled() ? TridentLogic.scaledCharge(vanilla, util.chargeScale.get()) : vanilla;
        } catch (Throwable t) {
            warn(t);
            return vanilla;
        }
    }

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
